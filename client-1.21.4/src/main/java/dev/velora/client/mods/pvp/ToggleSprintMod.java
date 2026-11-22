package dev.velora.client.mods.pvp;

import dev.velora.client.api.TickListener;
import dev.velora.client.hud.TextHudMod;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Toggle Sprint: pressing the sprint key toggles a persistent sprint state. The vanilla key binding
 * reports "pressed" while toggled (KeyBindingMixin), so the game itself decides whether sprinting is possible –
 * no movement is forced. Shows an optional status text.
 */
public final class ToggleSprintMod extends TextHudMod implements TickListener {
    private final BooleanSetting showText = add(new BooleanSetting("show_text", "Status-Text", "", true));
    private boolean toggled;

    public ToggleSprintMod() {
        super("toggle_sprint", "Toggle Sprint", "Sprint-Taste schaltet Sprinten dauerhaft an/aus", Category.PVP, false, Anchor.BOTTOM_RIGHT, -4, -4);
        showLabel.set(false);
        background.set(false);
    }

    public boolean isSprintForced() {
        return isEnabled() && toggled;
    }

    @Override
    protected void onDisable() { toggled = false; }

    @Override
    public void onTick(MinecraftClient mc) {
        // vanilla never consumes wasPressed() for the sprint key, so we can use the press counter
        while (mc.options.sprintKey.wasPressed()) toggled = !toggled;
    }

    @Override protected String label() { return ""; }

    @Override
    protected String value(boolean preview) {
        ToggleSneakMod sneak = dev.velora.client.Mods.toggleSneak;
        if (sneak != null && sneak.isSneakForced()) return "[Schleichen (Umgeschaltet)]";
        if (toggled) return "[Sprinten (Umgeschaltet)]";
        if (MC.player != null && MC.player.isSprinting()) return "[Sprinten (Taste)]";
        return preview ? "[Sprinten (Umgeschaltet)]" : "";
    }

    @Override
    public boolean hasContent() { return showText.isOn() && !value(false).isEmpty(); }

    @Override protected long refreshIntervalMs() { return 50; }
}
