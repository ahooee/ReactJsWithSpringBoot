package ir.linuxian.listener;

import ir.linuxian.telegram.IncomingMessage;
import ir.linuxian.telegram.MessageReader;
import ir.linuxian.telegram.TelegramException;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;

import java.util.function.Consumer;

/** Prints every incoming message, whatever its type. */
public class IncomingMessagePrinter implements Consumer<TdApi.Message> {
    private final TelegramService telegram;
    private final MessageReader reader;

    public IncomingMessagePrinter(TelegramService telegram, MessageReader reader) {
        this.telegram = telegram;
        this.reader = reader;
    }

    @Override
    public void accept(TdApi.Message message) {
        if (message.isOutgoing) return;

        IncomingMessage in = reader.read(message);
        String chatTitle;
        try {
            chatTitle = telegram.getChat(in.chatId()).title;
        } catch (TelegramException e) {
            chatTitle = "chat " + in.chatId();
        }

        System.out.println("\n<< [" + chatTitle + "] " + reader.senderName(in.senderUserId())
                + ": " + reader.describe(in));
    }
}
