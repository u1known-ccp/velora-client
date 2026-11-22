package dev.velora.client.mods.pvp;

import dev.velora.client.api.KeyListener;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.util.Animation;
import dev.velora.core.util.Easing;
import org.lwjgl.glfw.GLFW;

/** Smooth zoom by dividing the FOV (GameRendererMixin). Scrolling while zoomed changes the factor. */
public final class ZoomMod extends Module implements KeyListener {
    private final KeybindSetting key = add(new KeybindSetting("key", "Taste", "", GLFW.GLFW_KEY_C));
    private final ModeSetting mode = add(new ModeSetting("mode", "Modus", "", "Halten", "Halten", "Umschalten"));
    private final NumberSetting factor = add(new NumberSetting("factor", "Zoomstufe", "", 4, 1.5, 30, 0.5, "x"));
    private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Weicher Übergang", "", true));
    private final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scrollen zum Zoomen", "", true));
    private final BooleanSetting reduceSensitivity = add(new BooleanSetting("reduce_sensitivity", "Maus-Empfindlichkeit anpassen", "Langsamere Maus beim Zoomen", true));

    private final Animation current = new Animation(160, Easing.EASE_OUT_CUBIC, 1);
    private boolean active;
    private double scrollFactor;

    public ZoomMod() {
        super("zoom", "Zoom Mod", "Heranzoomen per Taste (optional mit Mausrad)", Category.PVP, false);
    }

    public boolean isActive() { return isEnabled() && active; }

    /** Current FOV divisor (1 = no zoom), animated. */
    public double getDivisor() {
        if (!isEnabled()) return 1;
        double target = active ? scrollFactor : 1;
        if (smooth.isOn()) {
            current.animateTo(target);
            return current.get();
        }
        current.set(target);
        return target;
    }

    public double getSensitivityMultiplier() {
        if (!isActive() || !reduceSensitivity.isOn()) return 1;
        return 1 / Math.max(1, getDivisor());
    }

    /** @return true if the scroll was consumed by the zoom. */
    public boolean onScroll(double amount) {
        if (!isActive() || !scroll.isOn() || amount == 0) return false;
        scrollFactor = Math.max(1.5, Math.min(50, scrollFactor * (amount > 0 ? 1.15 : 1 / 1.15)));
        return true;
    }

    @Override
    public void onKey(int code, boolean pressed) {
        if (code != key.getKey()) return;
        if (mode.is("Halten")) {
            setActive(pressed);
        } else if (pressed) {
            setActive(!active);
        }
    }

    private void setActive(boolean value) {
        if (value && !active) scrollFactor = factor.getDouble();
        active = value;
    }

    @Override
    protected void onDisable() {
        active = false;
        current.set(1);
    }
}
