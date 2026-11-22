package dev.velora.client.hud;

import dev.velora.client.render.Render2D;
import dev.velora.core.hud.Anchor;
import dev.velora.core.hud.HudModule;
import dev.velora.core.module.Category;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Client side HUD element. Subclasses only describe their size and draw their content in local
 * coordinates; positioning, scaling, background and opacity are handled here.
 */
public abstract class HudMod extends HudModule {
    protected static final MinecraftClient MC = MinecraftClient.getInstance();
    protected final NumberSetting cornerRadius;

    protected HudMod(String id, String name, String description, Category category, boolean enabledByDefault,
                     Anchor anchor, float offsetX, float offsetY) {
        super(id, name, description, category, enabledByDefault, anchor, offsetX, offsetY);
        cornerRadius = add(new NumberSetting("corner_radius", "Eckenradius", "", 3, 0, 8, 1, "px"));
        cornerRadius.visibleWhen(background::isOn);
    }

    /** Unscaled width in GUI pixels. {@code preview} is true inside the HUD editor (show sample data). */
    public abstract int getWidth(boolean preview);

    public abstract int getHeight(boolean preview);

    /** Draws the content at (0,0) in local, unscaled coordinates. */
    protected abstract void renderContent(DrawContext ctx, float tickDelta, boolean preview);

    /** Whether there is anything to show right now (e.g. no potion effects → hidden). */
    public boolean hasContent() { return true; }

    /** Requires a world/player to render (most elements). */
    public boolean requiresWorld() { return true; }

    public final void render(DrawContext ctx, float tickDelta, boolean preview) {
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float scale = getPosition().getScale();
        int w = getWidth(preview);
        int h = getHeight(preview);
        if (w <= 0 || h <= 0) return;
        float x = getPosition().resolveX(sw, w * scale);
        float y = getPosition().resolveY(sh, h * scale);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        if (background.isOn()) {
            Render2D.roundedRect(ctx, 0, 0, w, h, cornerRadius.getFloat(), ColorUtil.fade(backgroundColor.getRenderColor(), getOpacity()));
        }
        renderContent(ctx, tickDelta, preview);
        m.pop();
    }

    /** Screen-space bounds {x, y, w, h} (scaled) for the HUD editor. */
    public final float[] bounds(int screenWidth, int screenHeight, boolean preview) {
        float scale = getPosition().getScale();
        float w = Math.max(1, getWidth(preview)) * scale;
        float h = Math.max(1, getHeight(preview)) * scale;
        return new float[]{getPosition().resolveX(screenWidth, w), getPosition().resolveY(screenHeight, h), w, h};
    }

    /** Text color including the element opacity. */
    protected int text() { return ColorUtil.fade(textColor.getRenderColor(), getOpacity()); }

    protected int faded(int argb) { return ColorUtil.fade(argb, getOpacity()); }

    protected void drawString(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(MC.textRenderer, s, x, y, color, textShadow.isOn());
    }

    protected static int textWidth(String s) { return MC.textRenderer.getWidth(s); }
}
