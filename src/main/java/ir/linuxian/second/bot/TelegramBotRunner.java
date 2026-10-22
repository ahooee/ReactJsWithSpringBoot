package ir.linuxian.second.bot;

import ir.linuxian.ai.OpenAiResponder;
import ir.linuxian.app.ConsolePrompt;
import ir.linuxian.listener.AutoResponder;
import ir.linuxian.listener.IncomingMessagePrinter;
import ir.linuxian.telegram.MessageReader;
import ir.linuxian.telegram.MessageSender;
import ir.linuxian.telegram.SendTracker;
import ir.linuxian.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Starts the TDchatBot Telegram client in a background daemon thread when the
 * Spring Boot app starts, so {@code ./gradlew bootRun} (or {@code java -jar})
 * runs both the web app and the chat bot.
 *
 * <p>Headless equivalent of TDchatBot's {@code Main}: no interactive
 * {@code ChatApp} menu loop. Incoming messages are still printed and (when an
 * AI key is configured) auto-answered. Login prompts (phone/code/2FA) go to
 * the console, same as standalone; when the shared {@code tdlib-data} session
 * is reused no prompt appears.</p>
 *
 * <p>Connection/AI settings come from {@link BotSettingStore} (admin dashboard
 * values persisted in the DB, falling back to {@code application.properties}).
 * AI settings apply live; connection settings (api id/hash, session dir,
 * enabled) take effect on {@link #restart()}.</p>
 */
@Component
public class TelegramBotRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRunner.class);

    private final TelegramBotProperties props;
    private final BotSettingStore store;

    private volatile boolean running = false;
    private volatile boolean loggedIn = false;
    private Thread botThread;
    private volatile TelegramService telegram;
    private volatile MessageReader reader;
    private volatile MessageSender sender;
    private volatile SendTracker tracker;
    private volatile AutoResponder autoResponder;

    // Connection values the live thread was started with (for change detection).
    private volatile int currentApiId;
    private volatile String currentApiHash = "";
    private volatile String currentDbDir = "";
    private volatile boolean currentVerbose;

    public TelegramBotRunner(TelegramBotProperties props, BotSettingStore store) {
        this.props = props;
        this.store = store;
    }

    @Override
    public void start() {
        if (!store.effective(props).enabled()) {
            log.info("Telegram bot disabled, skipping (enable via admin Bot Manager or telegram.bot.enabled=true).");
            return;
        }
        if (running) {
            return;
        }
        running = true;
        loggedIn = false;
        botThread = new Thread(this::runBot, "telegram-bot");
        botThread.setDaemon(true);
        botThread.start();
        log.info("Telegram bot thread started.");
    }

    private void runBot() {
        try {
            BotSettingStore.EffectiveSettings eff = store.effective(props);
            String dbDir = resolveDatabaseDirectory(eff.databaseDirectory());
            currentApiId = eff.apiId();
            currentApiHash = eff.apiHash();
            currentDbDir = dbDir;
            currentVerbose = eff.verbose();
            log.info("Telegram bot starting (apiId={}, dbDir={}).", eff.apiId(), dbDir);

            ConsolePrompt prompt = new ConsolePrompt();
            TelegramService service = new TelegramService(eff.apiId(), eff.apiHash(), prompt);
            service.setDatabaseDirectory(dbDir);

            MessageReader messageReader = new MessageReader(service);
            SendTracker sendTracker = new SendTracker(service, messageReader, eff.verbose());
            service.addUpdateListener(sendTracker::onUpdate);
            MessageSender messageSender = new MessageSender(service, sendTracker);

            service.addMessageListener(new IncomingMessagePrinter(service, messageReader));

            this.telegram = service;
            this.reader = messageReader;
            this.sender = messageSender;
            this.tracker = sendTracker;
            this.autoResponder = null;

            ensureAutoResponder(eff);
            for (BotSettingStore.AllowedChat c : store.getAllowedChats()) {
                if (autoResponder != null) {
                    autoResponder.allowChat(c.chatId(), c.label());
                }
            }

            // Blocks until login completes (console prompts if needed), then returns.
            // Listeners keep working on TDLib threads afterwards.
            service.start();
            loggedIn = true;
            log.info("Telegram bot logged in and running.");

            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Throwable t) {
            // Never take the whole Spring app down because the bot failed
            // (missing native lib, bad credentials, ...). Log and stay down.
            log.error("Telegram bot failed and will stay stopped: {}", t.getMessage(), t);
        } finally {
            loggedIn = false;
            closeQuietly();
        }
    }

    /**
     * Creates the AI auto-responder if an AI key is configured, or updates the
     * live one. Called at startup and by the admin panel (no restart needed).
     */
    public synchronized void ensureAutoResponder(BotSettingStore.EffectiveSettings eff) {
        TelegramService service = telegram;
        if (service == null || reader == null || sender == null) {
            return;
        }
        if (eff.aiApiKey() == null || eff.aiApiKey().isBlank()) {
            log.warn("AI auto-reply disabled: no AI key configured.");
            if (autoResponder != null) {
                autoResponder.setEnabled(false);
            }
            return;
        }
        if (autoResponder == null) {
            OpenAiResponder ai = new OpenAiResponder(
                    eff.aiApiKey(), eff.aiModel(), eff.aiBaseUrl(), eff.aiSystemPrompt());
            ai.setVerbose(eff.aiVerbose());
            AutoResponder responder = new AutoResponder(
                    service, reader, sender, ai, eff.cooldownMillis());
            responder.setEnabled(eff.autoReplyEnabled());
            responder.setVerbose(eff.aiVerbose());
            for (BotSettingStore.AllowedChat c : store.getAllowedChats()) {
                responder.allowChat(c.chatId(), c.label());
            }
            service.addMessageListener(responder);
            autoResponder = responder;
            log.info("Telegram AI auto-reply created (enabled={}).", responder.isEnabled());
        } else {
            if (autoResponder.getAi() instanceof OpenAiResponder openAi) {
                openAi.setApiKey(eff.aiApiKey());
                openAi.setModel(eff.aiModel());
                openAi.setBaseUrl(eff.aiBaseUrl());
                openAi.setSystemPrompt(eff.aiSystemPrompt());
                openAi.setVerbose(eff.aiVerbose());
            } else {
                OpenAiResponder ai = new OpenAiResponder(
                        eff.aiApiKey(), eff.aiModel(), eff.aiBaseUrl(), eff.aiSystemPrompt());
                ai.setVerbose(eff.aiVerbose());
                autoResponder.setAi(ai);
            }
            autoResponder.setEnabled(eff.autoReplyEnabled());
            autoResponder.setVerbose(eff.aiVerbose());
            autoResponder.setCooldownMillis(eff.cooldownMillis());
            log.info("Telegram AI settings applied live (enabled={}).", autoResponder.isEnabled());
        }
    }

    public void ensureAutoResponder() {
        ensureAutoResponder(store.effective(props));
    }

    public void setTrackerVerbose(boolean verbose) {
        if (tracker != null) {
            tracker.setVerbose(verbose);
        }
        currentVerbose = verbose;
    }

    /** Restarts the bot so connection settings (api id/hash, session dir) apply. */
    public synchronized void restart() {
        log.info("Restarting Telegram bot.");
        stop();
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        start();
    }

    /**
     * Picks the TDLib session dir. An explicit setting always wins; otherwise
     * the embedded {@code tdlib-data} next to second is used. The old
     * {@code ../TDchatBot/tdlib-data} locations are kept as fallbacks so a
     * session from before the embed is still found and reused.
     */
    private String resolveDatabaseDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String[] candidates = {
                "tdlib-data",
                "../TDchatBot/tdlib-data",
                "TDchatBot/tdlib-data",
        };
        for (String candidate : candidates) {
            if (new File(candidate).isDirectory()) {
                return new File(candidate).getAbsoluteFile().getAbsolutePath();
            }
        }
        return new File("tdlib-data").getAbsolutePath();
    }

    private void closeQuietly() {
        TelegramService service = telegram;
        telegram = null;
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                log.warn("Error closing TelegramService: {}", e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        loggedIn = false;
        closeQuietly();
        Thread t = botThread;
        if (t != null && t.isAlive()) {
            t.interrupt();
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running && botThread != null && botThread.isAlive();
    }

    @Override
    public boolean isAutoStartup() {
        try {
            return store.effective(props).enabled();
        } catch (Exception e) {
            return props.isEnabled();
        }
    }

    /** Start late (after the web layer), so the bot logs after Spring is up. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    /** Live client once started; null before start or after failure. */
    public TelegramService getTelegram() {
        return telegram;
    }

    public MessageReader getReader() {
        return reader;
    }

    public MessageSender getSender() {
        return sender;
    }

    public SendTracker getTracker() {
        return tracker;
    }

    public AutoResponder getAutoResponder() {
        return autoResponder;
    }

    public boolean isLoggedIn() {
        return loggedIn && telegram != null;
    }

    public boolean isBotEnabled() {
        try {
            return store.effective(props).enabled();
        } catch (Exception e) {
            return props.isEnabled();
        }
    }

    public int getCurrentApiId() {
        return currentApiId;
    }

    public String getCurrentApiHash() {
        return currentApiHash;
    }

    public String getCurrentDbDir() {
        return currentDbDir;
    }

    public TelegramBotProperties getProps() {
        return props;
    }

    public BotSettingStore getStore() {
        return store;
    }
}
