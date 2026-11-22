package dev.velora.client.input;

import dev.velora.client.ModuleCache;
import dev.velora.client.api.KeyListener;
import dev.velora.client.gui.ModMenuScreen;
import dev.velora.core.VeloraCore;
import dev.velora.core.module.Module;
import dev.velora.core.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives raw keyboard/mouse events from the mixins and dispatches them to the menu key,
 * module toggle keys and {@link KeyListener} modules. Event driven – nothing is polled per tick.
 */
public final class InputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Input");

    private InputHandler() { }

    /** @param action GLFW action (PRESS / RELEASE / REPEAT) */
    public static void onKey(int key, int action) {
        if (!VeloraCore.isReady() || key == GLFW.GLFW_KEY_UNKNOWN || action == GLFW.GLFW_REPEAT) return;
        dispatch(key, action == GLFW.GLFW_PRESS);
    }

    public static void onMouseButton(int button, int action) {
        if (!VeloraCore.isReady() || action == GLFW.GLFW_REPEAT) return;
        // left/right click are never bindable for toggles (would break normal gameplay)
        if (button <= 1) return;
        dispatch(KeybindSetting.MOUSE_OFFSET + button, action == GLFW.GLFW_PRESS);
    }

    private static void dispatch(int code, boolean pressed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean inGame = mc.currentScreen == null && mc.getOverlay() == null;
        if (pressed && !inGame) return;

        if (pressed && code == VeloraCore.get().config().client().menuKey.getKey()) {
            mc.setScreen(new ModMenuScreen(null));
            return;
        }
        if (pressed) {
            for (Module m : ModuleCache.allModules()) {
                if (m.getToggleKey().getKey() == code) m.toggle();
            }
        }
        for (KeyListener listener : ModuleCache.keyListeners()) {
            try {
                listener.onKey(code, pressed);
            } catch (RuntimeException e) {
                LOGGER.error("Key listener failed", e);
            }
        }
    }

    /** Whether a bound key (keyboard or mouse) is currently held down. */
    public static boolean isDown(int code) {
        if (code == KeybindSetting.NONE) return false;
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        if (code >= KeybindSetting.MOUSE_OFFSET) {
            return GLFW.glfwGetMouseButton(handle, code - KeybindSetting.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
    }
}
