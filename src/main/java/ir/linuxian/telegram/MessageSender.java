package ir.linuxian.telegram;

import org.drinkless.tdlib.TdApi;

import java.io.File;

/** Sends every kind of message. All methods block until TDLib accepts the message. */
public class MessageSender {

    private final TelegramService telegram;
    private final SendTracker tracker;

    public MessageSender(TelegramService telegram,SendTracker tracker) {
        this.tracker = tracker;
        this.telegram = telegram;
    }

    // ---------- text ----------

    public TdApi.Message sendText(long chatId, String text) throws TelegramException {
        return send(chatId, textContent(text), 0, "text (" + text.length() + " chars)");
    }

    /** Sends text as a reply to another message. */
    public TdApi.Message replyText(long chatId, long replyToMessageId, String text) throws TelegramException {
        return send(chatId, textContent(text), replyToMessageId, "text (" + text.length() + " chars)");
    }

    // ---------- media (local files) ----------

    public TdApi.Message sendPhoto(long chatId, String path, String caption) throws TelegramException {
        return sendPhoto(chatId, path, caption, 0);
    }

    /** Sends a photo, optionally as a reply to another message. */
    public TdApi.Message sendPhoto(long chatId, String path, String caption, long replyToMessageId) throws TelegramException {
        TdApi.InputPhoto photo = new TdApi.InputPhoto();
        photo.photo = localFile(path);
        photo.addedStickerFileIds = new int[0];

        TdApi.InputMessagePhoto content = new TdApi.InputMessagePhoto();
        content.photo = photo;
        content.caption = formatted(caption);
        return send(chatId, content, replyToMessageId,"photo " + new File(path).getName());
    }

    public TdApi.Message sendVideo(long chatId, String path, String caption) throws TelegramException {
        return sendVideo(chatId, path, caption, 0);
    }

    /** Sends a video, optionally as a reply to another message. */
    public TdApi.Message sendVideo(long chatId, String path, String caption, long replyToMessageId) throws TelegramException {
        TdApi.InputVideo video = new TdApi.InputVideo();
        video.video = localFile(path);
        video.addedStickerFileIds = new int[0];
        video.supportsStreaming = true;

        TdApi.InputMessageVideo content = new TdApi.InputMessageVideo();
        content.video = video;
        content.caption = formatted(caption);
        return send(chatId, content, replyToMessageId,"video " + new File(path).getName());
    }

    public TdApi.Message sendAudio(long chatId, String path, String caption) throws TelegramException {
        return sendAudio(chatId, path, caption, 0);
    }

    /** Sends an audio file, optionally as a reply to another message. */
    public TdApi.Message sendAudio(long chatId, String path, String caption, long replyToMessageId) throws TelegramException {
        TdApi.InputAudio audio = new TdApi.InputAudio();
        audio.audio = localFile(path);

        TdApi.InputMessageAudio content = new TdApi.InputMessageAudio();
        content.audio = audio;
        content.caption = formatted(caption);
        return send(chatId, content, replyToMessageId,"audio " + new File(path).getName());
    }

    /** Voice notes must be OGG files encoded with Opus, otherwise Telegram sends them as a document. */
    public TdApi.Message sendVoiceNote(long chatId, String path, String caption) throws TelegramException {
        TdApi.InputVoiceNote voice = new TdApi.InputVoiceNote();
        voice.voiceNote = localFile(path);

        TdApi.InputMessageVoiceNote content = new TdApi.InputMessageVoiceNote();
        content.voiceNote = voice;
        content.caption = formatted(caption);
        return send(chatId, content, 0,"voiceNote " + new File(path).getName());
    }

    public TdApi.Message sendDocument(long chatId, String path, String caption) throws TelegramException {
        return sendDocument(chatId, path, caption, 0);
    }

    /** Sends a document, optionally as a reply to another message. */
    public TdApi.Message sendDocument(long chatId, String path, String caption, long replyToMessageId) throws TelegramException {
        TdApi.InputDocument document = new TdApi.InputDocument();
        document.document = localFile(path);

        TdApi.InputMessageDocument content = new TdApi.InputMessageDocument();
        content.document = document;
        content.caption = formatted(caption);
        return send(chatId, content, replyToMessageId,"document " + new File(path).getName());
    }

    // ---------- other ----------

    public TdApi.Message sendLocation(long chatId, double latitude, double longitude) throws TelegramException {
        TdApi.Location location = new TdApi.Location();
        location.latitude = latitude;
        location.longitude = longitude;

        TdApi.InputMessageLocation content = new TdApi.InputMessageLocation();
        content.location = location;
        return send(chatId, content, 0,"location " + latitude+" "+longitude);
    }

    // ---------- internals ----------

    private TdApi.Message send(long chatId, TdApi.InputMessageContent content,
                               long replyToMessageId, String what) throws TelegramException {
        TdApi.SendMessage request = new TdApi.SendMessage();
        request.chatId = chatId;
        request.inputMessageContent = content;

        if (replyToMessageId != 0) {
            TdApi.InputMessageReplyToMessage reply = new TdApi.InputMessageReplyToMessage();
            reply.messageId = replyToMessageId;
            request.replyTo = reply;
        }

        TdApi.Message pending = telegram.call(request);   // accepted, still uploading
        return tracker.await(pending, what);              // blocks until sent or failed
    }

    private static TdApi.InputMessageText textContent(String text) {
        TdApi.InputMessageText content = new TdApi.InputMessageText();
        content.text = formatted(text);
        return content;
    }

    private static TdApi.FormattedText formatted(String text) {
        return new TdApi.FormattedText(text == null ? "" : text, new TdApi.TextEntity[0]);
    }

    private static TdApi.InputFile localFile(String path) throws TelegramException {
        File file = new File(path);
        if (!file.isFile()) {
            throw new TelegramException(-1, "File not found: " + path);
        }
        return new TdApi.InputFileLocal(file.getAbsolutePath());
    }
}