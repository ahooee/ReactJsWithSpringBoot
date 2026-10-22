package ir.linuxian.telegram;

import org.drinkless.tdlib.TdApi;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Waits for a sent message to be delivered, draws an upload progress bar and logs the status. */
public class SendTracker {
    private static final int BAR_WIDTH = 30;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TelegramService telegram;
    private final MessageReader reader;
    private volatile boolean verbose;

    /** Upload progress snapshot pushed to web UIs (null = console bar only). */
    public record UploadProgress(String label, int percent, long doneBytes, long totalBytes) {
    }

    private volatile Consumer<UploadProgress> progressListener;
    // Extra listeners (e.g. the book uploader) can observe progress alongside
    // the main admin-send listener without replacing it.
    private final List<Consumer<UploadProgress>> extraProgressListeners = new CopyOnWriteArrayList<>();

    // pending message id -> final result (shared by the update thread and the waiting thread,
    // so it doesn't matter which one arrives first)
    private final Map<Long, CompletableFuture<TdApi.Message>> outcomes = new ConcurrentHashMap<>();
    private final Map<Long, String> labels = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fileToMessage = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastPercent = new ConcurrentHashMap<>();

    public SendTracker(TelegramService telegram, MessageReader reader, boolean verbose) {
        this.telegram = telegram;
        this.reader = reader;
        this.verbose = verbose;
    }

    /** Receives live upload progress (used by the admin send panel). Null disables. */
    public void setProgressListener(Consumer<UploadProgress> progressListener) {
        this.progressListener = progressListener;
    }

    /** Adds a progress listener that runs in addition to the main one. */
    public void addProgressListener(Consumer<UploadProgress> listener) {
        if (listener != null) {
            extraProgressListeners.add(listener);
        }
    }

    /** Removes a previously added progress listener. */
    public void removeProgressListener(Consumer<UploadProgress> listener) {
        extraProgressListeners.remove(listener);
    }

    /** Live toggle for send logging (used by the admin panel). */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /** Blocks until the pending message is sent or failed. Returns the final message. */
    public TdApi.Message await(TdApi.Message pending, String what) throws TelegramException {
        long id = pending.id;
        String label = what + " -> " + chatName(pending.chatId);
        CompletableFuture<TdApi.Message> outcome = outcome(id);

        int fileId = reader.read(pending).fileId();
        labels.put(id, label);
        if (fileId != 0) fileToMessage.put(fileId, id);

        if (verbose) log("queued " + label);
        long start = System.nanoTime();

        try {
            TdApi.Message sent = outcome.get(30, TimeUnit.MINUTES);
            double seconds = (System.nanoTime() - start) / 1e9;
            log(String.format("SENT   %s (%.1fs)", label, seconds));
            return sent;
        } catch (ExecutionException e) {
            TelegramException te = e.getCause() instanceof TelegramException t
                    ? t : new TelegramException(-1, String.valueOf(e.getCause()));
            log("FAILED " + label + ": [" + te.getCode() + "] " + te.getMessage());
            throw te;
        } catch (TimeoutException e) {
            log("FAILED " + label + ": timed out");
            throw new TelegramException(-1, "Timed out waiting for delivery");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(-1, "Interrupted");
        } finally {
            outcomes.remove(id);
            labels.remove(id);
            if (fileId != 0) {
                fileToMessage.remove(fileId);
                lastPercent.remove(fileId);
            }
        }
    }

    /** Register with TelegramService.addUpdateListener(tracker::onUpdate). */
    public void onUpdate(TdApi.Object update) {
        if (update instanceof TdApi.UpdateFile u) {
            onFile(u.file);
        } else if (update instanceof TdApi.UpdateMessageSendAcknowledged u) {
            String label = labels.get(u.messageId);
            if (verbose && label != null) log("received by Telegram servers: " + label);
        } else if (update instanceof TdApi.UpdateMessageSendSucceeded u) {
            outcome(u.oldMessageId).complete(u.message);
        } else if (update instanceof TdApi.UpdateMessageSendFailed u) {
            outcome(u.oldMessageId).completeExceptionally(
                    new TelegramException(u.error.code, u.error.message));
        }
    }

    // ---------- progress bar ----------

    private void onFile(TdApi.File f) {
        if (!fileToMessage.containsKey(f.id)) return;
        if (!f.remote.isUploadingActive && !f.remote.isUploadingCompleted) return;

        long total = f.size > 0 ? f.size : f.expectedSize;
        if (total <= 0) return;

        long done = f.remote.isUploadingCompleted ? total : f.remote.uploadedSize;
        int percent = (int) Math.min(100, done * 100 / total);

        Integer previous = lastPercent.put(f.id, percent);
        if (previous != null && previous == percent) return;   // redraw only when it changes

        Long messageId = fileToMessage.get(f.id);
        String label = messageId == null ? null : labels.get(messageId);
        UploadProgress progress = new UploadProgress(label, percent, done, total);
        Consumer<UploadProgress> listener = progressListener;
        if (listener != null) {
            try {
                listener.accept(progress);
            } catch (Exception ignored) {
            }
        }
        for (Consumer<UploadProgress> extra : extraProgressListeners) {
            try {
                extra.accept(progress);
            } catch (Exception ignored) {
            }
        }

        int filled = percent * BAR_WIDTH / 100;
        String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        System.out.printf("\r[upload] [%s] %3d%%  %s / %s", bar, percent, human(done), human(total));
        if (percent >= 100) System.out.println();
    }

    // ---------- helpers ----------

    private CompletableFuture<TdApi.Message> outcome(long id) {
        return outcomes.computeIfAbsent(id, k -> new CompletableFuture<>());
    }

    private String chatName(long chatId) {
        try {
            return telegram.getChat(chatId).title;
        } catch (TelegramException e) {
            return "chat " + chatId;
        }
    }

    private static void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] [send] " + message);
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        return String.format("%.1f MB", kb / 1024.0);
    }
}