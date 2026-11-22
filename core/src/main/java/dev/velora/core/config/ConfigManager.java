package dev.velora.core.config;

import com.google.gson.JsonObject;
import dev.velora.core.event.CoreEvents;
import dev.velora.core.event.EventBus;
import dev.velora.core.hud.HudModule;
import dev.velora.core.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Loads and saves {@code client.json} and the active profile.
 * Saves are debounced (changes within {@link #SAVE_DELAY_MS} are merged into one write) and written off-thread.
 * The JSON snapshot itself is taken on the game thread via {@code mainThread} to avoid data races.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Config");
    private static final long SAVE_DELAY_MS = 800;

    private final Path root;
    private final Path clientFile;
    private final ClientSettings clientSettings = new ClientSettings();
    private final ProfileManager profiles;
    private final ModuleManager modules;
    private final EventBus bus;
    private final Executor mainThread;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Velora-Config-IO");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private volatile boolean loading;
    private ScheduledFuture<?> pendingSave;

    public ConfigManager(Path root, ModuleManager modules, EventBus bus, Executor mainThread) {
        this.root = root;
        this.clientFile = root.resolve("client.json");
        this.modules = modules;
        this.bus = bus;
        this.mainThread = mainThread;
        this.profiles = new ProfileManager(root.resolve("profiles"), modules);
        modules.setDirtyHook(this::markDirty);
        clientSettings.setDirtyHook(() -> {
            markDirty();
            bus.post(new CoreEvents.ClientSettingsChanged());
        });
    }

    public Path getRoot() { return root; }
    public ClientSettings client() { return clientSettings; }
    public ProfileManager profiles() { return profiles; }

    /** Loads everything synchronously. Call once during client start. */
    public void load() {
        for (HudModule hud : modules.ofType(HudModule.class)) hud.setLayoutDirtyHook(this::markDirty);
        loading = true;
        try {
            JsonObject client = JsonFiles.read(clientFile).orElse(null);
            clientSettings.fromJson(client != null && client.has("settings") ? client.getAsJsonObject("settings") : null);
            profiles.setActiveName(client != null && client.has("active_profile") ? client.get("active_profile").getAsString() : ProfileManager.DEFAULT_PROFILE);
            profiles.apply(profiles.readProfile(profiles.getActive()).orElse(null));
            LOGGER.info("Loaded profile '{}' ({} modules)", profiles.getActive(), modules.all().size());
        } finally {
            loading = false;
        }
        saveNow();
    }

    public synchronized void markDirty() {
        if (loading) return;
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = io.schedule(() -> mainThread.execute(this::snapshotAndWriteAsync), SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void snapshotAndWriteAsync() {
        JsonObject client = clientJson();
        JsonObject profile = profiles.snapshot();
        String profileName = profiles.getActive();
        io.execute(() -> write(client, profileName, profile));
    }

    /** Blocking save on the calling thread (used on shutdown and before profile switches). */
    public synchronized void saveNow() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
        write(clientJson(), profiles.getActive(), profiles.snapshot());
    }

    private JsonObject clientJson() {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("active_profile", profiles.getActive());
        o.add("settings", clientSettings.toJson());
        return o;
    }

    private synchronized void write(JsonObject client, String profileName, JsonObject profile) {
        try {
            JsonFiles.write(clientFile, client);
            profiles.writeProfile(profileName, profile);
        } catch (IOException e) {
            LOGGER.error("Could not save Velora config", e);
        }
    }

    /** Saves the current profile and loads another one (creates it from defaults if it does not exist). */
    public void switchProfile(String name) {
        if (!ProfileManager.isValidName(name)) throw new IllegalArgumentException("Ungültiger Profilname");
        saveNow();
        loading = true;
        try {
            profiles.setActiveName(name);
            profiles.apply(profiles.readProfile(name).orElse(null));
        } finally {
            loading = false;
        }
        saveNow();
        bus.post(new CoreEvents.ProfileSwitched(name));
    }

    /** Creates a new profile, either as a copy of the current state or with defaults. */
    public String createProfile(String name, boolean copyCurrent) throws IOException {
        if (!ProfileManager.isValidName(name)) throw new IllegalArgumentException("Ungültiger Profilname");
        String unique = profiles.exists(name) ? profiles.uniqueName(name) : name;
        JsonObject json = copyCurrent ? profiles.snapshot() : new JsonObject();
        profiles.writeProfile(unique, json);
        return unique;
    }

    public void exportProfile(String name, Path target) throws IOException {
        profiles.export(name, target, profiles.snapshot());
    }

    public String importProfile(Path source) throws IOException {
        return profiles.importFrom(source);
    }

    public void shutdown() {
        saveNow();
        io.shutdown();
        try {
            if (!io.awaitTermination(2, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
