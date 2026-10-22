package ir.linuxian.telegram;

import ir.linuxian.telegram.IncomingMessage.Kind;
import org.drinkless.tdlib.TdApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Turns raw TDLib messages into IncomingMessage objects and downloads attached files. */
public class MessageReader {
    private final TelegramService telegram;
    private final Map<Long, String> nameCache = new ConcurrentHashMap<>();

    public MessageReader(TelegramService telegram) {
        this.telegram = telegram;
    }

    public IncomingMessage read(TdApi.Message m) {
        long sender = m.senderId instanceof TdApi.MessageSenderUser u ? u.userId : 0;
        TdApi.MessageContent c = m.content;

        Kind kind = Kind.OTHER;
        String text = "";
        int fileId = 0;
        String fileName = "";

        if (c instanceof TdApi.MessageText t) {
            kind = Kind.TEXT;
            text = t.text.text;
        } else if (c instanceof TdApi.MessagePhoto p) {
            kind = Kind.PHOTO;
            text = caption(p.caption);
            TdApi.PhotoSize[] sizes = p.photo.sizes;
            if (sizes.length > 0) fileId = sizes[sizes.length - 1].photo.id; // largest size
        } else if (c instanceof TdApi.MessageVideo v) {
            kind = Kind.VIDEO;
            text = caption(v.caption);
            fileId = v.video.video.id;
            fileName = v.video.fileName;
        } else if (c instanceof TdApi.MessageAudio a) {
            kind = Kind.AUDIO;
            text = caption(a.caption);
            fileId = a.audio.audio.id;
            fileName = a.audio.fileName;
        } else if (c instanceof TdApi.MessageVoiceNote v) {
            kind = Kind.VOICE;
            text = caption(v.caption);
            fileId = v.voiceNote.voice.id;
        } else if (c instanceof TdApi.MessageVideoNote v) {
            kind = Kind.VIDEO_NOTE;
            fileId = v.videoNote.video.id;
        } else if (c instanceof TdApi.MessageDocument d) {
            kind = Kind.DOCUMENT;
            text = caption(d.caption);
            fileId = d.document.document.id;
            fileName = d.document.fileName;
        } else if (c instanceof TdApi.MessageAnimation a) {
            kind = Kind.ANIMATION;
            text = caption(a.caption);
            fileId = a.animation.animation.id;
            fileName = a.animation.fileName;
        } else if (c instanceof TdApi.MessageSticker s) {
            kind = Kind.STICKER;
            text = s.sticker.emoji;
            fileId = s.sticker.sticker.id;
        } else if (c instanceof TdApi.MessageLocation l) {
            kind = Kind.LOCATION;
            text = l.location.latitude + "," + l.location.longitude;
        } else if (c instanceof TdApi.MessageContact ct) {
            kind = Kind.CONTACT;
            text = (ct.contact.firstName + " " + ct.contact.lastName).trim() + " " + ct.contact.phoneNumber;
        }

        return new IncomingMessage(m.chatId, m.id, sender, m.isOutgoing, m.date, kind, text, fileId, fileName);
    }

    /** Human-readable one-line description. */
    public String describe(IncomingMessage m) {
        return switch (m.kind()) {
            case TEXT -> m.text();
            case LOCATION -> "[location] " + m.text();
            case CONTACT -> "[contact] " + m.text();
            default -> "[" + m.kind().name().toLowerCase() + "]"
                    + (m.fileName() == null || m.fileName().isEmpty() ? "" : " " + m.fileName())
                    + (m.text() == null || m.text().isEmpty() ? "" : " " + m.text());
        };
    }

    /** Downloads a file (blocking) and returns its local path. */
    public String download(int fileId) throws TelegramException {
        TdApi.DownloadFile request = new TdApi.DownloadFile();
        request.fileId = fileId;
        request.priority = 1;
        request.offset = 0;
        request.limit = 0;
        request.synchronous = true;

        TdApi.File file = telegram.call(request);
        return file.local.path;
    }

    public String senderName(long userId) {
        if (userId == 0) return "unknown";
        return nameCache.computeIfAbsent(userId, id -> {
            try {
                TdApi.User user = telegram.getUser(id);
                return (user.firstName + " " + user.lastName).trim();
            } catch (TelegramException e) {
                return "user " + id;
            }
        });
    }

    private static String caption(TdApi.FormattedText text) {
        return text == null ? "" : text.text;
    }
}
