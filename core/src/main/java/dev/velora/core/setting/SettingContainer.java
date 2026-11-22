package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Ordered group of settings with JSON (de)serialization. Used by modules and by global client settings. */
public class SettingContainer {
    private final List<Setting<?>> settings = new ArrayList<>();
    private Runnable dirtyHook = () -> { };

    public <S extends Setting<?>> S add(S setting) {
        for (Setting<?> existing : settings) {
            if (existing.getId().equals(setting.getId())) {
                throw new IllegalArgumentException("Duplicate setting id: " + setting.getId());
            }
        }
        setting.setDirtyHook(() -> dirtyHook.run());
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> all() { return Collections.unmodifiableList(settings); }

    public Optional<Setting<?>> find(String id) {
        return settings.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    public void resetAll() { settings.forEach(Setting::reset); }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (Setting<?> s : settings) o.add(s.getId(), s.toJson());
        return o;
    }

    public void fromJson(JsonObject o) {
        for (Setting<?> s : settings) {
            JsonElement e = o == null ? null : o.get(s.getId());
            try {
                s.fromJson(e);
            } catch (RuntimeException ex) {
                s.reset();
            }
        }
    }
}
