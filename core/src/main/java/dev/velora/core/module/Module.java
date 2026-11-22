package dev.velora.core.module;

import com.google.gson.JsonObject;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.Setting;
import dev.velora.core.setting.SettingContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Base class of every Velora mod. A module is self-contained: it owns its settings,
 * its toggle keybind and reacts to {@link #onEnable()} / {@link #onDisable()}.
 * Platform specific behaviour (rendering, mixin hooks) lives in subclasses inside the client modules.
 */
public abstract class Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Module");

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean enabledByDefault;
    private final SettingContainer settings = new SettingContainer();
    private final KeybindSetting toggleKey;

    private boolean enabled;
    private boolean favorite;
    private Runnable dirtyHook = () -> { };
    private ModuleListener listener = ModuleListener.NONE;

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.category = Objects.requireNonNull(category);
        this.enabledByDefault = enabledByDefault;
        this.toggleKey = new KeybindSetting("toggle_key", "Umschalt-Taste", "Taste zum Ein-/Ausschalten dieses Mods", KeybindSetting.NONE);
        this.toggleKey.setDirtyHook(() -> dirtyHook.run());
        this.settings.setDirtyHook(() -> dirtyHook.run());
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final Category getCategory() { return category; }
    public final boolean isEnabled() { return enabled; }
    public final boolean isFavorite() { return favorite; }
    public final KeybindSetting getToggleKey() { return toggleKey; }
    public final List<Setting<?>> getSettings() { return settings.all(); }

    /** Registers a setting. Call from the subclass constructor. */
    protected final <S extends Setting<?>> S add(S setting) { return settings.add(setting); }

    public final void setEnabled(boolean value) {
        if (this.enabled == value) return;
        this.enabled = value;
        try {
            if (value) onEnable();
            else onDisable();
        } catch (RuntimeException e) {
            // A failing mod must never crash the game: log and roll back.
            LOGGER.error("Mod '{}' failed to {}", id, value ? "enable" : "disable", e);
            this.enabled = !value;
            if (value) {
                try { onDisable(); } catch (RuntimeException ignored) { }
            }
            return;
        }
        listener.onToggled(this, this.enabled);
        dirtyHook.run();
    }

    public final void toggle() { setEnabled(!enabled); }

    public final void setFavorite(boolean favorite) {
        if (this.favorite == favorite) return;
        this.favorite = favorite;
        dirtyHook.run();
    }

    /** Called when the module gets enabled. Override to register state, never block. */
    protected void onEnable() { }

    /** Called when the module gets disabled. Must undo every side effect of {@link #onEnable()}. */
    protected void onDisable() { }

    /** Restores every setting to its default value. */
    public void resetSettings() {
        settings.resetAll();
    }

    /** Hook used by the {@link ModuleManager} to schedule config saves. */
    final void bind(Runnable dirty, ModuleListener listener) {
        this.dirtyHook = dirty == null ? () -> { } : dirty;
        this.listener = listener == null ? ModuleListener.NONE : listener;
    }

    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("favorite", favorite);
        o.add("toggle_key", toggleKey.toJson());
        o.add("settings", settings.toJson());
        saveExtra(o);
        return o;
    }

    public void load(JsonObject o) {
        if (o == null) {
            settings.resetAll();
            toggleKey.reset();
            favorite = false;
            loadExtra(null);
            setEnabled(enabledByDefault);
            return;
        }
        settings.fromJson(o.has("settings") && o.get("settings").isJsonObject() ? o.getAsJsonObject("settings") : null);
        toggleKey.fromJson(o.get("toggle_key"));
        favorite = o.has("favorite") && o.get("favorite").getAsBoolean();
        loadExtra(o);
        boolean shouldEnable = o.has("enabled") ? o.get("enabled").getAsBoolean() : enabledByDefault;
        setEnabled(shouldEnable);
    }

    /** Extension point for subclasses that persist additional state (e.g. HUD position). */
    protected void saveExtra(JsonObject o) { }

    protected void loadExtra(JsonObject o) { }

    @Override
    public String toString() { return "Module[" + id + "]"; }

    /** Notification about module state changes (used by the client to e.g. refresh caches). */
    @FunctionalInterface
    public interface ModuleListener {
        ModuleListener NONE = (m, e) -> { };

        void onToggled(Module module, boolean enabled);
    }
}
