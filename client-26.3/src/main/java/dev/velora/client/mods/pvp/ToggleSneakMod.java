package dev.velora.client.mods.pvp;

import dev.velora.client.api.TickListener;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

/** Toggle Sneak: pressing the sneak key toggles a persistent sneak state (status text via Toggle Sprint). */
public final class ToggleSneakMod extends Module implements TickListener {
    private final BooleanSetting disableWhileFlying = add(new BooleanSetting("disable_flying", "Beim Fliegen deaktivieren", "Verhindert ungewolltes Sinken im Kreativ-Flug", true));
    private boolean toggled;

    public ToggleSneakMod() {
        super("toggle_sneak", "Toggle Sneak", "Schleich-Taste schaltet Schleichen dauerhaft an/aus", Category.PVP, false);
    }

    public boolean isSneakForced() {
        if (!isEnabled() || !toggled) return false;
        Minecraft mc = Minecraft.getInstance();
        return !(disableWhileFlying.isOn() && mc.player != null && mc.player.getAbilities().flying);
    }

    @Override
    protected void onDisable() { toggled = false; }

    @Override
    public void onTick(Minecraft mc) {
        while (mc.options.keyShift.consumeClick()) toggled = !toggled;
    }
}
