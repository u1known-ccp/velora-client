package dev.velora.core;

import dev.velora.core.backend.BackendClient;
import dev.velora.core.config.ConfigManager;
import dev.velora.core.cosmetics.CosmeticsManager;
import dev.velora.core.event.CoreEvents;
import dev.velora.core.event.EventBus;
import dev.velora.core.module.ModuleManager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Central, version independent state of the client. One instance per game session.
 * The platform layer registers its modules into {@link #modules()} and then calls {@link #start()}.
 */
public final class VeloraCore {
    public static final String NAME = "Velora";
    private static VeloraCore instance;

    private final String version;
    private final String minecraftVersion;
    private final EventBus bus = new EventBus();
    private final ModuleManager modules = new ModuleManager();
    private final ConfigManager config;
    private final CosmeticsManager cosmetics;
    private volatile BackendClient backend;
    private boolean started;

    public VeloraCore(String version, String minecraftVersion, Path configRoot, Executor mainThread) {
        this.version = Objects.requireNonNull(version);
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion);
        this.config = new ConfigManager(configRoot, modules, bus, mainThread);
        this.cosmetics = new CosmeticsManager(() -> backend);
        modules.addListener((module, enabled) -> bus.post(new CoreEvents.ModuleToggled(module, enabled)));
        bus.subscribe(CoreEvents.ClientSettingsChanged.class, e -> rebuildBackend());
        instance = this;
    }

    public static VeloraCore get() {
        if (instance == null) throw new IllegalStateException("Velora core not initialised");
        return instance;
    }

    public static boolean isReady() { return instance != null && instance.started; }

    /** Loads the configuration (enabling modules as stored). Call after all modules are registered. */
    public void start() {
        if (started) return;
        config.load();
        rebuildBackend();
        started = true;
        if (backend != null && backend.isEnabled() && config.client().statistics.isOn()) {
            backend.reportLaunch(version, minecraftVersion);
        }
    }

    private void rebuildBackend() {
        var settings = config.client();
        String url = settings.backendEnabled.isOn() ? settings.backendUrl.get() : "";
        BackendClient current = backend;
        if (current != null && current.getBaseUrl().equals(url.trim().replaceAll("/+$", ""))) return;
        backend = new BackendClient(url, NAME + "/" + version + " (Minecraft " + minecraftVersion + ")");
        cosmetics.clearRemoteCache();
    }

    public void shutdown() {
        config.shutdown();
    }

    public String version() { return version; }
    public String minecraftVersion() { return minecraftVersion; }
    public EventBus bus() { return bus; }
    public ModuleManager modules() { return modules; }
    public ConfigManager config() { return config; }
    public CosmeticsManager cosmetics() { return cosmetics; }
    public BackendClient backend() { return backend; }
}
