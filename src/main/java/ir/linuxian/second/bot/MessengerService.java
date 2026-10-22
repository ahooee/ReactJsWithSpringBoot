package ir.linuxian.second.bot;

import ir.linuxian.telegram.IncomingMessage;
import ir.linuxian.telegram.MessageReader;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.TelegramException;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

/**
 * Backend for the admin Messenger (Telegram-web-like section of the
 * dashboard): chat list, message history, read receipts, text replies and
 * media/file sends through the embedded TDLib client.
 */
@Service
public class MessengerService {

    private static final Logger log = LoggerFactory.getLogger(MessengerService.class);

    private final TelegramBotRunner runner;
    private final Path uploadDir;
    // Synchronous TDLib downloads block a request thread until the whole
    // file arrives; cap them so a media-heavy chat cannot stall the server.
    private final java.util.concurrent.Semaphore downloadSlots = new java.util.concurrent.Semaphore(3);

    public MessengerService(TelegramBotRunner runner,
                            @Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.runner = runner;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir.resolve("messenger"));
    }

    // ---------- status ----------

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", runner.isRunning());
        m.put("loggedIn", runner.isLoggedIn());
        if (runner.isLoggedIn()) {
            try {
                TelegramService telegram = requireTelegram();
                TdApi.User me = telegram.call(new TdApi.GetMe());
                Map<String, Object> self = new LinkedHashMap<>();
                self.put("id", me.id);
                self.put("name", (me.firstName + " " + me.lastName).trim());
                m.put("me", self);
            } catch (TelegramException e) {
                log.warn("Messenger status: could not load self: {}", e.getMessage());
            }
        }
        return m;
    }

    // ---------- chats ----------

    /** Lists chats from the main chat list, most recent first. */
    public List<Map<String, Object>> listChats(int limit) throws TelegramException {
        TelegramService telegram = requireTelegram();
        MessageReader reader = requireReader();
        int n = Math.min(Math.max(limit, 1), 100);
        TdApi.Chats chats = telegram.call(new TdApi.GetChats(new TdApi.ChatListMain(), n));
        List<Map<String, Object>> out = new ArrayList<>();
        if (chats.chatIds == null) {
            return out;
        }
        for (long chatId : chats.chatIds) {
            try {
                out.add(chatMap(telegram, reader, telegram.getChat(chatId)));
            } catch (TelegramException e) {
                log.warn("Messenger: skipping chat {} ({}).", chatId, e.getMessage());
            }
        }
        return out;
    }

    public Map<String, Object> getChat(long chatId) throws TelegramException {
        TelegramService telegram = requireTelegram();
        return chatMap(telegram, requireReader(), telegram.getChat(chatId));
    }

    /** Resolves @username / numeric user id / +phone to a chat (for "new chat"). */
    public Map<String, Object> resolveTarget(String target) throws TelegramException {
        TelegramService telegram = requireTelegram();
        if (target == null || target.isBlank()) {
            throw new TelegramException(-1, "Target is required (username, user id or phone).");
        }
        TdApi.Chat chat = BotManagementService.resolveChat(telegram, target);
        return chatMap(telegram, requireReader(), chat);
    }

    // ---------- messages ----------

    /**
     * Returns up to {@code limit} messages ending at {@code fromMessageId}
     * (0 = latest), oldest first. {@code canLoadMore} tells the UI whether an
     * older page probably exists.
     */
    public Map<String, Object> getMessages(long chatId, long fromMessageId, int limit) throws TelegramException {
        TelegramService telegram = requireTelegram();
        MessageReader reader = requireReader();
        int n = Math.min(Math.max(limit, 1), 100);
        TdApi.Chat chat = telegram.getChat(chatId);
        // TDLib can briefly under-fill the first page while it fetches from
        // the server; retry so the UI does not show a single message with no
        // way to load older ones.
        TdApi.Messages history = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            history = telegram.call(
                    new TdApi.GetChatHistory(chatId, fromMessageId, 0, n, false));
            if (history.messages != null
                    && (history.messages.length >= n || fromMessageId != 0)) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history.messages != null) {
            // TDLib returns newest first; the UI wants oldest first.
            for (int i = history.messages.length - 1; i >= 0; i--) {
                messages.add(messageMap(telegram, reader, history.messages[i], chat.title));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chatId", chatId);
        out.put("messages", messages);
        out.put("canLoadMore", history.messages != null && history.messages.length >= n);
        return out;
    }

    /** Marks messages as read (opens the chat, then views the ids). */
    public Map<String, Object> markRead(long chatId, List<Long> messageIds) throws TelegramException {
        TelegramService telegram = requireTelegram();
        long[] ids = messageIds == null ? new long[0]
                : messageIds.stream().filter(id -> id != null && id > 0).mapToLong(Long::longValue).toArray();
        telegram.call(new TdApi.OpenChat(chatId));
        if (ids.length > 0) {
            telegram.call(new TdApi.ViewMessages(chatId, ids, new TdApi.MessageSourceChatHistory(), true));
        }
        return Map.of("ok", true, "marked", ids.length);
    }

    // ---------- sending ----------

    public Map<String, Object> sendText(long chatId, String text, long replyToMessageId) throws TelegramException {
        TelegramService telegram = requireTelegram();
        MessageSender sender = requireSender();
        if (text == null || text.isBlank()) {
            throw new TelegramException(-1, "Message text is required.");
        }
        // Throws if the chat does not exist / cannot be written to.
        telegram.getChat(chatId);
        TdApi.Message sent = replyToMessageId > 0
                ? sender.replyText(chatId, replyToMessageId, text)
                : sender.sendText(chatId, text);
        return messageMap(telegram, requireReader(), sent, "");
    }

    /**
     * Sends an uploaded file. {@code kind} is photo|video|audio|voice|document
     * or blank/auto (detected from content type and extension).
     */
    public Map<String, Object> sendFile(long chatId, String kind, String caption,
                                        MultipartFile file, long replyToMessageId)
            throws TelegramException, IOException {
        TelegramService telegram = requireTelegram();
        MessageSender sender = requireSender();
        if (file == null || file.isEmpty()) {
            throw new TelegramException(-1, "A file is required.");
        }
        telegram.getChat(chatId);
        String resolved = kind == null || kind.isBlank() || kind.equalsIgnoreCase("auto")
                ? detectKind(file) : kind.trim().toLowerCase();
        if (!List.of("photo", "video", "audio", "voice", "document").contains(resolved)) {
            throw new TelegramException(-1, "Unsupported kind: " + kind);
        }
        String stored = storeUpload(file);
        String text = caption == null ? "" : caption;
        TdApi.Message sent = switch (resolved) {
            case "photo" -> sender.sendPhoto(chatId, stored, text, replyToMessageId);
            case "video" -> sender.sendVideo(chatId, stored, text, replyToMessageId);
            case "audio" -> sender.sendAudio(chatId, stored, text, replyToMessageId);
            case "voice" -> sender.sendVoiceNote(chatId, stored, text);
            default -> sender.sendDocument(chatId, stored, text, replyToMessageId);
        };
        return messageMap(telegram, requireReader(), sent, "");
    }

    // ---------- files ----------

    /** Downloads a Telegram file (blocking) and returns its local path. */
    public Path downloadFilePath(int fileId) throws TelegramException {
        TelegramService telegram = requireTelegram();
        MessageReader reader = requireReader();
        if (fileId == 0) {
            throw new TelegramException(-1, "Message has no file.");
        }
        try {
            downloadSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(-1, "Download interrupted while waiting for a slot.");
        }
        try {
            String local = reader.download(fileId);
            Path path = Paths.get(local);
            if (!Files.isRegularFile(path)) {
                throw new TelegramException(-1, "Downloaded file not found.");
            }
            return path;
        } finally {
            downloadSlots.release();
        }
    }

    // ---------- helpers ----------

    private TelegramService requireTelegram() {
        TelegramService telegram = runner.getTelegram();
        if (telegram == null || !runner.isLoggedIn()) {
            throw new IllegalStateException("Bot is not running or not logged in yet.");
        }
        return telegram;
    }

    private MessageReader requireReader() {
        MessageReader reader = runner.getReader();
        if (reader == null) {
            throw new IllegalStateException("Bot is not running or not logged in yet.");
        }
        return reader;
    }

    private MessageSender requireSender() {
        MessageSender sender = runner.getSender();
        if (sender == null) {
            throw new IllegalStateException("Bot is not running or not logged in yet.");
        }
        return sender;
    }

    private Map<String, Object> chatMap(TelegramService telegram, MessageReader reader, TdApi.Chat chat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", chat.id);
        m.put("title", chat.title);
        m.put("type", chatType(chat));
        m.put("unreadCount", chat.unreadCount);
        m.put("markedUnread", chat.isMarkedAsUnread);
        int photoFileId = 0;
        if (chat.photo != null && chat.photo.small != null) {
            photoFileId = chat.photo.small.id;
        }
        m.put("photoFileId", photoFileId);
        if (chat.lastMessage != null) {
            IncomingMessage last = reader.read(chat.lastMessage);
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("id", last.messageId());
            lm.put("date", last.date());
            lm.put("outgoing", last.outgoing());
            lm.put("preview", reader.describe(last));
            m.put("lastMessage", lm);
        } else {
            m.put("lastMessage", null);
        }
        return m;
    }

    private static String chatType(TdApi.Chat chat) {
        if (chat.type instanceof TdApi.ChatTypeSupergroup sg) {
            return sg.isChannel ? "channel" : "supergroup";
        }
        if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            return "group";
        }
        if (chat.type instanceof TdApi.ChatTypeSecret) {
            return "secret";
        }
        return "private";
    }

    private Map<String, Object> messageMap(TelegramService telegram, MessageReader reader,
                                           TdApi.Message message, String chatTitle) {
        IncomingMessage im = reader.read(message);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", im.messageId());
        m.put("chatId", im.chatId());
        m.put("senderId", im.senderUserId());
        m.put("senderName", senderDisplayName(telegram, reader, message, chatTitle));
        m.put("outgoing", im.outgoing());
        m.put("date", im.date());
        m.put("kind", im.kind().name().toLowerCase());
        m.put("text", im.text());
        m.put("fileId", im.fileId());
        m.put("fileName", im.fileName());
        m.put("replyToMessageId", replyToOf(message));
        return m;
    }

    private String senderDisplayName(TelegramService telegram, MessageReader reader,
                                     TdApi.Message message, String chatTitle) {
        if (message.senderId instanceof TdApi.MessageSenderUser u) {
            return reader.senderName(u.userId);
        }
        if (message.senderId instanceof TdApi.MessageSenderChat c) {
            try {
                return telegram.getChat(c.chatId).title;
            } catch (TelegramException e) {
                return chatTitle == null || chatTitle.isEmpty() ? "channel" : chatTitle;
            }
        }
        return "unknown";
    }

    private static long replyToOf(TdApi.Message message) {
        if (message.replyTo instanceof TdApi.MessageReplyToMessage r) {
            return r.messageId;
        }
        return 0;
    }

    private String storeUpload(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        Path target = uploadDir.resolve("messenger").resolve(UUID.randomUUID() + ext);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }

    private static String detectKind(MultipartFile file) {
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (ct.startsWith("image/") || name.matches(".*\\.(jpg|jpeg|png|webp|bmp)$")) {
            return "photo";
        }
        if (ct.startsWith("video/") || name.matches(".*\\.(mp4|mov|mkv|webm|avi)$")) {
            return "video";
        }
        if (name.endsWith(".ogg") || name.endsWith(".oga")) {
            return "voice";
        }
        if (ct.startsWith("audio/") || name.matches(".*\\.(mp3|wav|m4a|flac|aac|opus)$")) {
            return "audio";
        }
        return "document";
    }
}
