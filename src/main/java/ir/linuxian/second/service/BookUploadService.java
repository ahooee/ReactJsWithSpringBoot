package ir.linuxian.second.service;

import ir.linuxian.second.bot.BotManagementService;
import ir.linuxian.second.bot.TelegramBotRunner;
import ir.linuxian.second.entities.book.Book;
import ir.linuxian.second.entities.book.BookMessage;
import ir.linuxian.second.repos.BookMessageRepo;
import ir.linuxian.second.repos.BookRepo;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.SendTracker;
import ir.linuxian.telegram.TelegramException;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Uploads the files of not-yet-uploaded rows from the {@code books} table to a
 * Telegram chat, records each sent message in {@code books_messages}, and
 * marks the row as {@code uploaded}.
 *
 * <p>The downloads directory supplied by the admin is the root of the
 * downloader library. Files are matched through {@code rel_path} / {@code
 * filename}; everything that cannot be found is skipped.</p>
 */
@Service
public class BookUploadService {

    private static final Logger log = LoggerFactory.getLogger(BookUploadService.class);
    private static final String DOWNLOADS_PREFIX = "downloads/";

    private final TelegramBotRunner runner;
    private final BookRepo bookRepo;
    private final BookMessageRepo bookMessageRepo;
    private final TransactionTemplate transactionTemplate;

