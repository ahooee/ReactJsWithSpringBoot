package ir.linuxian.telegram;

public class TelegramException extends Exception {
    private final int code;

    public TelegramException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
