package dev.velora.core.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Asynchronous client for the optional Velora backend. Every method returns immediately;
 * failures complete the future exceptionally and never block the game thread.
 * <p>
 * Authentication uses the Mojang session server: the backend issues a random {@code serverId},
 * the game calls {@code joinServer} with it (exactly like joining an online-mode server) and the
 * backend verifies via {@code hasJoined}. No Microsoft/Minecraft token is ever sent to the backend.
 */
public final class BackendClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final String baseUrl;
    private final String userAgent;
    private final HttpClient http;

    public BackendClient(String baseUrl, String userAgent) {
        this.baseUrl = normalize(baseUrl);
        this.userAgent = userAgent;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "";
        return u;
    }

    public boolean isEnabled() { return !baseUrl.isEmpty(); }

    public String getBaseUrl() { return baseUrl; }

    // ------------------------------------------------------------------ public API

    public CompletableFuture<List<NewsItem>> fetchNews() {
        return getJson("/api/news").thenApply(json -> {
            List<NewsItem> items = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                items.add(new NewsItem(str(o, "title"), str(o, "body"), str(o, "date"), str(o, "url")));
            }
            return items;
        });
    }

    public CompletableFuture<Optional<UpdateInfo>> fetchLatestUpdate(String component) {
        return getJson("/api/updates/latest?component=" + enc(component)).thenApply(json -> {
            if (!json.has("version")) return Optional.<UpdateInfo>empty();
            return Optional.of(new UpdateInfo(str(json, "component"), str(json, "version"), str(json, "url"),
                    str(json, "sha256"), str(json, "changelog")));
        });
    }

    /** Step 1 of the login: ask the backend for a one-time server id. */
    public CompletableFuture<String> requestChallenge(String username) {
        return getJson("/api/auth/challenge?username=" + enc(username)).thenApply(json -> str(json, "serverId"));
    }

    /** Step 3 of the login (after the game called sessionserver join): returns a backend session token. */
    public CompletableFuture<String> verify(String username, String serverId) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("serverId", serverId);
        return postJson("/api/auth/verify", body, null).thenApply(json -> str(json, "token"));
    }

    public CompletableFuture<Set<String>> fetchOwnedCosmetics(UUID player) {
        return getJson("/api/cosmetics/owned/" + player).thenApply(json -> {
            Set<String> ids = new HashSet<>();
            for (JsonElement e : json.getAsJsonArray("owned")) ids.add(e.getAsString());
            return ids;
        });
    }

    /** Equipped cosmetics (type name → cosmetic id) for several players in one request. */
    public CompletableFuture<Map<UUID, Map<String, String>>> fetchEquipped(Collection<UUID> players) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        players.forEach(p -> arr.add(p.toString()));
        body.add("players", arr);
        return postJson("/api/cosmetics/equipped", body, null).thenApply(json -> {
            Map<UUID, Map<String, String>> result = new HashMap<>();
            JsonObject map = json.getAsJsonObject("equipped");
            for (String key : map.keySet()) {
                Map<String, String> equipped = new HashMap<>();
                JsonObject entry = map.getAsJsonObject(key);
                for (String type : entry.keySet()) equipped.put(type, entry.get(type).getAsString());
                result.put(UUID.fromString(key), equipped);
            }
            return result;
        });
    }

    public CompletableFuture<Void> equip(String token, String type, String cosmeticId) {
        JsonObject body = new JsonObject();
        body.addProperty("type", type);
        body.addProperty("id", cosmeticId == null ? "" : cosmeticId);
        return postJson("/api/cosmetics/equip", body, token).thenApply(j -> null);
    }

    public CompletableFuture<Optional<JsonObject>> downloadConfig(String token) {
        return send(HttpRequest.newBuilder(uri("/api/config")).header("Authorization", "Bearer " + token).GET())
                .thenApply(json -> json.has("config") && json.get("config").isJsonObject()
                        ? Optional.of(json.getAsJsonObject("config")) : Optional.<JsonObject>empty());
    }

    public CompletableFuture<Void> uploadConfig(String token, JsonObject config) {
        JsonObject body = new JsonObject();
        body.add("config", config);
        return postJson("/api/config", body, token).thenApply(j -> null);
    }

    /** Anonymous, opt-in launch statistic. Contains only versions, no identifiers. */
    public CompletableFuture<Void> reportLaunch(String clientVersion, String minecraftVersion) {
        JsonObject body = new JsonObject();
        body.addProperty("client", clientVersion);
        body.addProperty("minecraft", minecraftVersion);
        return postJson("/api/stats/launch", body, null).thenApply(j -> null);
    }

    // ------------------------------------------------------------------ transport

    private CompletableFuture<JsonObject> getJson(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private CompletableFuture<JsonObject> postJson(String path, JsonObject body, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return send(b);
    }

    private URI uri(String path) {
        if (!isEnabled()) throw new IllegalStateException("Backend disabled");
        return URI.create(baseUrl + path);
    }

    private CompletableFuture<JsonObject> send(HttpRequest.Builder builder) {
        if (!isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Backend disabled"));
        HttpRequest request = builder.timeout(TIMEOUT).header("User-Agent", userAgent).header("Accept", "application/json").build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
            if (resp.statusCode() / 100 != 2) {
                throw new CompletionException(new BackendException(resp.statusCode(), resp.body()));
            }
            String text = resp.body();
            if (text == null || text.isBlank()) return new JsonObject();
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        });
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    public static final class BackendException extends RuntimeException {
        private final int status;

        public BackendException(int status, String body) {
            super("Backend returned HTTP " + status + (body == null || body.isBlank() ? "" : ": " + truncate(body)));
            this.status = status;
        }

        public int getStatus() { return status; }

        private static String truncate(String s) { return s.length() > 200 ? s.substring(0, 200) + "…" : s; }
    }
}
