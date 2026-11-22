package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One-of-many choice, rendered as a cycling selector. */
public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String id, String name, String description, String defaultMode, String... modes) {
        super(id, name, description, defaultMode);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultMode)) {
            throw new IllegalArgumentException("Default mode '" + defaultMode + "' not in modes for " + id);
        }
    }

    public List<String> getModes() { return modes; }

    public boolean is(String mode) { return value.equalsIgnoreCase(mode); }

    public int getIndex() { return Math.max(0, modes.indexOf(value)); }

    public void cycle(int direction) {
        int size = modes.size();
        set(modes.get(Math.floorMod(getIndex() + direction, size)));
    }

    @Override
    protected String sanitize(String input) {
        if (input == null) return getDefault();
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(input)) return mode;
        }
        return getDefault();
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsString());
        else reset();
    }
}
