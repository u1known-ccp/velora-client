package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A key binding stored as a GLFW key code (modern versions). {@link #NONE} means unbound.
 * Mouse buttons are stored as {@code MOUSE_OFFSET + button} so they can share the same setting.
 * Legacy (LWJGL2) platforms translate codes in their platform layer.
 */
public class KeybindSetting extends Setting<Integer> {
    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = 10_000;

    public KeybindSetting(String id, String name, String description, int defaultKey) {
        super(id, name, description, defaultKey);
    }

    public int getKey() { return value; }

    public boolean isBound() { return value != NONE; }

    public boolean isMouse() { return value >= MOUSE_OFFSET; }

    public int getMouseButton() { return value - MOUSE_OFFSET; }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) set(element.getAsInt());
        else reset();
    }
}
