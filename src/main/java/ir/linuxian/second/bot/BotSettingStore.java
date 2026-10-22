package ir.linuxian.second.bot;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted Bot Manager settings. Values saved by the admin dashboard take
 * precedence over {@link TelegramBotProperties} (which come from
 * {@code application.properties}); the AI key additionally falls back to the
 * {@code GROQ_API_KEY} environment variable, mirroring standalone behaviour.
 */
@Service
public class BotSettingStore {

    public static final String ENABLED = "enabled";
    public static final String API_ID = "apiId";
    public static final String API_HASH = "apiHash";
    public static final String DATABASE_DIRECTORY = "databaseDirectory";
    public static final String VERBOSE = "verbose";
    public static final String AI_API_KEY = "aiApiKey";
    public static final String AI_MODEL = "aiModel";
    public static final String AI_BASE_URL = "aiBaseUrl";
    public static final String AI_SYSTEM_PROMPT = "aiSystemPrompt";
    public static final String AUTO_REPLY_ENABLED = "autoReplyEnabled";
    public static final String AI_VERBOSE = "aiVerbose";
    public static final String COOLDOWN_MILLIS = "cooldownMillis";
    public static final String ALLOWED_CHATS = "allowedChats";

    private static final List<String> KNOWN_KEYS = List.of(
            ENABLED, API_ID, API_HASH, DATABASE_DIRECTORY, VERBOSE,
            AI_API_KEY, AI_MODEL, AI_BASE_URL, AI_SYSTEM_PROMPT,
            AUTO_REPLY_ENABLED, AI_VERBOSE, COOLDOWN_MILLIS);

    private final BotSettingRepo repo;
    private final Gson gson = new Gson();

    public BotSettingStore(BotSettingRepo repo) {
        this.repo = repo;
    }

    /** Merged view: persisted admin values over application.properties. */
    public record EffectiveSettings(
            boolean enabled,
            int apiId,
            String apiHash,
            String databaseDirectory,
            boolean verbose,
            String aiApiKey,
            boolean aiApiKeySet,
            String aiModel,
            String aiBaseUrl,
            String aiSystemPrompt,
            boolean autoReplyEnabled,
            boolean aiVerbose,
            long cooldownMillis) {
    }

    public record AllowedChat(long chatId, String label) {
    }

    public Map<String, String> getAll() {
        Map<String, String> out = new HashMap<>();
        repo.findAll().forEach(s -> out.put(s.getKey(), s.getValue()));
        return out;
    }

    /** Persists the given keys (unknown keys ignored; blank clears back to default). */
    public synchronized void saveAll(Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!KNOWN_KEYS.contains(e.getKey())) {
                continue;
            }
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (v.isEmpty()) {
                repo.deleteById(e.getKey());
            } else {
                repo.save(new BotSetting(e.getKey(), e.getValue()));
            }
        }
    }

    public EffectiveSettings effective(TelegramBotProperties props) {
        Map<String, String> saved = getAll();
        boolean enabled = bool(saved.get(ENABLED), props.isEnabled());
        int apiId = integer(saved.get(API_ID), props.getApiId());
        String apiHash = str(saved.get(API_HASH), props.getApiHash());
        String dbDir = str(saved.get(DATABASE_DIRECTORY), props.getDatabaseDirectory());
        boolean verbose = bool(saved.get(VERBOSE), props.isVerbose());

        String aiKey = saved.get(AI_API_KEY);
        if (aiKey == null || aiKey.isBlank()) {
            aiKey = props.getAiApiKey();
        }
        if (aiKey == null || aiKey.isBlank()) {
            aiKey = System.getenv("GROQ_API_KEY");
        }
        if (aiKey == null) {
            aiKey = "";
        }
        String aiModel = str(saved.get(AI_MODEL), props.getAiModel());
        String aiBaseUrl = str(saved.get(AI_BASE_URL), props.getAiBaseUrl());
        String aiPrompt = str(saved.get(AI_SYSTEM_PROMPT), props.getAiSystemPrompt());
        boolean autoReply = bool(saved.get(AUTO_REPLY_ENABLED), props.isAutoReplyEnabled());
        boolean aiVerbose = bool(saved.get(AI_VERBOSE), props.isAiVerbose());
        long cooldown = integer(saved.get(COOLDOWN_MILLIS), (int) props.getCooldownMillis());

        return new EffectiveSettings(enabled, apiId, apiHash == null ? "" : apiHash,
                dbDir == null ? "" : dbDir, verbose, aiKey, !aiKey.isBlank(),
                aiModel, aiBaseUrl, aiPrompt, autoReply, aiVerbose, cooldown);
    }

    /** Effective settings as an editable map for the dashboard form. */
    public Map<String, Object> asMap(TelegramBotProperties props) {
        EffectiveSettings e = effective(props);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(ENABLED, e.enabled());
        m.put(API_ID, e.apiId());
        m.put(API_HASH, e.apiHash());
        m.put(DATABASE_DIRECTORY, e.databaseDirectory());
        m.put(VERBOSE, e.verbose());
        m.put(AI_API_KEY, e.aiApiKey());
        m.put("aiApiKeySet", e.aiApiKeySet());
        m.put(AI_MODEL, e.aiModel());
        m.put(AI_BASE_URL, e.aiBaseUrl());
        m.put(AI_SYSTEM_PROMPT, e.aiSystemPrompt());
        m.put(AUTO_REPLY_ENABLED, e.autoReplyEnabled());
        m.put(AI_VERBOSE, e.aiVerbose());
        m.put(COOLDOWN_MILLIS, e.cooldownMillis());
        return m;
    }

    public synchronized List<AllowedChat> getAllowedChats() {
        String raw = getAll().get(ALLOWED_CHATS);
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<AllowedChat>>() {
            }.getType();
            List<AllowedChat> list = gson.fromJson(raw, type);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized void saveAllowedChats(List<AllowedChat> chats) {
        repo.save(new BotSetting(ALLOWED_CHATS, gson.toJson(chats)));
    }

    public synchronized void clearAllowedChats() {
        repo.deleteById(ALLOWED_CHATS);
    }

    private static String str(String saved, String fallback) {
        return saved == null || saved.isBlank() ? fallback : saved;
    }

    private static boolean bool(String saved, boolean fallback) {
        if (saved == null || saved.isBlank()) {
            return fallback;
        }
        return saved.equalsIgnoreCase("true") || saved.equals("1");
    }

    private static int integer(String saved, int fallback) {
        if (saved == null || saved.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(saved.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
