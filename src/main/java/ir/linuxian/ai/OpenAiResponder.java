package ir.linuxian.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Works with OpenAI and any OpenAI-compatible endpoint (Chat Completions API). */
public class OpenAiResponder implements AiResponder {
    private static final int MAX_HISTORY = 10; // messages kept per chat

    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final Map<Long, Deque<JsonObject>> history = new HashMap<>();

    private volatile String apiKey;
    private volatile String model;
    private volatile String baseUrl;       // e.g. https://api.openai.com/v1
    private volatile String systemPrompt;
    private volatile boolean verbose = false;

    public OpenAiResponder(String apiKey, String model, String baseUrl, String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.systemPrompt = systemPrompt;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getModel() {
        return model;
    }

    /** Rotates the AI key live (used by the admin panel). Blank values are ignored. */
    public void setApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.apiKey = apiKey.trim();
        }
    }
    public synchronized void setModel(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model.trim();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public synchronized void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            String b = baseUrl.trim();
            this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    /** Applies a new pre-prompt live (used by the admin panel). Null is ignored. */
    public synchronized void setSystemPrompt(String systemPrompt) {
        if (systemPrompt != null) {
            this.systemPrompt = systemPrompt;
        }
    }

    @Override
    public synchronized String reply(long chatId, String userText) throws IOException, InterruptedException {
        Deque<JsonObject> chatHistory = history.computeIfAbsent(chatId, k -> new ArrayDeque<>());
        chatHistory.addLast(message("user", userText));
        trim(chatHistory);

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        chatHistory.forEach(messages::add);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);

        v("POST " + baseUrl + "/chat/completions model=" + model
                + " chat=" + chatId + " history=" + chatHistory.size()
                + " prompt=" + preview(userText));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        long start = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            v("HTTP " + response.statusCode() + " chat=" + chatId + " took=" + tookMs + "ms"
                    + " body=" + preview(response.body()));
            if (response.statusCode() != 200) {
                throw new IOException("AI API error " + response.statusCode() + ": " + response.body());
            }

            String answer = JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();

            chatHistory.addLast(message("assistant", answer));
            trim(chatHistory);
            v("answer chat=" + chatId + " len=" + answer.length() + " preview=" + preview(answer));
            return answer;
        } catch (IOException | InterruptedException | RuntimeException e) {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            v("request failed chat=" + chatId + " took=" + tookMs + "ms: " + e);
            chatHistory.removeLast(); // don't keep the unanswered user message
            throw e;
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    /** Lists model ids from GET {baseUrl}/models (OpenAI-compatible). Used to pick a working model. */
    public java.util.List<String> listModels() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        v("GET " + baseUrl + "/models -> HTTP " + response.statusCode());
        if (response.statusCode() != 200) {
            throw new IOException("List models failed " + response.statusCode() + ": " + response.body());
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (var e : JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonArray("data")) {
            var id = e.getAsJsonObject().get("id");
            if (id != null) ids.add(id.getAsString());
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    private static void trim(Deque<JsonObject> h) {
        while (h.size() > MAX_HISTORY) h.removeFirst();
    }

    private void v(String msg) {
        if (verbose) System.out.println("[ai-verbose] [openai] " + msg);
    }

    private static String preview(String s) {
        if (s == null) return "\"\"";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= 200) return "\"" + oneLine + "\"";
        return "\"" + oneLine.substring(0, 200) + "...\"";
    }
}
