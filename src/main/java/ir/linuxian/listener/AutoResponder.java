package ir.linuxian.listener;

import ir.linuxian.ai.AiResponder;
import ir.linuxian.telegram.IncomingMessage;
import ir.linuxian.telegram.MessageReader;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Answers incoming private text messages with an AI.
 * Safety defaults: off until enabled, private chats only, ignores bots, per-chat cooldown,
 * and an optional allow-list of chats.
 */
public class AutoResponder implements Consumer<TdApi.Message> {
    private final TelegramService telegram;
    private final MessageReader reader;
    private final MessageSender sender;
    private volatile AiResponder ai;
    private volatile long cooldownMillis;

    private volatile boolean enabled = false;
    private volatile boolean verbose = false;
    private final Set<Long> allowedChats = ConcurrentHashMap.newKeySet(); // empty = every private chat
    private final Map<Long, String> allowedLabels = new ConcurrentHashMap<>(); // chatId -> username/title for display
    private final Map<Long, Long> lastReplyAt = new ConcurrentHashMap<>();

    // AI calls are slow, so they get their own thread.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auto-responder");
        t.setDaemon(true);
        return t;
    });

    public AutoResponder(TelegramService telegram, MessageReader reader, MessageSender sender,
                         AiResponder ai, long cooldownMillis) {
        this.telegram = telegram;
        this.reader = reader;
        this.sender = sender;
        this.ai = ai;
        this.cooldownMillis = cooldownMillis;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        if (cooldownMillis >= 0) {
            this.cooldownMillis = cooldownMillis;
        }
    }

    public AiResponder getAi() {
        return ai;
    }

    /** Swaps the AI backend live (used by the admin panel when the key changes). */
    public void setAi(AiResponder ai) {
        if (ai != null) {
            this.ai = ai;
        }
    }

    /** Once at least one chat is allowed, only allowed chats get answers. */
    public void allowChat(long chatId) {
        allowChat(chatId, null);
    }

    /** Same as {@link #allowChat(long)} but remembers a human-readable label (e.g. @username). */
    public void allowChat(long chatId, String label) {
        allowedChats.add(chatId);
        if (label != null && !label.isBlank()) {
            allowedLabels.put(chatId, label);
        } else {
            allowedLabels.putIfAbsent(chatId, "chat " + chatId);
        }
    }

    public boolean removeChat(long chatId) {
        allowedLabels.remove(chatId);
        return allowedChats.remove(chatId);
    }

    public boolean isChatAllowed(long chatId) {
        return allowedChats.isEmpty() || allowedChats.contains(chatId);
    }

    /** Copy of the allow-listed chat ids. Empty means "every private chat". */
    public Set<Long> getAllowedChats() {
        return Set.copyOf(allowedChats);
    }

    /** Copy of chatId -> label (username/title) for settings display. */
    public Map<Long, String> getAllowedLabels() {
        return Map.copyOf(allowedLabels);
    }

    public void clearAllowedChats() {
        allowedChats.clear();
        allowedLabels.clear();
    }

    @Override
    public void accept(TdApi.Message message) {
        if (!enabled) {
            v("skip msg " + message.id + " in chat " + message.chatId + ": auto-reply disabled");
            return;
        }
        if (message.isOutgoing) {
            v("skip msg " + message.id + " in chat " + message.chatId + ": outgoing");
            return;
        }
        v("queue msg " + message.id + " in chat " + message.chatId + " for AI check");
        worker.submit(() -> handle(message));
    }

    private void handle(TdApi.Message message) {
        try {
            IncomingMessage in = reader.read(message);
            v("incoming chat=" + in.chatId() + " msg=" + in.messageId()
                    + " sender=" + in.senderUserId() + " kind=" + in.kind()
                    + " text=" + preview(in.text()));
            if (in.kind() != IncomingMessage.Kind.TEXT || in.text().isBlank()) {
                v("skip chat " + in.chatId() + ": not a text message (kind=" + in.kind() + ")");
                return;
            }

            TdApi.Chat chat = telegram.getChat(in.chatId());
            if (!(chat.type instanceof TdApi.ChatTypePrivate)) {
                v("skip chat " + in.chatId() + " (" + chat.title + "): not a private chat");
                return;
            }
            if (!allowedChats.isEmpty() && !allowedChats.contains(in.chatId())) {
                v("skip chat " + in.chatId() + " (" + chat.title + "): not in allow-list " + allowedChats);
                return;
            }
            if (telegram.getUser(in.senderUserId()).type instanceof TdApi.UserTypeBot) {
                v("skip chat " + in.chatId() + ": sender is a bot");
                return;
            }

            long now = System.currentTimeMillis();
            Long last = lastReplyAt.get(in.chatId());
            if (last != null && now - last < cooldownMillis) {
                v("skip chat " + in.chatId() + ": cooldown (" + (now - last) + "ms < " + cooldownMillis + "ms)");
                return;
            }
            lastReplyAt.put(in.chatId(), now);

            v("AI request chat=" + in.chatId() + " (" + chat.title + ") text=" + preview(in.text()));
            long start = System.nanoTime();
            String answer = ai.reply(in.chatId(), in.text());
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            v("AI answer chat=" + in.chatId() + " took=" + tookMs + "ms len=" + answer.length()
                    + " preview=" + preview(answer));
            sender.sendText(in.chatId(), answer);
            System.out.println("\n>> [auto-reply to " + chat.title + "] " + answer);
            v("sent auto-reply to chat " + in.chatId() + " (" + chat.title + ")");
        } catch (Exception e) {
            System.err.println("Auto-reply failed: " + e.getMessage());
            v("AI failure: " + e);
        }
    }

    private void v(String msg) {
        if (verbose) System.out.println("[ai-verbose] " + msg);
    }

    private static String preview(String s) {
        if (s == null) return "\"\"";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= 120) return "\"" + oneLine + "\"";
        return "\"" + oneLine.substring(0, 120) + "...\"";
    }
}
