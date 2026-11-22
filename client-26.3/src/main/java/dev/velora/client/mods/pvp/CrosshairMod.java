package dev.velora.client.mods.pvp;

import dev.velora.client.render.Render2D;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

/** Fully custom crosshair (replaces the vanilla one while enabled). Also covers "Custom Crosshair" in Visuals. */
public final class CrosshairMod extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Form", "", "Kreuz", "Kreuz", "Kreuz + Punkt", "Punkt", "Kreis", "T-Form"));
    private final NumberSetting size = add(new NumberSetting("size", "Länge", "", 5, 1, 15, 0.5, "px"));
    private final NumberSetting gap = add(new NumberSetting("gap", "Lücke", "", 2, 0, 10, 0.5, "px"));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Dicke", "", 1, 0.5, 4, 0.5, "px"));
    private final ColorSetting color = add(new ColorSetting("color", "Farbe", "", 0xFFFFFFFF));
    private final BooleanSetting outline = add(new BooleanSetting("outline", "Umrandung", "", true));
    private final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Umrandungsfarbe", "", 0xC0000000));
    private final BooleanSetting targetColorEnabled = add(new BooleanSetting("target_color_enabled", "Farbe bei Ziel", "Andere Farbe wenn ein Lebewesen anvisiert wird", true));
    private final ColorSetting targetColor = add(new ColorSetting("target_color", "Zielfarbe", "", 0xFFFF5C7A));
    private final BooleanSetting showInThirdPerson = add(new BooleanSetting("third_person", "In 3rd Person anzeigen", "", false));

    public CrosshairMod() {
        super("crosshair", "Crosshair Customizer", "Eigenes Fadenkreuz: Form, Größe, Lücke, Farbe", Category.PVP, false);
        outlineColor.visibleWhen(outline::isOn);
        targetColor.visibleWhen(targetColorEnabled::isOn);
    }

    /** Whether the vanilla crosshair should be suppressed right now. */
    public boolean replacesVanilla(Minecraft mc) {
        return isEnabled() && !mc.gui.hud.getDebugOverlay().showDebugScreen();
    }

    public void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        if (!mc.options.getCameraType().isFirstPerson() && !showInThirdPerson.isOn()) return;
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        float cx = ctx.guiWidth() / 2f;
        float cy = ctx.guiHeight() / 2f;
        boolean target = targetColorEnabled.isOn() && mc.crosshairPickEntity instanceof LivingEntity;
        int c = target ? targetColor.getRenderColor() : color.getRenderColor();
        float t = thickness.getFloat();
        float len = size.getFloat();
        float g = gap.getFloat();

        switch (style.get()) {
            case "Punkt" -> dot(ctx, cx, cy, Math.max(t, 1.5f), c);
            case "Kreis" -> ring(ctx, cx, cy, g + len / 2f, t, c);
            case "T-Form" -> {
                arm(ctx, cx - g - len, cy - t / 2, len, t, c);
                arm(ctx, cx + g, cy - t / 2, len, t, c);
                arm(ctx, cx - t / 2, cy + g, t, len, c);
            }
            default -> {
                arm(ctx, cx - g - len, cy - t / 2, len, t, c);
                arm(ctx, cx + g, cy - t / 2, len, t, c);
                arm(ctx, cx - t / 2, cy - g - len, t, len, c);
                arm(ctx, cx - t / 2, cy + g, t, len, c);
                if (style.is("Kreuz + Punkt")) dot(ctx, cx, cy, t, c);
            }
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
            float progress = mc.player.getAttackStrengthScale(0f);
            if (progress < 1f) {
                float w = 16;
                Render2D.rect(ctx, cx - w / 2, cy + g + len + 4, w, 2, 0x80000000);
                Render2D.rect(ctx, cx - w / 2, cy + g + len + 4, w * progress, 2, c);
            }
        }
    }

    private void arm(GuiGraphicsExtractor ctx, float x, float y, float w, float h, int c) {
        if (outline.isOn()) Render2D.rect(ctx, x - 0.5f, y - 0.5f, w + 1, h + 1, outlineColor.getRenderColor());
        Render2D.rect(ctx, x, y, w, h, c);
    }

    private void dot(GuiGraphicsExtractor ctx, float cx, float cy, float size, int c) {
        if (outline.isOn()) Render2D.rect(ctx, cx - size / 2 - 0.5f, cy - size / 2 - 0.5f, size + 1, size + 1, outlineColor.getRenderColor());
        Render2D.rect(ctx, cx - size / 2, cy - size / 2, size, size, c);
    }

    private void ring(GuiGraphicsExtractor ctx, float cx, float cy, float r, float t, int c) {
        if (outline.isOn()) {
            Render2D.roundedRectBordered(ctx, cx - r - 0.5f, cy - r - 0.5f, (r + 0.5f) * 2, (r + 0.5f) * 2, r + 0.5f, 0, outlineColor.getRenderColor(), t + 1);
        }
        Render2D.roundedRectBordered(ctx, cx - r, cy - r, r * 2, r * 2, r, 0, c, t);
    }
}
