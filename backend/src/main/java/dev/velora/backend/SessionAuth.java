package dev.velora.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proof of account ownership without receiving any Microsoft/Minecraft token:
 * <ol>
 *   <li>client requests a one-time {@code serverId} (challenge)</li>
 *   <li>the game calls Mojang's sessionserver {@code join} with that id (exactly like joining a server)</li>
 *   <li>the backend asks {@code sessionserver.mojang.com/session/minecraft/hasJoined} and issues a session token</li>
 * </ol>
 */
public final class SessionAuth {
    private static final long CHALLENGE_TTL = 120_000;
    private static final long SESSION_TTL = 24 * 3600_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Challenge(String username, long expires) { }

    public record Session(String uuid, String username, long expires) { }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public String challenge(String username) {
        cleanup();
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        String serverId = HexFormat.of().formatHex(b);
        challenges.put(serverId, new Challenge(username, System.currentTimeMillis() + CHALLENGE_TTL));
        return serverId;
    }

    public Optional<String> verify(String username, String serverId) throws IOException, InterruptedException {
        Challenge c = challenges.remove(serverId);
        if (c == null || c.expires < System.currentTimeMillis() || !c.username.equalsIgnoreCase(username)) return Optional.empty();
        String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 || resp.body().isBlank()) return Optional.empty();
        JsonObject profile = JsonParser.parseString(resp.body()).getAsJsonObject();
        String uuid = DataStore.normalizeUuid(profile.get("id").getAsString());
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);
        sessions.put(token, new Session(uuid, profile.get("name").getAsString(), System.currentTimeMillis() + SESSION_TTL));
        return Optional.of(token);
    }

    public Optional<Session> session(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return Optional.empty();
        Session s = sessions.get(authorizationHeader.substring(7).trim());
        if (s == null || s.expires < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(s);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(c -> c.expires < now);
        sessions.values().removeIf(s -> s.expires < now);
    }
}
