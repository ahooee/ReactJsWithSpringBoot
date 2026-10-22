package ir.linuxian;

import ir.linuxian.ai.AiResponder;
import ir.linuxian.ai.OpenAiResponder;
import ir.linuxian.app.ChatApp;
import ir.linuxian.app.ConsolePrompt;
import ir.linuxian.listener.AutoResponder;
import ir.linuxian.listener.IncomingMessagePrinter;
import ir.linuxian.telegram.MessageReader;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.SendTracker;
import ir.linuxian.telegram.TelegramService;

public class Main {
    private static final int API_ID = 1006772;                // your api_id
    private static final String API_HASH = "8e49b4e91bf667471bd0d0958897eed3";  // your api_hash

    public static void main(String[] args) throws Exception {
        ConsolePrompt prompt = new ConsolePrompt();

        try (TelegramService telegram = new TelegramService(API_ID, API_HASH, prompt)) {
            MessageReader reader = new MessageReader(telegram);
            SendTracker tracker = new SendTracker(telegram, reader, true);   // true = verbose
            telegram.addUpdateListener(tracker::onUpdate);
            MessageSender sender = new MessageSender(telegram, tracker);

            telegram.addMessageListener(new IncomingMessagePrinter(telegram, reader));

            AutoResponder autoResponder = null;
            String apiKey = System.getenv("GROQ_API_KEY");

            if (apiKey != null && !apiKey.isBlank()) {
                OpenAiResponder ai = new OpenAiResponder(
                        apiKey,
                        env("GROQ_MODEL", "openai/gpt-oss-20b"),
                        env("GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
                        "You are an automated assistant replying on behalf of the account owner. "
                                + "Be brief and helpful, and mention you are an AI if asked.");
                autoResponder = new AutoResponder(telegram, reader, sender, ai, 3000);
                boolean aiVerbose = env("AI_VERBOSE", "false").equalsIgnoreCase("true");
                autoResponder.setVerbose(aiVerbose);
                ai.setVerbose(aiVerbose);
                telegram.addMessageListener(autoResponder);
            } else {
                System.out.println("GROQ_API_KEY not set: AI auto-reply is disabled.");
            }

            telegram.start();   // login via terminal prompts
            new ChatApp(telegram, sender, autoResponder, prompt).run();
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
