package dev.velora.client.mods.pvp;

import dev.velora.client.api.KeyListener;
import dev.velora.client.api.TickListener;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Perspective / Freelook: look around in third person without turning the player.
 * The mouse delta is redirected to a separate camera rotation (MouseMixin) which the camera uses
 * instead of the player rotation (CameraMixin). Server-side the player keeps looking straight ahead.
 */
public final class PerspectiveMod extends Module implements KeyListener, TickListener {
    private final KeybindSetting key = add(new KeybindSetting("key", "Taste", "", InputConstants.KEY_LALT));
    private final ModeSetting mode = add(new ModeSetting("mode", "Modus", "", "Halten", "Halten", "Umschalten"));
    private final BooleanSetting invertPitch = add(new BooleanSetting("invert_pitch", "Vertikal invertieren", "", false));
    private final ModeSetting view = add(new ModeSetting("view", "Ansicht", "", "Hinten", "Hinten", "Vorne"));

    private boolean active;
    private CameraType previous;
    private float yaw;
    private float pitch;

    public PerspectiveMod() {
        super("perspective", "Perspective Mod", "Freelook: in der 3rd Person frei umsehen (Freelook)", Category.PVP, false);
    }

    public boolean isActive() { return isEnabled() && active; }

    public float getYaw() { return yaw; }

    public float getPitch() { return pitch; }

    /** Applies a mouse delta to the free camera (same factor as Entity#changeLookDirection). */
    public void addDelta(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch += (float) (invertPitch.isOn() ? -dy : dy) * 0.15f;
        pitch = Mth.clamp(pitch, -90f, 90f);
    }

    @Override
    public void onKey(int code, boolean pressed) {
        if (code != key.getKey()) return;
        if (mode.is("Halten")) {
            if (pressed) start();
            else stop();
        } else if (pressed) {
            if (active) stop();
            else start();
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (active && mc.player == null) stop();
    }

    private void start() {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.player == null) return;
        active = true;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        previous = mc.options.getCameraType();
        // in front view the camera itself flips yaw/pitch after our rotation is applied
        mc.options.setCameraType(view.is("Vorne") ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
    }

    private void stop() {
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }

    @Override
    protected void onDisable() { stop(); }
}
