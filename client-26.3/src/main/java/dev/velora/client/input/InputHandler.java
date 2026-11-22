package dev.velora.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.velora.client.ModuleCache;
import dev.velora.client.api.KeyListener;
import dev.velora.client.gui.ModMenuScreen;
import dev.velora.core.VeloraCore;
import dev.velora.core.module.Module;
import dev.velora.core.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives raw keyboard/mouse events from the mixins and dispatches them to the menu key,
 * module toggle keys and {@link KeyListener} modules. Event driven – nothing is polled per tick.
 * <p>26.3 uses SDL3: key codes are {@link InputConstants} scancodes, mouse buttons start at 1 (left), 3 = right.
 */
public final class InputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Input");

    private InputHandler() { }

    /** @param action {@link InputConstants#PRESS}, {@link InputConstants#RELEASE} or {@link InputConstants#REPEAT} */
    public static void onKey(int key, int action) {
        if (!VeloraCore.isReady() || key == 0 || action == InputConstants.REPEAT) return;
        dispatch(key, action == InputConstants.PRESS);
    }

    public static void onMouseButton(int button, int action) {
        if (!VeloraCore.isReady() || action == InputConstants.REPEAT) return;
        // left/right click are never bindable for toggles (would break normal gameplay)
        if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) return;
        dispatch(KeybindSetting.MOUSE_OFFSET + button, action == InputConstants.PRESS);
    }

    private static void dispatch(int code, boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        boolean inGame = mc.gui.screen() == null && mc.gui.overlay() == null;
        if (pressed && !inGame) return;

        if (pressed && code == VeloraCore.get().config().client().menuKey.getKey()) {
            mc.gui.setScreen(new ModMenuScreen(null));
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

    /** Whether a bound keyboard key is currently held down (mouse buttons are tracked via events only). */
    public static boolean isDown(int code) {
        if (code == KeybindSetting.NONE || code >= KeybindSetting.MOUSE_OFFSET) return false;
        return InputConstants.isKeyDown(code);
    }
}
