package dev.velora.core.setting;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single, typed, persistable option of a module or of the client itself.
 * Settings are platform independent; each Minecraft version renders them with its own widgets.
 */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private Runnable dirtyHook = () -> { };

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getDefault() { return defaultValue; }
    public T get() { return value; }

    /** Sets the value (validated by {@link #sanitize(Object)}) and notifies listeners if it changed. */
    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (Objects.equals(sanitized, value)) return;
        value = sanitized;
        fireChanged();
    }

    /** Notifies listeners and schedules a save; for subclasses with extra state besides {@link #value}. */
    protected void fireChanged() {
        for (Consumer<T> listener : listeners) listener.accept(value);
        dirtyHook.run();
    }

    public void reset() { set(defaultValue); }

    protected T sanitize(T input) { return input == null ? defaultValue : input; }

    public Setting<T> onChange(Consumer<T> listener) {
        listeners.add(listener);
        return this;
    }

    /** Only show the setting in the GUI while the condition holds (e.g. sub-options of a toggle). */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return this;
    }

    public boolean isVisible() { return visibility.getAsBoolean(); }

    /** Called by the owning container so changes schedule a config save. */
    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    public abstract JsonElement toJson();

    /** Loads the value from JSON. Invalid or missing data falls back to the default value. */
    public abstract void fromJson(JsonElement element);
}
