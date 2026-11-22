package dev.velora.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.velora.core.cosmetics.Cosmetic;
import dev.velora.core.cosmetics.CosmeticCatalog;
import dev.velora.core.cosmetics.CosmeticType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Optional Velora backend (JDK HttpServer, no framework). Start:
 * {@code java -jar velora-backend.jar [port] [dataDir]} – default port 8787, data dir ./data.
 * Grant a cosmetic: {@code java -jar velora-backend.jar grant <uuid> <cosmeticId> [dataDir]}.
 */
public final class BackendServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Backend");
    private static final int MAX_BODY = 256 * 1024;
    private static final int RATE_LIMIT_PER_MINUTE = 120;

    private final DataStore store;
    private final SessionAuth auth = new SessionAuth();
    private final Set<String> freeCosmetics = CosmeticCatalog.all().stream().filter(Cosmetic::free).map(Cosmetic::id).collect(Collectors.toSet());
    private final Map<String, int[]> rate = new ConcurrentHashMap<>();
    private volatile long rateWindow = System.currentTimeMillis() / 60_000;

    public BackendServer(DataStore store) {
        this.store = store;
    }

    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && args[0].equals("grant")) {
            DataStore store = new DataStore(Path.of(args.length >= 4 ? args[3] : "data"));
            String uuid = DataStore.normalizeUuid(args[1]);
            if (CosmeticCatalog.byId(args[2]).isEmpty()) {
                System.err.println("Unbekanntes Cosmetic: " + args[2]);
                System.exit(1);
            }
            System.out.println(store.grant(uuid, args[2]) ? "Freigeschaltet: " + args[2] + " für " + uuid : "Bereits freigeschaltet");
            return;
        }
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 8787;
        Path data = Path.of(args.length >= 2 ? args[1] : "data");
        new BackendServer(new DataStore(data)).start(port);
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", this::handle);
        server.start();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(store::flushStats, 1, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(store::flushStats));
        LOGGER.info("Velora backend listening on port {}", port);
        return server;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            if (!allow(ip)) {
                send(ex, 429, error("Zu viele Anfragen"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            JsonObject response = route(ex, method, path);
            if (response == null) send(ex, 404, error("Nicht gefunden"));
            else send(ex, response.has("_status") ? response.remove("_status").getAsInt() : 200, response);
        } catch (IllegalArgumentException e) {
            send(ex, 400, error(e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Request failed", e);
            send(ex, 500, error("Interner Fehler"));
        } finally {
            ex.close();
        }
    }

    private JsonObject route(HttpExchange ex, String method, String path) throws Exception {
        Map<String, String> q = query(ex);
        if (method.equals("GET") && path.equals("/api/health")) {
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            return o;
        }
        if (method.equals("GET") && path.equals("/api/news")) {
            JsonObject news = store.news();
            if (!news.has("items")) news.add("items", new JsonArray());
            return news;
        }
        if (method.equals("GET") && path.equals("/api/updates/latest")) {
            return store.update(required(q, "component"));
        }
        if (method.equals("GET") && path.equals("/api/auth/challenge")) {
            String username = required(q, "username");
            if (!username.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Ungültiger Name");
            JsonObject o = new JsonObject();
            o.addProperty("serverId", auth.challenge(username));
            return o;
        }
        if (method.equals("POST") && path.equals("/api/auth/verify")) {
            JsonObject body = body(ex);
            Optional<String> token = auth.verify(str(body, "username"), str(body, "serverId"));
            if (token.isEmpty()) return status(401, error("Verifizierung fehlgeschlagen"));
            JsonObject o = new JsonObject();
            o.addProperty("token", token.get());
            return o;
        }
        if (method.equals("GET") && path.startsWith("/api/cosmetics/owned/")) {
            String uuid = DataStore.normalizeUuid(path.substring("/api/cosmetics/owned/".length()));
            JsonObject o = new JsonObject();
            o.add("owned", store.owned(uuid));
            return o;
        }
        if (method.equals("POST") && path.equals("/api/cosmetics/equipped")) {
            JsonArray players = body(ex).getAsJsonArray("players");
            if (players.size() > 200) throw new IllegalArgumentException("Zu viele Spieler");
            JsonObject map = new JsonObject();
            for (JsonElement p : players) {
                String uuid = DataStore.normalizeUuid(p.getAsString());
                JsonObject eq = store.equipped(uuid);
                if (!eq.isEmpty()) map.add(uuid, eq);
            }
            JsonObject o = new JsonObject();
            o.add("equipped", map);
            return o;
        }
        if (method.equals("POST") && path.equals("/api/cosmetics/equip")) {
            var session = auth.session(ex.getRequestHeaders().getFirst("Authorization"));
            if (session.isEmpty()) return status(401, error("Nicht angemeldet"));
            JsonObject body = body(ex);
            String type = CosmeticType.valueOf(str(body, "type")).name();
            String id = str(body, "id");
            if (!id.isEmpty()) {
                Cosmetic c = CosmeticCatalog.byId(id).orElseThrow(() -> new IllegalArgumentException("Unbekanntes Cosmetic"));
                if (!c.type().name().equals(type)) throw new IllegalArgumentException("Falscher Typ");
                if (!store.ownsOrFree(session.get().uuid(), id, freeCosmetics)) return status(403, error("Nicht freigeschaltet"));
            }
            store.equip(session.get().uuid(), type, id);
            return new JsonObject();
        }
        if (path.equals("/api/config")) {
            var session = auth.session(ex.getRequestHeaders().getFirst("Authorization"));
            if (session.isEmpty()) return status(401, error("Nicht angemeldet"));
            if (method.equals("GET")) {
                JsonObject o = new JsonObject();
                JsonObject cfg = store.loadConfig(session.get().uuid());
                if (cfg != null) o.add("config", cfg);
                return o;
            }
            if (method.equals("POST")) {
                JsonObject body = body(ex);
                if (!body.has("config") || !body.get("config").isJsonObject()) throw new IllegalArgumentException("config fehlt");
                store.saveConfig(session.get().uuid(), body.getAsJsonObject("config"));
                return new JsonObject();
            }
        }
        if (method.equals("POST") && path.equals("/api/stats/launch")) {
            JsonObject body = body(ex);
            store.countLaunch(str(body, "client"), str(body, "minecraft"));
            return new JsonObject();
        }
        if (method.equals("GET") && path.equals("/api/stats")) {
            return store.statsSnapshot();
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private boolean allow(String ip) {
        long window = System.currentTimeMillis() / 60_000;
        if (window != rateWindow) {
            rate.clear();
            rateWindow = window;
        }
        int[] count = rate.computeIfAbsent(ip, k -> new int[1]);
        synchronized (count) {
            return ++count[0] <= RATE_LIMIT_PER_MINUTE;
        }
    }

    private static JsonObject body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) throw new IllegalArgumentException("Anfrage zu groß");
            JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) throw new IllegalArgumentException("JSON-Objekt erwartet");
            return el.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new IllegalArgumentException("Ungültiges JSON");
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return map;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            map.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String required(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Parameter fehlt: " + key);
        return v;
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) throw new IllegalArgumentException("Feld fehlt: " + key);
        return o.get(key).getAsString();
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static JsonObject status(int code, JsonObject body) {
        body.addProperty("_status", code);
        return body;
    }

    private static void send(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
