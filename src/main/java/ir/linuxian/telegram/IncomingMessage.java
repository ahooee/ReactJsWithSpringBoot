package ir.linuxian.telegram;

/** Simplified, TDLib-independent view of a received message. */
public record IncomingMessage(
        long chatId,
        long messageId,
        long senderUserId,
        boolean outgoing,
        int date,
        Kind kind,
        String text,       // message text, caption, or "lat,lon" / "name phone" for location/contact
        int fileId,        // 0 if the message has no downloadable file
        String fileName) {

    public enum Kind {
        TEXT, PHOTO, VIDEO, AUDIO, VOICE, VIDEO_NOTE, DOCUMENT, ANIMATION, STICKER, LOCATION, CONTACT, OTHER
    }

    public boolean hasFile() {
        return fileId != 0;
    }
}