    private final Map<String, BookUploadJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService uploadPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "book-telegram-upload");
        t.setDaemon(true);
        return t;
    });
    private volatile BookUploadJob activeJob;

    public BookUploadService(TelegramBotRunner runner, BookRepo bookRepo,
                             BookMessageRepo bookMessageRepo,
                             PlatformTransactionManager transactionManager) {
        this.runner = runner;
        this.bookRepo = bookRepo;
        this.bookMessageRepo = bookMessageRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------- public API ----------

    /** Starts an upload run. The chat is resolved before returning so bad links fail fast. */
    public synchronized BookUploadJob start(String downloadsPath, String chatTarget) {
        if (activeJob != null && activeJob.isActive()) {
            throw new IllegalStateException("An upload is already running (job " + activeJob.getJobId() + ").");
        }
        if (downloadsPath == null || downloadsPath.isBlank()) {
            throw new IllegalArgumentException("Downloads directory is required.");
        }
        if (chatTarget == null || chatTarget.isBlank()) {
            throw new IllegalArgumentException("Telegram chat link is required.");
        }

        Path root = Paths.get(expandHome(downloadsPath.trim())).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Downloads directory not found: " + root);
        }
        try {
            root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read downloads directory: " + e.getMessage());
        }
        final Path realRoot = root;

        TelegramService telegram = requireTelegram();
        TdApi.Chat chat;
        try {
            chat = resolveUploadChat(telegram, chatTarget.trim());
        } catch (TelegramException e) {
            throw new IllegalArgumentException("Could not open Telegram chat: " + e.getMessage());
        }

        BookUploadJob job = new BookUploadJob(UUID.randomUUID().toString(), root.toString(), chatTarget.trim());
        job.setChatId(chat.id);
        job.setChatTitle(chat.title);
        job.setMessage("Preparing…");
        jobs.put(job.getJobId(), job);
        activeJob = job;
        purgeOldJobs();
        uploadPool.submit(() -> run(job, realRoot, chat));
        return job;
    }

    public BookUploadJob getJob(String jobId) {
        BookUploadJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Unknown upload job: " + jobId);
        }
        return job;
    }

    /** The currently running job, or null when nothing is running. */
    public BookUploadJob getActiveJob() {
        BookUploadJob job = activeJob;
        return job != null && job.isActive() ? job : null;
    }

    public BookUploadJob stop(String jobId) {
        BookUploadJob job = getJob(jobId);
        if (job.isActive()) {
            job.requestStop();
            job.setStatus(BookUploadJob.Status.STOPPING);
            job.setMessage("Stopping after the current file…");
        }
        return job;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalBooks", bookRepo.count());
        m.put("downloadedBooks", bookRepo.countByDownloadedTrue());
        m.put("uploadedBooks", bookRepo.countByUploadedTrue());
        m.put("messages", bookMessageRepo.count());
        return m;
    }

    public Map<String, Object> snapshot(BookUploadJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", job.getJobId());
        m.put("status", job.getStatus().name());
        m.put("downloadsPath", job.getDownloadsPath());
        m.put("chatTarget", job.getChatTarget());
        m.put("chatId", job.getChatId());
        m.put("chatTitle", job.getChatTitle());
        m.put("totalBooks", job.getTotalBooks());
        m.put("processedBooks", job.getProcessedBooks());
        m.put("uploadedBooks", job.getUploadedBooks());
        m.put("failedBooks", job.getFailedBooks());
        m.put("skippedBooks", job.getSkippedBooks());
        m.put("totalBytes", job.getTotalBytes());
        m.put("doneBytes", job.getDoneBytes());
        m.put("percent", job.getPercent());
        m.put("currentBookId", job.getCurrentBookId());
        m.put("currentBookTitle", job.getCurrentBookTitle());
        m.put("currentFile", job.getCurrentFile());
        m.put("message", job.getMessage());
        m.put("error", job.getError());
        m.put("createdAt", job.getCreatedAt());
        return m;
    }

    // ---------- upload run ----------

    private void run(BookUploadJob job, Path root, TdApi.Chat chat) {
        try {
            job.setStatus(BookUploadJob.Status.RUNNING);
            job.setMessage("Scanning books…");

            List<Candidate> candidates = scan(job, root);
            if (job.isStopRequested()) {
                finishStopped(job);
                return;
            }

            long totalBytes = candidates.stream().mapToLong(Candidate::size).sum();
            job.setTotalBooks(candidates.size());
            job.setTotalBytes(totalBytes);
            if (candidates.isEmpty()) {
                job.setStatus(BookUploadJob.Status.COMPLETED);
                job.setPercent(100);
                job.setMessage("No new book files were found under the downloads directory.");
                return;
            }

            MessageSender sender = runner.getSender();
            SendTracker tracker = runner.getTracker();
            if (!runner.isLoggedIn() || sender == null || tracker == null) {
                throw new IllegalStateException("Telegram bot is not running or not logged in.");
            }

            AtomicLong uploadedBytes = new AtomicLong();
            for (Candidate candidate : candidates) {
                if (job.isStopRequested()) {
                    break;
                }
                Book book = candidate.book();
                String displayTitle = displayTitle(book);
                String fileName = candidate.path().getFileName().toString();
                job.setCurrentBookId(book.getId() == null ? null : book.getId().toString());
                job.setCurrentBookTitle(displayTitle);
                job.setCurrentFile(fileName);
                job.setMessage("Uploading " + displayTitle);

                Consumer<SendTracker.UploadProgress> listener = progress -> {
                    // Ignore progress belonging to a different concurrent send.
                    if (progress.label() == null || !progress.label().contains(fileName)) {
                        return;
                    }
                    long done = uploadedBytes.get() + progress.doneBytes();
                    job.setDoneBytes(done);
                    job.setPercent(percent(done, totalBytes));
                };

                tracker.addProgressListener(listener);
                try {
                    TdApi.Message sent = sender.sendDocument(chat.id, candidate.path().toString(), caption(book));
                    persistUpload(book, chat.id, sent.id);
                    job.incrementUploadedBooks();
                } catch (Exception e) {
                    job.incrementFailedBooks();
                    job.setError("Book #" + book.getId() + " (" + fileName + "): " + e.getMessage());
                    log.warn("Book upload failed for id {} ({}): {}", book.getId(), fileName, e.getMessage());
                } finally {
                    tracker.removeProgressListener(listener);
                }

                uploadedBytes.addAndGet(candidate.size());
                job.setDoneBytes(uploadedBytes.get());
                job.setPercent(percent(uploadedBytes.get(), totalBytes));
                job.incrementProcessedBooks();
            }

            if (job.isStopRequested()) {
                finishStopped(job);
            } else {
                job.setStatus(BookUploadJob.Status.COMPLETED);
                job.setPercent(100);
                job.setDoneBytes(totalBytes);
                job.setCurrentBookId(null);
                job.setCurrentFile("");
                job.setMessage("Uploaded " + job.getUploadedBooks() + " of " + job.getTotalBooks()
                        + " file(s)" + (job.getFailedBooks() > 0 ? ", " + job.getFailedBooks() + " failed." : "."));
            }
        } catch (Exception e) {
            job.setStatus(BookUploadJob.Status.FAILED);
            job.setError(e.getMessage() == null ? e.toString() : e.getMessage());
            job.setMessage("Upload stopped because of an error.");
            log.error("Book upload job {} failed: {}", job.getJobId(), e.getMessage(), e);
        }
    }

    private void finishStopped(BookUploadJob job) {
        job.setStatus(BookUploadJob.Status.STOPPED);
        job.setCurrentBookId(null);
        job.setCurrentFile("");
        job.setMessage("Stopped after " + job.getProcessedBooks() + " of " + job.getTotalBooks() + " file(s).");
    }

    private List<Candidate> scan(BookUploadJob job, Path root) {
        List<Book> books = bookRepo.findByDownloadedTrueAndUploadedFalseOrderByTitleAsc();
        List<Candidate> candidates = new ArrayList<>();
        int skipped = 0;
        for (Book book : books) {
            Path file = resolveBookFile(root, book);
            if (file == null) {
                skipped++;
                continue;
            }
            try {
                long size = Files.size(file);
                if (size <= 0) {
                    skipped++;
                    continue;
                }
                candidates.add(new Candidate(book, file, size));
            } catch (IOException e) {
                skipped++;
                log.warn("Cannot read book file {}: {}", file, e.getMessage());
            }
        }
        job.setSkippedBooks(skipped);
        return candidates;
    }

    /**
     * Finds the file for a books row under the admin-supplied root. The
     * downloader stores paths as "downloads/name.ext" while {@code rel_path}
     * is usually just "name.ext"; both shapes are accepted, with or without
     * the root already ending in "downloads".
     */
    static Path resolveBookFile(Path root, Book book) {
        Set<String> names = new LinkedHashSet<>();
        addPathNames(names, book.getRelPath());
        addPathNames(names, book.getFilename());
        for (String name : names) {
            Path candidate = root.resolve(name).normalize();
            if (candidate.startsWith(root) && Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void addPathNames(Set<String> names, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String path = raw.trim().replace('\\', '/');
        if (path.regionMatches(true, 0, DOWNLOADS_PREFIX, 0, DOWNLOADS_PREFIX.length())) {
            names.add(path.substring(DOWNLOADS_PREFIX.length()));
        }
        names.add(path);
    }

    private void persistUpload(Book book, long chatId, long messageId) {
        transactionTemplate.executeWithoutResult(status -> {
            book.setUploaded(true);
            Book saved = bookRepo.save(book);
            bookMessageRepo.save(new BookMessage(saved, chatId, messageId));
        });
    }

    // ---------- chat link resolution ----------

    private static TdApi.Chat resolveUploadChat(TelegramService telegram, String target) throws TelegramException {
        String invite = inviteLink(target);
        if (invite != null) {
            try {
                TdApi.ChatJoinResult result = telegram.call(new TdApi.JoinChatByInviteLink(invite));
                if (result instanceof TdApi.ChatJoinResultSuccess success) {
                    return telegram.getChat(success.chatId);
                }
                if (result instanceof TdApi.ChatJoinResultRequestSent) {
                    throw new TelegramException(-1,
                            "Join request sent. Approve it in Telegram, then click Apply again.");
                }
                if (result instanceof TdApi.ChatJoinResultDeclined) {
                    throw new TelegramException(-1, "The chat declined the join request.");
                }
                throw new TelegramException(-1, "Could not join the invite link.");
            } catch (TelegramException e) {
                String upper = e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.ROOT);
                if (upper.contains("USER_ALREADY_PARTICIPANT")) {
                    // TDLib reports this as an error for links the account has
                    // already joined; the link still tells us which chat it is.
                    TdApi.Chat chat = chatFromInviteLink(telegram, invite);
                    if (chat != null) {
                        return chat;
                    }
                    throw new TelegramException(-1,
                            "This account is already a member of the chat, but its id could not be resolved. "
                                    + "Open the chat once in Telegram and try again.");
                }
                if (upper.contains("INVITE_REQUEST_SENT")) {
                    throw new TelegramException(-1,
                            "Join request sent. Approve it in Telegram, then click Apply again.");
                }
                throw e;
            }
        }

        String username = publicUsername(target);
        if (username != null) {
            return telegram.findPublicChat(username);
        }
        if (isPrivateMessageLink(target)) {
            throw new TelegramException(-1,
                    "t.me/c/... message links are not supported. Use an invite link (t.me/+...) or a public username.");
        }
        return BotManagementService.resolveChat(telegram, target);
    }

    private static TdApi.Chat chatFromInviteLink(TelegramService telegram, String invite) {
        try {
            TdApi.ChatInviteLinkInfo info = telegram.call(new TdApi.CheckChatInviteLink(invite));
            if (info != null && info.chatId != 0) {
                return telegram.getChat(info.chatId);
            }
        } catch (TelegramException e) {
            log.debug("Could not resolve invite link chat id: {}", e.getMessage());
        }
        return null;
    }

    private static String inviteLink(String target) {
        String path = telegramLinkPath(target);
        if (path == null && target.startsWith("+") && !target.matches("\\+\\d+")) {
            path = target;
        }
        if (path == null) {
            return null;
        }
        if (path.startsWith("+") || path.startsWith("joinchat/")) {
            return "https://t.me/" + path;
        }
        return null;
    }

    private static String publicUsername(String target) {
        String path = telegramLinkPath(target);
        if (path == null) {
            return null;
        }
        int slash = path.indexOf('/');
        if (slash >= 0) {
            path = path.substring(0, slash);
        }
        if (path.isBlank() || path.startsWith("+") || path.startsWith("joinchat/")
                || path.equals("c") || path.equals("s") || path.equals("joinchat")) {
            return null;
        }
        return path;
    }

    private static boolean isPrivateMessageLink(String target) {
        String path = telegramLinkPath(target);
        return path != null && path.startsWith("c/");
    }

    private static String telegramLinkPath(String target) {
        String t = target.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        String[] prefixes = {
                "https://www.t.me/", "http://www.t.me/", "www.t.me/",
                "https://t.me/", "http://t.me/", "t.me/",
                "https://telegram.me/", "http://telegram.me/", "telegram.me/",
                "https://www.telegram.me/", "http://www.telegram.me/", "www.telegram.me/"
        };
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String path = t.substring(prefix.length());
                int cut = path.indexOf('?');
                if (cut >= 0) {
                    path = path.substring(0, cut);
                }
                cut = path.indexOf('#');
                if (cut >= 0) {
                    path = path.substring(0, cut);
                }
                while (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            }
        }
        return null;
    }

    // ---------- helpers ----------

    private TelegramService requireTelegram() {
        TelegramService telegram = runner.getTelegram();
        if (telegram == null || !runner.isLoggedIn()) {
            throw new IllegalStateException("Telegram bot is not running or not logged in. Start it in Bot Manager first.");
        }
        return telegram;
    }

    private static String expandHome(String path) {
        if (path.equals("~")) {
            return System.getProperty("user.home", path);
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home", "~") + path.substring(1);
        }
        return path;
    }

    private static int percent(long done, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.min(100, done * 100 / total);
    }

    private static String displayTitle(Book book) {
        if (book.getTitle() != null && !book.getTitle().isBlank()) {
            return book.getTitle().trim();
        }
        String raw = book.getFilename() != null && !book.getFilename().isBlank()
                ? book.getFilename() : book.getRelPath();
        if (raw == null) {
            return "Book #" + book.getId();
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.isBlank() ? "Book #" + book.getId() : name;
    }

    private static String caption(Book book) {
        String title = displayTitle(book);
        String author = book.getAuthor();
        String text = title;
        if (author != null && !author.isBlank() && !"Unknown_Author".equalsIgnoreCase(author.trim())) {
            text += "\n" + author.trim();
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private void purgeOldJobs() {
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        jobs.entrySet().removeIf(e -> e.getValue().isDone() && e.getValue().getCreatedAt() < cutoff);
    }

    private record Candidate(Book book, Path path, long size) {
    }
}
