package dev.velora.client.mods.pvp;

import dev.velora.client.mixin.OverlayTextureAccessor;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.ColorSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Changes the color of the red damage tint on entities by rewriting the "hurt" half of
 * Minecraft's 16x16 overlay texture (rows 0–7). Restores the vanilla color when disabled.
 */
public final class HitColorMod extends Module {
    /** Vanilla value from OverlayTexture: 0xB2FF0000. */
    private static final int VANILLA = 0xB2FF0000;

    private final ColorSetting color = add(new ColorSetting("color", "Trefferfarbe", "Alpha = Stärke der Färbung", 0x9C7C5CFF));

    public HitColorMod() {
        super("hit_color", "Hit Color", "Eigene Farbe für das Treffer-Aufleuchten", Category.PVP, false);
        color.onChange(c -> { if (isEnabled()) apply(c); });
    }

    @Override protected void onEnable() { apply(color.getArgb()); }

    @Override protected void onDisable() { apply(VANILLA); }

    private static void apply(int argb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return;
        Runnable task = () -> {
            NativeImageBackedTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.getOverlayTexture()).velora$getTexture();
            NativeImage image = texture.getImage();
            if (image == null) return;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) image.setColorArgb(x, y, argb);
            }
            texture.upload();
        };
        if (mc.isOnThread()) task.run();
        else mc.execute(task);
    }
}
