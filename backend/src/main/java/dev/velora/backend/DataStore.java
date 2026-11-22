package dev.velora.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.velora.core.config.JsonFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * File based persistence (JSON). Deliberately simple – swap for a database when needed;
 * all access goes through this class.
 */
public final class DataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Store");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F\\-]{36}");

    private final Path root;
    private JsonObject cosmetics;   // { "owned": {uuid: [ids]}, "equipped": {uuid: {type: id}} }
    private JsonObject stats;       // { "launches": n, "byClient": {...}, "byMinecraft": {...} }

    public DataStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("configs"));
        cosmetics = JsonFiles.read(root.resolve("cosmetics.json")).orElseGet(() -> {
            JsonObject o = new JsonObject();
            o.add("owned", new JsonObject());
            o.add("equipped", new JsonObject());
            return o;
        });
        stats = JsonFiles.read(root.resolve("stats.json")).orElseGet(JsonObject::new);
        seedDefaults();
    }

    private void seedDefaults() throws IOException {
        if (!Files.exists(root.resolve("news.json"))) {
            JsonObject news = new JsonObject();
            JsonArray items = new JsonArray();
            JsonObject item = new JsonObject();
            item.addProperty("title", "Velora-Backend läuft");
            item.addProperty("date", java.time.LocalDate.now().toString());
            item.addProperty("body", "Diese News kommt vom eigenen Velora-Backend. Bearbeite data/news.json, um sie zu ändern.");
            item.addProperty("url", "");
            items.add(item);
            news.add("items", items);
            JsonFiles.write(root.resolve("news.json"), news);
        }
        if (!Files.exists(root.resolve("updates.json"))) {
            JsonObject updates = new JsonObject();
            JsonObject example = new JsonObject();
            example.addProperty("component", "client-1.21.4");
            example.addProperty("version", "0.1.0");
            example.addProperty("url", "");
            example.addProperty("sha256", "");
            example.addProperty("changelog", "Erste Version");
            updates.add("client-1.21.4", example);
            JsonFiles.write(root.resolve("updates.json"), updates);
        }
    }

    public static String normalizeUuid(String raw) {
        if (raw == null || !UUID_PATTERN.matcher(raw).matches()) throw new IllegalArgumentException("Ungültige UUID");
        String hex = raw.replace("-", "").toLowerCase();
        return UUID.fromString(hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")).toString();
    }

    public JsonObject news() { return JsonFiles.read(root.resolve("news.json")).orElseGet(JsonObject::new); }

    public JsonObject update(String component) {
        JsonObject all = JsonFiles.read(root.resolve("updates.json")).orElseGet(JsonObject::new);
        return all.has(component) ? all.getAsJsonObject(component) : new JsonObject();
    }

    public synchronized JsonArray owned(String uuid) {
        JsonObject owned = cosmetics.getAsJsonObject("owned");
        return owned.has(uuid) ? owned.getAsJsonArray(uuid).deepCopy() : new JsonArray();
    }

    public synchronized boolean grant(String uuid, String cosmeticId) throws IOException {
        JsonObject owned = cosmetics.getAsJsonObject("owned");
        JsonArray list = owned.has(uuid) ? owned.getAsJsonArray(uuid) : new JsonArray();
        for (var e : list) if (e.getAsString().equals(cosmeticId)) return false;
        list.add(cosmeticId);
        owned.add(uuid, list);
        JsonFiles.write(root.resolve("cosmetics.json"), cosmetics);
        return true;
    }

    public synchronized JsonObject equipped(String uuid) {
        JsonObject eq = cosmetics.getAsJsonObject("equipped");
        return eq.has(uuid) ? eq.getAsJsonObject(uuid).deepCopy() : new JsonObject();
    }

    public synchronized void equip(String uuid, String type, String id) throws IOException {
        JsonObject eq = cosmetics.getAsJsonObject("equipped");
        JsonObject mine = eq.has(uuid) ? eq.getAsJsonObject(uuid) : new JsonObject();
        if (id == null || id.isEmpty()) mine.remove(type);
        else mine.addProperty(type, id);
        eq.add(uuid, mine);
        JsonFiles.write(root.resolve("cosmetics.json"), cosmetics);
    }

    public synchronized boolean ownsOrFree(String uuid, String cosmeticId, Set<String> freeIds) {
        if (freeIds.contains(cosmeticId)) return true;
        for (var e : owned(uuid)) if (e.getAsString().equals(cosmeticId)) return true;
        return false;
    }

    public JsonObject loadConfig(String uuid) {
        return JsonFiles.read(root.resolve("configs").resolve(uuid + ".json")).orElse(null);
    }

    public void saveConfig(String uuid, JsonObject config) throws IOException {
        JsonFiles.write(root.resolve("configs").resolve(uuid + ".json"), config);
    }

    public synchronized void countLaunch(String client, String minecraft) {
        stats.addProperty("launches", (stats.has("launches") ? stats.get("launches").getAsLong() : 0) + 1);
        increment("byClient", client);
        increment("byMinecraft", minecraft);
    }

    private void increment(String bucket, String key) {
        if (key == null || key.isBlank() || key.length() > 32) key = "unknown";
        JsonObject b = stats.has(bucket) ? stats.getAsJsonObject(bucket) : new JsonObject();
        b.addProperty(key, (b.has(key) ? b.get(key).getAsLong() : 0) + 1);
        stats.add(bucket, b);
    }

    public synchronized void flushStats() {
        try {
            JsonFiles.write(root.resolve("stats.json"), stats);
        } catch (IOException e) {
            LOGGER.error("Could not save stats", e);
        }
    }

    public synchronized JsonObject statsSnapshot() { return stats.deepCopy(); }
}
