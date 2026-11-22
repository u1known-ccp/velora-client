package dev.velora.client.render;

import org.joml.Matrix3x2fStack;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 2D drawing helpers on top of {@link GuiGraphicsExtractor}. Rounded shapes are rasterised in physical pixels
 * (independent of the GUI scale) with one anti-aliased edge pixel per row, so corners stay smooth at any scale.
 * Everything goes through DrawContext's batched GUI buffers – no extra draw calls or shaders.
 */
public final class Render2D {
    private Render2D() { }

    private static double scaleFactor() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static void rect(GuiGraphicsExtractor ctx, float x, float y, float w, float h, int color) {
        if (ColorUtil.alpha(color) == 0 || w <= 0 || h <= 0) return;
        double s = scaleFactor();
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.scale((float) (1 / s), (float) (1 / s));
        ctx.fill((int) Math.round(x * s), (int) Math.round(y * s), (int) Math.round((x + w) * s), (int) Math.round((y + h) * s), color);
        m.popMatrix();
    }

    /** Filled rounded rectangle. */
    public static void roundedRect(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, int color) {
        if (ColorUtil.alpha(color) == 0 || w <= 0 || h <= 0) return;
        double s = scaleFactor();
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.scale((float) (1 / s), (float) (1 / s));
        int x1 = (int) Math.round(x * s);
        int y1 = (int) Math.round(y * s);
        int x2 = (int) Math.round((x + w) * s);
        int y2 = (int) Math.round((y + h) * s);
        int r = (int) Math.min(Math.round(radius * s), Math.min(x2 - x1, y2 - y1) / 2);
        fillRounded(ctx, x1, y1, x2, y2, r, color);
        m.popMatrix();
    }

    /** Rounded rectangle with a border; the border is drawn as a ring so translucent fills stay correct. */
    public static void roundedRectBordered(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, int fill, int border, float borderWidth) {
        if (w <= 0 || h <= 0) return;
        double s = scaleFactor();
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.scale((float) (1 / s), (float) (1 / s));
        int x1 = (int) Math.round(x * s);
        int y1 = (int) Math.round(y * s);
        int x2 = (int) Math.round((x + w) * s);
        int y2 = (int) Math.round((y + h) * s);
        int r = (int) Math.min(Math.round(radius * s), Math.min(x2 - x1, y2 - y1) / 2);
        int b = Math.max(1, (int) Math.round(borderWidth * s));
        if (ColorUtil.alpha(border) > 0) ringRounded(ctx, x1, y1, x2, y2, r, b, border);
        if (ColorUtil.alpha(fill) > 0) fillRounded(ctx, x1 + b, y1 + b, x2 - b, y2 - b, Math.max(0, r - b), fill);
        m.popMatrix();
    }

    /** Soft drop shadow made of a few expanding translucent rounded rects. */
    public static void shadow(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, float size, int color) {
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            float grow = size * i / layers;
            int c = ColorUtil.fade(color, 0.22f / i);
            roundedRect(ctx, x - grow, y - grow + size * 0.35f, w + grow * 2, h + grow * 2, radius + grow, c);
        }
    }

    public static void circle(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int color) {
        roundedRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void outline(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float thickness, int color) {
        rect(ctx, x, y, w, thickness, color);
        rect(ctx, x, y + h - thickness, w, thickness, color);
        rect(ctx, x, y + thickness, thickness, h - thickness * 2, color);
        rect(ctx, x + w - thickness, y + thickness, thickness, h - thickness * 2, color);
    }

    // ------------------------------------------------------------------ rasterisation (physical pixels)

    private static void fillRounded(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        if (r <= 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double inset = r - Math.sqrt(Math.max(0, (double) r * r - dy * dy));
            int full = (int) Math.ceil(inset);
            float coverage = (float) (full - inset);
            int top = y1 + i;
            int bottom = y2 - i - 1;
            ctx.fill(x1 + full, top, x2 - full, top + 1, color);
            ctx.fill(x1 + full, bottom, x2 - full, bottom + 1, color);
            if (full > 0 && coverage > 0.03f) {
                int edge = ColorUtil.fade(color, coverage);
                ctx.fill(x1 + full - 1, top, x1 + full, top + 1, edge);
                ctx.fill(x2 - full, top, x2 - full + 1, top + 1, edge);
                ctx.fill(x1 + full - 1, bottom, x1 + full, bottom + 1, edge);
                ctx.fill(x2 - full, bottom, x2 - full + 1, bottom + 1, edge);
            }
        }
    }

    private static void ringRounded(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int b, int color) {
        int ir = Math.max(0, r - b);
        int ix1 = x1 + b, iy1 = y1 + b, ix2 = x2 - b, iy2 = y2 - b;
        for (int y = y1; y < y2; y++) {
            int[] outer = rowSpan(x1, y1, x2, y2, r, y);
            if (outer == null) continue;
            int[] inner = (y >= iy1 && y < iy2) ? rowSpan(ix1, iy1, ix2, iy2, ir, y) : null;
            if (inner == null) {
                ctx.fill(outer[0], y, outer[1], y + 1, color);
            } else {
                if (inner[0] > outer[0]) ctx.fill(outer[0], y, inner[0], y + 1, color);
                if (outer[1] > inner[1]) ctx.fill(inner[1], y, outer[1], y + 1, color);
            }
        }
    }

    private static int[] rowSpan(int x1, int y1, int x2, int y2, int r, int y) {
        if (y < y1 || y >= y2) return null;
        int fromTop = y - y1;
        int fromBottom = y2 - 1 - y;
        int i = Math.min(fromTop, fromBottom);
        if (i >= r) return new int[]{x1, x2};
        double dy = r - i - 0.5;
        int inset = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
        return new int[]{x1 + inset, x2 - inset};
    }

    // ------------------------------------------------------------------ text

    public static void text(GuiGraphicsExtractor ctx, String text, float x, float y, int color, boolean shadow) {
        Font tr = Minecraft.getInstance().font;
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        ctx.text(tr, text, 0, 0, color, shadow);
        m.popMatrix();
    }

    public static void text(GuiGraphicsExtractor ctx, String text, float x, float y, int color, float scale, boolean shadow) {
        Font tr = Minecraft.getInstance().font;
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.text(tr, text, 0, 0, color, shadow);
        m.popMatrix();
    }

    public static void centeredText(GuiGraphicsExtractor ctx, String text, float cx, float y, int color, float scale, boolean shadow) {
        float w = width(text) * scale;
        text(ctx, text, cx - w / 2f, y, color, scale, shadow);
    }

    public static int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    public static int fontHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    /** Trims a string with an ellipsis so it fits {@code maxWidth}. */
    public static String ellipsize(String text, int maxWidth) {
        Font tr = Minecraft.getInstance().font;
        if (tr.width(text) <= maxWidth) return text;
        String dots = "…";
        int dotsW = tr.width(dots);
        return tr.plainSubstrByWidth(text, Math.max(0, maxWidth - dotsW)) + dots;
    }
}
