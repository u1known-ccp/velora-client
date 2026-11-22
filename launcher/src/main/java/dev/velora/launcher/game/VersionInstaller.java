package dev.velora.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.velora.core.backend.UpdateInfo;
import dev.velora.core.config.JsonFiles;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.config.BuildInfo;
import dev.velora.launcher.config.LauncherSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Installs vanilla Minecraft (official piston-meta data), Fabric Loader (meta.fabricmc.net)
 * and the Velora + Fabric API mods into the profile's instance directory. Idempotent: verified
 * files are skipped, so a second launch only checks hashes.
 */
public final class VersionInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Installer");
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json";
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
    private static final String ASSETS = "https://resources.download.minecraft.net/";

    public interface Listener {
        void status(String text);

        void progress(double fraction);
    }

    private final Downloader downloader = new Downloader();
    private final BuildInfo build;

    public VersionInstaller(BuildInfo build) {
        this.build = build;
    }

    public LaunchSpec install(LauncherSettings.GameProfile profile, Listener listener, BooleanSupplier cancelled) throws IOException {
        LauncherPaths.ensure();
        String mc = profile.minecraftVersion;

        // 1. vanilla version JSON
        listener.status("Lade Versionsliste…");
        listener.progress(-1);
        Path versionJsonPath = LauncherPaths.versions().resolve(mc).resolve(mc + ".json");
        JsonObject vanilla = loadVanillaJson(mc, versionJsonPath);

        // 2. Fabric profile JSON
        listener.status("Lade Fabric-Profil…");
        String loader = profile.loaderVersion;
        String fabricId = "fabric-loader-" + loader + "-" + mc;
        Path fabricJsonPath = LauncherPaths.versions().resolve(fabricId).resolve(fabricId + ".json");
        JsonObject fabric = loadFabricJson(mc, loader, fabricJsonPath);

        // 3. client jar + libraries
        List<Downloader.Task> tasks = new ArrayList<>();
        JsonObject client = vanilla.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = LauncherPaths.versions().resolve(mc).resolve(mc + ".jar");
        tasks.add(new Downloader.Task(client.get("url").getAsString(), clientJar, client.get("sha1").getAsString(), client.get("size").getAsLong()));

        Map<String, Path> classpath = new LinkedHashMap<>();
        for (JsonElement e : vanilla.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(lib.has("rules") ? lib.getAsJsonArray("rules") : null, Map.of())) continue;
            if (!lib.has("downloads") || !lib.getAsJsonObject("downloads").has("artifact")) continue;
            JsonObject art = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            Path path = LauncherPaths.libraries().resolve(art.get("path").getAsString());
            tasks.add(new Downloader.Task(art.get("url").getAsString(), path, art.get("sha1").getAsString(), art.get("size").getAsLong()));
            classpath.put(libraryKey(lib.get("name").getAsString()), path);
        }
        // Fabric libraries override vanilla ones with the same group:artifact (e.g. ASM)
        for (JsonElement e : fabric.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            String name = lib.get("name").getAsString();
            String base = lib.has("url") ? lib.get("url").getAsString() : FABRIC_MAVEN;
            if (!base.endsWith("/")) base += "/";
            String rel = mavenPath(name);
            Path path = LauncherPaths.libraries().resolve(rel);
            String sha1 = lib.has("sha1") ? lib.get("sha1").getAsString() : "";
            long size = lib.has("size") ? lib.get("size").getAsLong() : -1;
            tasks.add(new Downloader.Task(base + rel, path, sha1, size));
            classpath.put(libraryKey(name), path);
        }

        // 4. logging config
        String loggingArg = null;
        if (vanilla.has("logging") && vanilla.getAsJsonObject("logging").has("client")) {
            JsonObject log = vanilla.getAsJsonObject("logging").getAsJsonObject("client");
            JsonObject file = log.getAsJsonObject("file");
            Path logPath = LauncherPaths.assets().resolve("log_configs").resolve(file.get("id").getAsString());
            tasks.add(new Downloader.Task(file.get("url").getAsString(), logPath, file.get("sha1").getAsString(), file.get("size").getAsLong()));
            loggingArg = log.get("argument").getAsString().replace("${path}", logPath.toAbsolutePath().toString());
        }

        listener.status("Lade Spieldateien…");
        downloader.downloadAll(tasks, (done, total, files, totalFiles) -> {
            listener.status("Lade Spieldateien… (" + files + "/" + totalFiles + ")");
            listener.progress(done / (double) total);
        }, cancelled);

        // 5. assets
        JsonObject assetIndex = vanilla.getAsJsonObject("assetIndex");
        String indexId = assetIndex.get("id").getAsString();
        Path indexPath = LauncherPaths.assets().resolve("indexes").resolve(indexId + ".json");
        downloader.downloadAll(List.of(new Downloader.Task(assetIndex.get("url").getAsString(), indexPath,
                assetIndex.get("sha1").getAsString(), assetIndex.get("size").getAsLong())), (a, b, c, d) -> { }, cancelled);
        JsonObject objects = JsonFiles.read(indexPath).orElseThrow(() -> new IOException("Asset-Index fehlt")).getAsJsonObject("objects");
        List<Downloader.Task> assetTasks = new ArrayList<>();
        for (String key : objects.keySet()) {
            JsonObject o = objects.getAsJsonObject(key);
            String hash = o.get("hash").getAsString();
            String sub = hash.substring(0, 2) + "/" + hash;
            // size check only for assets (thousands of files) – hash verified on download
            assetTasks.add(new Downloader.Task(ASSETS + sub, LauncherPaths.assets().resolve("objects").resolve(sub), "", o.get("size").getAsLong()));
        }
        List<Downloader.Task> missing = assetTasks.stream().filter(t -> !Downloader.isValid(t)).toList();
        if (!missing.isEmpty()) {
            listener.status("Lade Assets…");
            // re-attach hashes for downloads so corrupted transfers are detected
            List<Downloader.Task> withHash = missing.stream()
                    .map(t -> new Downloader.Task(t.url(), t.target(), t.target().getFileName().toString(), t.size())).toList();
            downloader.downloadAll(withHash, (done, total, files, totalFiles) -> {
                listener.status("Lade Assets… (" + files + "/" + totalFiles + ")");
                listener.progress(done / (double) total);
            }, cancelled);
        }

        // 6. mods
        listener.status("Installiere Velora…");
        listener.progress(-1);
        Path gameDir = LauncherPaths.instance(profile.name);
        installMods(gameDir.resolve("mods"), cancelled);

        Path natives = LauncherPaths.versions().resolve(mc).resolve("natives");
        Files.createDirectories(natives);

        List<Path> cp = new ArrayList<>(classpath.values());
        cp.add(clientJar);

        JsonArray jvmArgs = new JsonArray();
        JsonArray gameArgs = new JsonArray();
        if (vanilla.has("arguments")) {
            JsonObject a = vanilla.getAsJsonObject("arguments");
            if (a.has("jvm")) jvmArgs.addAll(a.getAsJsonArray("jvm"));
            if (a.has("game")) gameArgs.addAll(a.getAsJsonArray("game"));
        }
        if (fabric.has("arguments")) {
            JsonObject a = fabric.getAsJsonObject("arguments");
            if (a.has("jvm")) jvmArgs.addAll(a.getAsJsonArray("jvm"));
            if (a.has("game")) gameArgs.addAll(a.getAsJsonArray("game"));
        }
        int java = vanilla.has("javaVersion") ? vanilla.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 21;
        listener.status("Bereit");
        listener.progress(1);
        return new LaunchSpec(fabricId, fabric.get("mainClass").getAsString(), cp, jvmArgs, gameArgs, loggingArg, indexId, gameDir, natives, java);
    }

    private JsonObject loadVanillaJson(String mc, Path target) throws IOException {
        Optional<JsonObject> cached = JsonFiles.read(target);
        try {
            JsonObject manifest = Http.getJson(MANIFEST);
            for (JsonElement e : manifest.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                if (v.get("id").getAsString().equals(mc)) {
                    downloader.downloadAll(List.of(new Downloader.Task(v.get("url").getAsString(), target, v.get("sha1").getAsString(), -1)),
                            (a, b, c, d) -> { }, () -> false);
                    return JsonFiles.read(target).orElseThrow(() -> new IOException("Version-JSON ungültig"));
                }
            }
            throw new IOException("Minecraft-Version " + mc + " nicht gefunden");
        } catch (IOException e) {
            // offline: use the cached JSON if we have one
            if (cached.isPresent()) {
                LOGGER.warn("Using cached version JSON ({}).", e.getMessage());
                return cached.get();
            }
            throw e;
        }
    }

    private JsonObject loadFabricJson(String mc, String loader, Path target) throws IOException {
        try {
            JsonObject json = Http.getJson(String.format(FABRIC_META, mc, loader));
            JsonFiles.write(target, json);
            return json;
        } catch (IOException e) {
            Optional<JsonObject> cached = JsonFiles.read(target);
            if (cached.isPresent()) return cached.get();
            throw e;
        }
    }

    /** Copies the bundled (or a newer, downloaded and verified) Velora jar and downloads Fabric API. */
    public void installMods(Path modsDir, BooleanSupplier cancelled) throws IOException {
        Files.createDirectories(modsDir);
        // remove stale managed jars of other versions
        String fabricApiName = "fabric-api-" + build.fabricApiVersion() + ".jar";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path p : stream) {
                String n = p.getFileName().toString();
                if (n.startsWith("fabric-api-") && !n.equals(fabricApiName)) Files.delete(p);
            }
        }

        Path veloraTarget = modsDir.resolve("velora-client.jar");
        Path updated = newestDownloadedClientUpdate();
        if (updated != null) {
            Files.copy(updated, veloraTarget, StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = VersionInstaller.class.getResourceAsStream("/velora/bundled/velora-client.bin")) {
                if (in == null) throw new IOException("Velora-Client-Jar fehlt im Launcher (Build unvollständig)");
                Path tmp = modsDir.resolve("velora-client.jar.tmp");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, veloraTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        String v = build.fabricApiVersion();
        String url = FABRIC_MAVEN + "net/fabricmc/fabric-api/fabric-api/" + v + "/fabric-api-" + v + ".jar";
        String sha1 = Http.getString(url + ".sha1").trim();
        downloader.downloadAll(List.of(new Downloader.Task(url, modsDir.resolve(fabricApiName), sha1, -1)), (a, b, c, d) -> { }, cancelled);
    }

    /** A client update downloaded by the UpdateService (verified by SHA-256 before it was stored). */
    private Path newestDownloadedClientUpdate() throws IOException {
        Path dir = LauncherPaths.updates();
        if (!Files.isDirectory(dir)) return null;
        Path best = null;
        String bestVersion = build.version();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "velora-client-*.jar")) {
            for (Path p : stream) {
                String v = p.getFileName().toString().replace("velora-client-", "").replace(".jar", "");
                if (UpdateInfo.compareVersions(v, bestVersion) > 0) {
                    best = p;
                    bestVersion = v;
                }
            }
        }
        return best;
    }

    /** group:artifact[:classifier] – used to de-duplicate libraries across vanilla and Fabric. */
    static String libraryKey(String coords) {
        String[] p = coords.split(":");
        return p.length >= 4 ? p[0] + ":" + p[1] + ":" + p[3] : p[0] + ":" + p[1];
    }

    /** Maven coordinates → repository path (group/artifact/version/artifact-version[-classifier].jar). */
    static String mavenPath(String coords) {
        String[] p = coords.split(":");
        if (p.length < 3) throw new IllegalArgumentException("Ungültige Maven-Koordinate: " + coords);
        String group = p[0].replace('.', '/');
        String file = p[1] + "-" + p[2] + (p.length >= 4 ? "-" + p[3] : "") + ".jar";
        return group + "/" + p[1] + "/" + p[2] + "/" + file;
    }
}
