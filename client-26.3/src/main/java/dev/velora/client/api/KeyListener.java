package dev.velora.client.api;

/**
 * Implemented by modules with action keys (zoom, freelook, macros, ...). Receives raw key events.
 * Key codes are GLFW key codes; mouse buttons arrive as {@code KeybindSetting.MOUSE_OFFSET + button}.
 * Press events are only delivered while no screen is open; release events are always delivered.
 */
public interface KeyListener {
    /** @param pressed true on press, false on release (repeat events are filtered out) */
    void onKey(int key, boolean pressed);
}
