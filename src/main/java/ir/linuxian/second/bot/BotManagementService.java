package ir.linuxian.second.bot;

import ir.linuxian.ai.OpenAiResponder;
import ir.linuxian.listener.AutoResponder;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.SendTracker;
import ir.linuxian.telegram.TelegramException;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backend for the admin Bot Manager: settings, allow-list, AI models and
 * message sending with progress jobs.
 */
@Service
public class BotManagementService {

    private static final Logger log = LoggerFactory.getLogger(BotManagementService.class);

    private static final List<String> FALLBACK_MODELS = List.of(
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "qwen/qwen3-32b",
            "deepseek-r1-distill-llama-70b",
            "gemma2-9b-it");

    private final TelegramBotRunner runner;
    private final BotSettingStore store;
    private final TelegramBotProperties props;
    private final Path uploadDir;

    private final Map<String, BotSendJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService sendPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bot-admin-send");
        t.setDaemon(true);
        return t;
    });
    // Admin sends go one at a time so upload progress maps to the right job.
    private final Object sendLock = new Object();

    public BotManagementService(TelegramBotRunner runner, BotSettingStore store,
                                TelegramBotProperties props,
                                @Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.runner = runner;
        this.store = store;
        this.props = props;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir.resolve("bot"));
    }

    // ---------- status ----------

    public Map<String, Object> status() {
        BotSettingStore.EffectiveSettings eff = store.effective(props);
        AutoResponder responder = runner.getAutoResponder();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", eff.enabled());
        m.put("running", runner.isRunning());
        m.put("loggedIn", runner.isLoggedIn());
        m.put("apiId", runner.isRunning() ? runner.getCurrentApiId() : eff.apiId());
        m.put("aiConfigured", eff.aiApiKeySet());
        m.put("autoReplyEnabled", responder != null && responder.isEnabled());
        m.put("allowedChats", allowedChats().size());
        return m;
    }

    // ---------- settings ----------

    public Map<String, Object> settings() {
        return store.asMap(props);
    }

    /**
     * Saves admin settings. AI + behaviour keys apply live; connection keys
     * (enabled/apiId/apiHash/databaseDirectory) restart the bot.
     */
    public Map<String, Object> updateSettings(Map<String, String> values) {
        BotSettingStore.EffectiveSettings before = store.effective(props);
        store.saveAll(values);
        BotSettingStore.EffectiveSettings after = store.effective(props);

        boolean connectionChanged = before.enabled() != after.enabled()
                || before.apiId() != after.apiId()
                || !before.apiHash().equals(after.apiHash())
                || !before.databaseDirectory().equals(after.databaseDirectory());
        boolean restarted = false;
        if (connectionChanged) {
            runner.restart();
            restarted = true;
        } else {
            runner.setTrackerVerbose(after.verbose());
            runner.ensureAutoResponder(after);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", store.asMap(props));
        out.put("restarted", restarted);
        out.put("running", runner.isRunning());
        return out;
    }

    public Map<String, Object> restart() {
        runner.restart();
        return status();
    }

    // ---------- telegram account login (Settings page wizard) ----------

    /** What the admin login wizard needs right now: the pending TDLib
     * step (phone/code/2FA), any error from the last attempt, and session
     * flags. */
    public Map<String, Object> loginStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.putAll(runner.getLoginPrompt().status());
        m.put("running", runner.isRunning());
        m.put("loggedIn", runner.isLoggedIn());
        m.put("enabled", store.effective(props).enabled());
        return m;
    }

    /**
     * Starts (or continues) a login with the account's phone number. If no
     * client is live yet (bot disabled or thread died) a fresh one is
     * started; TDLib then sends the code and the wizard moves on to the
     * code step.
     */
    public Map<String, Object> loginStart(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required (international format, e.g. +98912…).");
        }
        if (!store.effective(props).enabled()) {
            throw new IllegalStateException(
                    "Telegram is disabled — enable it in Bot Manager (connection settings) first.");
        }
        AdminLoginPrompt prompt = runner.getLoginPrompt();
        AdminLoginPrompt.Phase phase = prompt.currentPhase();
        if (phase == AdminLoginPrompt.Phase.NEEDS_CODE) {
            throw new IllegalStateException("A login is already waiting for the verification code.");
        }
        if (phase == AdminLoginPrompt.Phase.NEEDS_PASSWORD) {
            throw new IllegalStateException("A login is already waiting for the 2FA password.");
        }
        if (!runner.isRunning()) {
            prompt.cancel();     // drop a stale pending step before a new client
            runner.restart();
        }
        // Queued for the live (or fresh) client; consumed at WaitPhoneNumber.
        prompt.submitPhone(phone.trim());
        return loginStatus();
    }

    public Map<String, Object> loginCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Verification code is required.");
        }
        runner.getLoginPrompt().submitCode(code.trim());
        return loginStatus();
    }

    public Map<String, Object> loginPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("2FA password is required.");
        }
        runner.getLoginPrompt().submitPassword(password.trim());
        return loginStatus();
    }

    /** Aborts a pending login: cancels the step and reopens the session
     * fresh, so the wizard starts over cleanly. */
    public Map<String, Object> loginCancel() {
        runner.getLoginPrompt().cancel();
        if (runner.isRunning()) {
            runner.restart();
        }
        return loginStatus();
    }

    /**
     * Logs the Telegram account out of the shared tdlib session and closes
     * the client; on next login TDLib asks for phone/code again.
     */
    public Map<String, Object> logout() {
        TelegramService telegram = runner.getTelegram();
        if (telegram != null && runner.isLoggedIn()) {
            try {
                telegram.call(new TdApi.LogOut());
            } catch (TelegramException e) {
                throw new IllegalStateException("Telegram logout failed: " + e.getMessage());
            }
        }
        runner.getLoginPrompt().cancel();
        if (runner.isRunning()) {
            runner.restart();
        }
        return loginStatus();
    }

    // ---------- allow-list ----------

    public List<Map<String, Object>> allowedChats() {
        List<Map<String, Object>> out = new ArrayList<>();
        AutoResponder responder = runner.getAutoResponder();
        Map<Long, String> labels = responder == null ? Map.of() : responder.getAllowedLabels();
        for (BotSettingStore.AllowedChat c : store.getAllowedChats()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chatId", c.chatId());
            m.put("label", c.label());
            m.put("allowed", responder == null || responder.isChatAllowed(c.chatId()));
            if (labels.get(c.chatId()) != null) {
                m.put("label", labels.get(c.chatId()));
            }
            out.add(m);
        }
        return out;
    }

    /** Resolves @username / numeric id / +phone to a chat and adds it to the allow-list. */
    public Map<String, Object> addAllowedChat(String target) throws TelegramException {
        TelegramService telegram = requireTelegram();
        TdApi.Chat chat = resolveChat(telegram, target);
        String label = targetLabel(target);
        AutoResponder responder = runner.getAutoResponder();

        List<BotSettingStore.AllowedChat> chats = store.getAllowedChats();
        chats.removeIf(c -> c.chatId() == chat.id);
        chats.add(new BotSettingStore.AllowedChat(chat.id, label));
        store.saveAllowedChats(chats);
        if (responder != null) {
            responder.allowChat(chat.id, label);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chatId", chat.id);
        m.put("label", label);
        m.put("title", chat.title);
        return m;
    }

    public void removeAllowedChat(long chatId) {
        List<BotSettingStore.AllowedChat> chats = store.getAllowedChats();
        chats.removeIf(c -> c.chatId() == chatId);
        store.saveAllowedChats(chats);
        AutoResponder responder = runner.getAutoResponder();
        if (responder != null) {
            responder.removeChat(chatId);
        }
    }

    public void clearAllowedChats() {
        store.clearAllowedChats();
        AutoResponder responder = runner.getAutoResponder();
        if (responder != null) {
            responder.clearAllowedChats();
        }
    }

    // ---------- AI models ----------

    /** Live Groq/OpenAI model list for the current (or given test) credentials. */
    public Map<String, Object> aiModels(String apiKey, String baseUrl) {
        BotSettingStore.EffectiveSettings eff = store.effective(props);
        String key = apiKey != null && !apiKey.isBlank() ? apiKey : eff.aiApiKey();
        String base = baseUrl != null && !baseUrl.isBlank() ? baseUrl : eff.aiBaseUrl();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", eff.aiModel());
        if (key == null || key.isBlank()) {
            out.put("models", FALLBACK_MODELS);
            out.put("source", "fallback");
            out.put("hint", "Enter an AI key to load the live model list.");
            return out;
        }
        try {
            OpenAiResponder probe = new OpenAiResponder(key, eff.aiModel(), base, "");
            List<String> models = probe.listModels();
            out.put("models", models.isEmpty() ? FALLBACK_MODELS : models);
            out.put("source", models.isEmpty() ? "fallback" : "live");
        } catch (Exception e) {
            log.warn("Could not list AI models: {}", e.getMessage());
            out.put("models", FALLBACK_MODELS);
            out.put("source", "fallback");
            out.put("hint", "Live list failed (" + e.getMessage() + "), showing known models.");
        }
        return out;
    }

    // ---------- sending ----------

    public record SendRequest(String target, String kind, String text, String caption,
                              String filePath, Double latitude, Double longitude) {
    }

    /** Queues a send; poll {@link #getJob(String)} for progress. */
    public BotSendJob send(SendRequest req) {
        requireTelegram();
        String kind = req.kind() == null ? "text" : req.kind().trim().toLowerCase();
        if (!List.of("text", "photo", "document", "video", "audio", "voice", "location").contains(kind)) {
            throw new IllegalArgumentException("Unsupported kind: " + req.kind());
        }
        if (req.target() == null || req.target().isBlank()) {
            throw new IllegalArgumentException("Target is required (username, user id or phone).");
        }
        String jobId = UUID.randomUUID().toString();
        BotSendJob job = new BotSendJob(jobId, kind, req.target().trim());
        jobs.put(jobId, job);
        purgeOldJobs();
        sendPool.submit(() -> doSend(job, req, kind));
        return job;
    }

    public BotSendJob getJob(String jobId) {
        BotSendJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Unknown send job: " + jobId);
        }
        return job;
    }

    /** Stores an uploaded media file for a bot send, returns the local path. */
    public String storeUpload(org.springframework.web.multipart.MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        Path target = uploadDir.resolve("bot").resolve(UUID.randomUUID() + ext);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }

    private void doSend(BotSendJob job, SendRequest req, String kind) {
        synchronized (sendLock) {
            job.setStatus(BotSendJob.Status.SENDING);
            TelegramService telegram = runner.getTelegram();
            MessageSender sender = runner.getSender();
            SendTracker sendTracker = runner.getTracker();
            if (telegram == null || sender == null || sendTracker == null) {
                fail(job, "Bot is not running.");
                return;
            }
            sendTracker.setProgressListener(p -> {
                job.setPercent(p.percent());
                job.setDoneBytes(p.doneBytes());
                job.setTotalBytes(p.totalBytes());
            });
            try {
                TdApi.Chat chat = resolveChat(telegram, job.getTarget());
                job.setChatTitle(chat.title);
                String caption = req.caption() == null ? "" : req.caption();
                TdApi.Message sent = switch (kind) {
                    case "photo" -> sender.sendPhoto(chat.id, requireFile(req), caption);
                    case "document" -> sender.sendDocument(chat.id, requireFile(req), caption);
                    case "video" -> sender.sendVideo(chat.id, requireFile(req), caption);
                    case "audio" -> sender.sendAudio(chat.id, requireFile(req), caption);
                    case "voice" -> sender.sendVoiceNote(chat.id, requireFile(req), "");
                    case "location" -> {
                        if (req.latitude() == null || req.longitude() == null) {
                            throw new IllegalArgumentException("Latitude and longitude are required.");
                        }
                        yield sender.sendLocation(chat.id, req.latitude(), req.longitude());
                    }
                    default -> sender.sendText(chat.id, req.text() == null ? "" : req.text());
                };
                job.setMessageId(sent.id);
                job.setPercent(100);
                job.setMessage("Sent to " + chat.title);
                job.setStatus(BotSendJob.Status.SENT);
            } catch (Exception e) {
                fail(job, e.getMessage());
            } finally {
                sendTracker.setProgressListener(null);
            }
        }
    }

    private static void fail(BotSendJob job, String error) {
        job.setError(error == null || error.isBlank() ? "Send failed." : error);
        job.setStatus(BotSendJob.Status.FAILED);
    }

    private static String requireFile(SendRequest req) {
        if (req.filePath() == null || req.filePath().isBlank()) {
            throw new IllegalArgumentException("A file is required for this message kind.");
        }
        if (!Files.isRegularFile(Paths.get(req.filePath()))) {
            throw new IllegalArgumentException("Uploaded file not found, please upload again.");
        }
        return req.filePath();
    }

    // ---------- helpers ----------

    private TelegramService requireTelegram() {
        TelegramService telegram = runner.getTelegram();
        if (telegram == null || !runner.isLoggedIn()) {
            throw new IllegalStateException("Bot is not running or not logged in yet.");
        }
        return telegram;
    }

    /**
     * Accepts @username (or bare username), a numeric user id, or a phone
     * number starting with '+' — same rules as the console bot.
     */
    public static TdApi.Chat resolveChat(TelegramService telegram, String target) throws TelegramException {
        String t = target == null ? "" : target.trim();
        if (t.isEmpty()) {
            throw new TelegramException(-1, "Empty target.");
        }
        if (t.startsWith("+")) {
            return telegram.findChatByPhoneNumber(t);
        }
        if (t.matches("\\d+")) {
            return telegram.findChatByUserId(Long.parseLong(t));
        }
        if (t.startsWith("@")) {
            t = t.substring(1);
        }
        return telegram.findPublicChat(t);
    }

    private static String targetLabel(String target) {
        String t = target == null ? "" : target.trim();
        if (t.startsWith("+") || t.matches("\\d+")) {
            return t;
        }
        return t.startsWith("@") ? t : "@" + t;
    }

    private void purgeOldJobs() {
        long cutoff = System.currentTimeMillis() - 30 * 60 * 1000L;
        jobs.entrySet().removeIf(e -> e.getValue().isDone() && e.getValue().getCreatedAt() < cutoff);
    }
}
