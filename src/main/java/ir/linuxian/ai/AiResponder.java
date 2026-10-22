package ir.linuxian.ai;

public interface AiResponder {
    /** Returns the AI's answer to userText. chatId lets implementations keep per-chat history. */
    String reply(long chatId, String userText) throws Exception;
}
