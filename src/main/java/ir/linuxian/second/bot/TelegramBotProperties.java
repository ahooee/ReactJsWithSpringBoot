package ir.linuxian.second.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the embedded TDchatBot Telegram client.
 *
 * <p>All values can be set in {@code application.properties} under
 * {@code telegram.bot.*}. The AI key defaults to the {@code GROQ_API_KEY}
 * environment variable, mirroring the standalone TDchatBot behaviour.</p>
 */
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    /** Master switch. When false the bot thread is never started. */
    private boolean enabled = true;

    /** Telegram api_id (defaults to the value used by standalone Main). */
    private int apiId = 1006772;

    /** Telegram api_hash (defaults to the value used by standalone Main). */
    private String apiHash = "8e49b4e91bf667471bd0d0958897eed3";

    /**
     * TDLib session directory. When blank the runner auto-detects the embedded
     * {@code tdlib-data} session so login is not required again.
     */
    private String databaseDirectory = "";

    /** Verbose send-tracker logging (as in standalone Main: true). */
    private boolean verbose = true;

    /** AI API key. Blank means "fall back to GROQ_API_KEY env var". */
    private String aiApiKey = "";

    private String aiModel = "openai/gpt-oss-20b";
    private String aiBaseUrl = "https://api.groq.com/openai/v1";
    private String aiSystemPrompt = "You are an automated assistant replying on behalf of the account owner. "
            + "Be brief and helpful, and mention you are an AI if asked.";

    /** Headless mode has no console menu, so auto-reply starts ON by default. */
    private boolean autoReplyEnabled = true;
    private boolean aiVerbose = false;
    private long cooldownMillis = 3000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getApiId() {
        return apiId;
    }

    public void setApiId(int apiId) {
        this.apiId = apiId;
    }

    public String getApiHash() {
        return apiHash;
    }

    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }

    public String getDatabaseDirectory() {
        return databaseDirectory;
    }

    public void setDatabaseDirectory(String databaseDirectory) {
        this.databaseDirectory = databaseDirectory;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public void setAiApiKey(String aiApiKey) {
        this.aiApiKey = aiApiKey;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl;
    }

    public String getAiSystemPrompt() {
        return aiSystemPrompt;
    }

    public void setAiSystemPrompt(String aiSystemPrompt) {
        this.aiSystemPrompt = aiSystemPrompt;
    }

    public boolean isAutoReplyEnabled() {
        return autoReplyEnabled;
    }

    public void setAutoReplyEnabled(boolean autoReplyEnabled) {
        this.autoReplyEnabled = autoReplyEnabled;
    }

    public boolean isAiVerbose() {
        return aiVerbose;
    }

    public void setAiVerbose(boolean aiVerbose) {
        this.aiVerbose = aiVerbose;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }
}
