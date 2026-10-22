package ir.linuxian.app;

import ir.linuxian.ai.OpenAiResponder;
import ir.linuxian.listener.AutoResponder;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.TelegramException;
import ir.linuxian.telegram.TelegramService;
import org.drinkless.tdlib.TdApi;

import java.util.Map;

public class ChatApp {
    private final TelegramService telegram;
    private final MessageSender sender;
    private final AutoResponder autoResponder; // may be null if no AI key is configured
    private final ConsolePrompt prompt;

    public ChatApp(TelegramService telegram, MessageSender sender,
                   AutoResponder autoResponder, ConsolePrompt prompt) {
        this.telegram = telegram;
        this.sender = sender;
        this.autoResponder = autoResponder;
        this.prompt = prompt;
    }

    public void run() {
        while (true) {
            printMenu();
            String choice = prompt.ask("> ");

            try {
                switch (choice) {
                    case "1" -> {
                        TdApi.Chat chat = askChatByUsername();
                        sender.sendText(chat.id, prompt.askRaw("Message: "));
                        System.out.println("Sent.");
                    }
                    case "2" -> sendFile("photo");
                    case "3" -> sendFile("document");
                    case "4" -> sendFile("video");
                    case "5" -> sendFile("audio");
                    case "6" -> sendFile("voice");
                    case "7" -> {
                        TdApi.Chat chat = askChatByUsername();
                        double lat = Double.parseDouble(prompt.ask("Latitude: "));
                        double lon = Double.parseDouble(prompt.ask("Longitude: "));
                        sender.sendLocation(chat.id, lat, lon);
                        System.out.println("Sent.");
                    }
                    case "8" -> toggleAutoReply();
                    case "9" -> addAutoReplyUsers();
                    case "10" -> showAutoReplySettings();
                    case "11" -> toggleAiVerbose();
                    case "12" -> sendTextByUserId();
                    case "13" -> sendTextByPhoneNumber();
                    case "14" -> changeAiModel();
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (TelegramException e) {
                System.err.println("Error " + e.getCode() + ": " + e.getMessage());
            } catch (NumberFormatException e) {
                System.err.println("That is not a valid number.");
            }
        }
    }

    private void printMenu() {
        String auto = autoResponder == null ? "unavailable"
                : (autoResponder.isEnabled() ? "ON" : "OFF");
        String verb = (autoResponder != null && autoResponder.isVerbose()) ? "ON" : "OFF";
        System.out.println();
        System.out.println("---- Menu (incoming messages appear live as '<<') ----");
        System.out.println("1) Send text (by username)");
        System.out.println("2) Send photo");
        System.out.println("3) Send document");
        System.out.println("4) Send video");
        System.out.println("5) Send audio");
        System.out.println("6) Send voice note (.ogg opus)");
        System.out.println("7) Send location");
        System.out.println("8) Toggle AI auto-reply");
        System.out.println("9) Add user(s) to auto-reply allow-list");
        System.out.println("10) Show auto-reply settings");
        System.out.println("11) Toggle AI verbose logging");
        System.out.println("12) Send text by user ID");
        System.out.println("13) Send text by phone number");
        System.out.println("14) Change AI model");
        System.out.println("0) Exit            [auto-reply: " + auto + ", verbose: " + verb + "]");
    }

    // ---------- chat lookup ----------

    private TdApi.Chat askChatByUsername() throws TelegramException {
        String username = prompt.ask("Username (without @): ");
        if (username.startsWith("@")) username = username.substring(1);
        TdApi.Chat chat = telegram.findPublicChat(username);
        System.out.println("Chat: " + chat.title);
        return chat;
    }

    private TdApi.Chat askChatByUserId() throws TelegramException {
        long userId = Long.parseLong(prompt.ask("User ID (numeric): "));
        TdApi.Chat chat = telegram.findChatByUserId(userId);
        System.out.println("Chat: " + chat.title + " (id " + chat.id + ")");
        return chat;
    }

    private TdApi.Chat askChatByPhoneNumber() throws TelegramException {
        String phone = prompt.ask("Phone number (international format, e.g. +98912...): ");
        TdApi.Chat chat = telegram.findChatByPhoneNumber(phone);
        System.out.println("Chat: " + chat.title + " (id " + chat.id + ")");
        return chat;
    }

    private void sendFile(String type) throws TelegramException {
        TdApi.Chat chat = askChatByUsername();
        String path = prompt.ask("File path: ");
        String caption = type.equals("voice") ? "" : prompt.askRaw("Caption (optional): ");

        switch (type) {
            case "photo" -> sender.sendPhoto(chat.id, path, caption);
            case "document" -> sender.sendDocument(chat.id, path, caption);
            case "video" -> sender.sendVideo(chat.id, path, caption);
            case "audio" -> sender.sendAudio(chat.id, path, caption);
            case "voice" -> sender.sendVoiceNote(chat.id, path, caption);
            default -> throw new IllegalArgumentException(type);
        }
        System.out.println("Sent.");
    }

    private void sendTextByUserId() throws TelegramException {
        TdApi.Chat chat = askChatByUserId();
        sender.sendText(chat.id, prompt.askRaw("Message: "));
        System.out.println("Sent.");
    }

    private void sendTextByPhoneNumber() throws TelegramException {
        TdApi.Chat chat = askChatByPhoneNumber();
        sender.sendText(chat.id, prompt.askRaw("Message: "));
        System.out.println("Sent.");
    }

    // ---------- auto-reply ----------

    private void toggleAutoReply() {
        if (autoResponder == null) {
            System.out.println("Set the GROQ_API_KEY environment variable to use auto-reply.");
            return;
        }
        autoResponder.setEnabled(!autoResponder.isEnabled());
        System.out.println("Auto-reply is now " + (autoResponder.isEnabled() ? "ON" : "OFF"));
    }

    private void toggleAiVerbose() {
        if (autoResponder == null) {
            System.out.println("Set the GROQ_API_KEY environment variable to use auto-reply.");
            return;
        }
        boolean next = !autoResponder.isVerbose();
        autoResponder.setVerbose(next);
        if (autoResponder.getAi() instanceof OpenAiResponder openAi) {
            openAi.setVerbose(next);
        }
        System.out.println("AI verbose logging is now " + (next ? "ON" : "OFF"));
        if (next) {
            System.out.println("Watch for lines starting with [ai-verbose] to verify the responder.");
            System.out.println("Send a test message from an allowed private chat to see the full flow.");
        }
    }

    /**
     * Adds one or more users to the allow-list. Accepts a comma- and/or
     * space-separated list where each token is a username (@optional),
     * a numeric user id, or a phone number starting with '+'.
     * Type 'clear' to allow every private chat again.
     */
    private void addAutoReplyUsers() {
        if (autoResponder == null) {
            System.out.println("Set the GROQ_API_KEY environment variable to use auto-reply.");
            return;
        }
        String raw = prompt.ask("Usernames/IDs/phones (comma-separated, or 'clear'): ");
        if (raw.equalsIgnoreCase("clear")) {
            autoResponder.clearAllowedChats();
            System.out.println("Allow-list cleared: auto-reply will answer every private chat.");
            return;
        }
        String[] tokens = raw.split("[,\\s]+");
        int ok = 0;
        for (String token : tokens) {
            if (token.isBlank()) continue;
            try {
                TdApi.Chat chat;
                String label;
                if (token.startsWith("+")) {
                    chat = telegram.findChatByPhoneNumber(token);
                    label = token;
                } else if (token.matches("\\d+")) {
                    chat = telegram.findChatByUserId(Long.parseLong(token));
                    label = "id:" + token;
                } else {
                    String username = token.startsWith("@") ? token.substring(1) : token;
                    chat = telegram.findPublicChat(username);
                    label = "@" + username;
                }
                autoResponder.allowChat(chat.id, label);
                System.out.println("Added " + label + " -> " + chat.title + " (chat " + chat.id + ")");
                ok++;
            } catch (TelegramException e) {
                System.err.println("Could not add '" + token + "': " + e.getMessage());
            } catch (NumberFormatException e) {
                System.err.println("Could not add '" + token + "': not a valid user id.");
            }
        }
        if (ok > 0) {
            System.out.println("Auto-reply will now only answer " + autoResponder.getAllowedChats().size()
                    + " allowed chat(s). Use option 10 to review them.");
        } else {
            System.out.println("No users added.");
        }
    }

    /** Prints the current AI auto-reply configuration, including allowed usernames. */
    private void showAutoReplySettings() {
        if (autoResponder == null) {
            System.out.println("Auto-reply: unavailable (GROQ_API_KEY not set).");
            return;
        }
        System.out.println();
        System.out.println("---- Auto-reply settings ----");
        System.out.println("Enabled:  " + (autoResponder.isEnabled() ? "ON" : "OFF"));
        System.out.println("Verbose:  " + (autoResponder.isVerbose() ? "ON" : "OFF"));
        System.out.println("Cooldown: " + autoResponder.getCooldownMillis() + " ms per chat");

        if (autoResponder.getAi() instanceof OpenAiResponder openAi) {
            System.out.println("Backend:  OpenAI-compatible");
            System.out.println("Model:    " + openAi.getModel());
            System.out.println("Base URL: " + openAi.getBaseUrl());
        } else if (autoResponder.getAi() != null) {
            System.out.println("Backend:  " + autoResponder.getAi().getClass().getSimpleName());
        }

        var allowed = autoResponder.getAllowedChats();
        Map<Long, String> labels = autoResponder.getAllowedLabels();
        if (allowed.isEmpty()) {
            System.out.println("Allow-list: (empty) answers EVERY private chat");
        } else {
            System.out.println("Allow-list: " + allowed.size() + " chat(s), only these are answered:");
            for (long chatId : allowed) {
                System.out.println("  - " + describeAllowedChat(chatId, labels.get(chatId)));
            }
        }
        System.out.println("-----------------------------");
    }

    /** Lets the user pick a working model without restarting (fixes 404 model_not_found). */
    private void changeAiModel() {
        if (autoResponder == null) {
            System.out.println("Set the GROQ_API_KEY environment variable to use auto-reply.");
            return;
        }
        if (!(autoResponder.getAi() instanceof OpenAiResponder openAi)) {
            System.out.println("AI backend is not OpenAI-compatible, cannot change model.");
            return;
        }
        System.out.println("Current model: " + openAi.getModel());
        try {
            var ids = openAi.listModels();
            System.out.println("Models available to your key (" + ids.size() + "):");
            for (String id : ids) System.out.println("  - " + id);
        } catch (Exception e) {
            System.err.println("Could not list models: " + e.getMessage());
            System.out.println("Known-good Groq IDs: openai/gpt-oss-20b, openai/gpt-oss-120b, qwen/qwen3.8-27b");
            System.out.println("(llama-3.3-70b-versatile is Enterprise-only now and 404s on free keys.)");
        }
        String next = prompt.ask("New model id (empty to cancel): ");
        if (next.isBlank()) {
            System.out.println("Cancelled.");
            return;
        }
        openAi.setModel(next);
        System.out.println("Model is now: " + openAi.getModel());
    }

    private String describeAllowedChat(long chatId, String label) {        StringBuilder sb = new StringBuilder();
        sb.append("chat ").append(chatId);
        if (label != null) sb.append(" [").append(label).append("]");
        try {
            TdApi.Chat chat = telegram.getChat(chatId);
            sb.append(" title=\"").append(chat.title).append("\"");
            if (chat.type instanceof TdApi.ChatTypePrivate priv) {
                sb.append(" userId=").append(priv.userId);
                try {
                    TdApi.User user = telegram.getUser(priv.userId);
                    String uname = user.usernames != null && user.usernames.activeUsernames.length > 0
                            ? "@" + String.join(", @", user.usernames.activeUsernames) : "(no username)";
                    sb.append(" ").append(uname);
                    sb.append(" name=\"").append((user.firstName + " " + user.lastName).trim()).append("\"");
                    if (user.phoneNumber != null && !user.phoneNumber.isBlank()) {
                        sb.append(" phone=+").append(user.phoneNumber.replaceFirst("^\\+?", ""));
                    }
                } catch (TelegramException e) {
                    sb.append(" (user lookup failed: ").append(e.getMessage()).append(")");
                }
            }
        } catch (TelegramException e) {
            sb.append(" (chat lookup failed: ").append(e.getMessage()).append(")");
        }
        return sb.toString();
    }
}
