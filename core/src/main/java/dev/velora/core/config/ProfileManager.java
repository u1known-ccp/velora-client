package dev.velora.core.config;

import com.google.gson.JsonObject;
import dev.velora.core.module.Module;
import dev.velora.core.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Profiles are complete snapshots of all module states, settings, keybinds and HUD positions.
 * Each profile is one JSON file in {@code config/velora/profiles/}.
 */
public final class ProfileManager {
    public static final String DEFAULT_PROFILE = "Default";
    public static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Profiles");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9 _\\-]{1,32}");

    private final Path directory;
    private final ModuleManager modules;
    private String active = DEFAULT_PROFILE;

    public ProfileManager(Path directory, ModuleManager modules) {
        this.directory = directory;
        this.modules = modules;
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches() && !name.isBlank();
    }

    public String getActive() { return active; }

    void setActiveName(String name) {
        this.active = isValidName(name) ? name : DEFAULT_PROFILE;
    }

    public Path fileOf(String name) { return directory.resolve(name + ".json"); }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
                for (Path p : stream) {
                    String file = p.getFileName().toString();
                    String name = file.substring(0, file.length() - ".json".length());
                    if (isValidName(name)) names.add(name);
                }
            } catch (IOException e) {
                LOGGER.error("Could not list profiles", e);
            }
        }
        if (!names.contains(active)) names.add(active);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Serializes the current state of every module. */
    public JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        JsonObject mods = new JsonObject();
        for (Module m : modules.all()) mods.add(m.getId(), m.save());
        root.add("modules", mods);
        return root;
    }

    /** Applies a snapshot; modules missing in the snapshot are reset to their defaults. */
    public void apply(JsonObject root) {
        JsonObject mods = root != null && root.has("modules") && root.get("modules").isJsonObject()
                ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module m : modules.all()) {
            JsonObject data = mods.has(m.getId()) && mods.get(m.getId()).isJsonObject() ? mods.getAsJsonObject(m.getId()) : null;
            try {
                m.load(data);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to load module {} from profile, using defaults", m.getId(), e);
                m.load(null);
            }
        }
    }

    public Optional<JsonObject> readProfile(String name) {
        return JsonFiles.read(fileOf(name));
    }

    public void writeProfile(String name, JsonObject json) throws IOException {
        JsonFiles.write(fileOf(name), json);
    }

    public boolean exists(String name) { return Files.isRegularFile(fileOf(name)); }

    public void delete(String name) throws IOException {
        if (name.equals(active)) throw new IllegalStateException("Das aktive Profil kann nicht gelöscht werden");
        Files.deleteIfExists(fileOf(name));
    }

    /** Copies a profile file into an arbitrary location (export). */
    public void export(String name, Path target, JsonObject currentSnapshotIfActive) throws IOException {
        JsonObject json = name.equals(active) ? currentSnapshotIfActive
                : readProfile(name).orElseThrow(() -> new IOException("Profil nicht gefunden: " + name));
        JsonFiles.write(target, json);
    }

    /** Imports a profile file under a free name derived from the file name. Returns the new profile name. */
    public String importFrom(Path source) throws IOException {
        JsonObject json = JsonFiles.read(source).orElseThrow(() -> new IOException("Keine gültige Profildatei: " + source));
        if (!json.has("modules")) throw new IOException("Datei enthält keine Velora-Moddaten");
        String base = source.getFileName().toString().replaceFirst("\\.json$", "").replaceAll("[^A-Za-z0-9 _\\-]", "");
        if (base.isBlank()) base = "Import";
        if (base.length() > 28) base = base.substring(0, 28);
        String name = uniqueName(base);
        writeProfile(name, json);
        return name;
    }

    public String uniqueName(String base) {
        String trimmed = base.length() > 28 ? base.substring(0, 28) : base;
        String name = trimmed;
        int i = 2;
        while (exists(name) || name.toLowerCase(Locale.ROOT).equals(active.toLowerCase(Locale.ROOT))) {
            name = trimmed + " " + i++;
        }
        return name;
    }
}
