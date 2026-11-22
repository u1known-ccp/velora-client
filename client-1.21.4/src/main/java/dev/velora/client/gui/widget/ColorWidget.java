package dev.velora.client.gui.widget;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/** Color row with an expandable HSB picker, alpha slider and chroma toggle. */
public final class ColorWidget extends SettingWidget {
    private static final int PICKER_H = 74;
    private static final int SB_W = 96;
    private static final int BAR_W = 10;

    private final ColorSetting s;
    private final Toggle chromaToggle;
    private boolean expanded;
    private float hue, sat, bri;
    private int dragging; // 0 none, 1 sb, 2 hue, 3 alpha

    public ColorWidget(ColorSetting s) {
        super(s);
        this.s = s;
        this.chromaToggle = new Toggle(s.isChroma());
        syncFromSetting();
    }

    private void syncFromSetting() {
        float[] hsb = ColorUtil.toHsb(s.getArgb());
        hue = hsb[0];
        sat = hsb[1];
        bri = hsb[2];
    }

    private int baseHeight() { return super.getHeight(); }

    @Override
    public int getHeight() { return baseHeight() + (expanded ? PICKER_H : 0); }

    @Override protected int controlWidth() { return 40; }

    @Override
    protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
        int sx = x + width - 30;
        int sy = controlCenterY() - 6;
        // checkerboard for alpha
        Render2D.roundedRect(ctx, sx, sy, 22, 12, Theme.RADIUS_SMALL, 0xFF808080);
        Render2D.rect(ctx, sx + 11, sy, 11, 6, 0xFFC0C0C0);
        Render2D.rect(ctx, sx, sy + 6, 11, 6, 0xFFC0C0C0);
        Render2D.roundedRectBordered(ctx, sx, sy, 22, 12, Theme.RADIUS_SMALL, s.getRenderColor(), Theme.BORDER, 1);
        if (!expanded) return;

        int px = x + 8;
        int py = y + baseHeight();
        // saturation / brightness box: columns of vertical gradients
        for (int i = 0; i < SB_W; i++) {
            float sa = i / (float) (SB_W - 1);
            int top = ColorUtil.hsb(hue, sa, 1f);
            ctx.fillGradient(px + i, py, px + i + 1, py + 64, top, 0xFF000000);
        }
        float cx = px + sat * (SB_W - 1);
        float cy = py + (1 - bri) * 63;
        Render2D.roundedRectBordered(ctx, cx - 3, cy - 3, 6, 6, 3, 0, 0xFFFFFFFF, 1);

        // hue bar
        int hx = px + SB_W + 6;
        for (int i = 0; i < 64; i++) {
            Render2D.rect(ctx, hx, py + i, BAR_W, 1, ColorUtil.hsb(i / 63f, 1, 1));
        }
        Render2D.rect(ctx, hx - 1, py + hue * 63 - 1, BAR_W + 2, 2, 0xFFFFFFFF);

        // alpha bar
        if (s.allowsAlpha()) {
            int ax = hx + BAR_W + 6;
            int opaque = s.getArgb() | 0xFF000000;
            ctx.fillGradient(ax, py, ax + BAR_W, py + 64, opaque, ColorUtil.withAlpha(opaque, 0));
            float a = ColorUtil.alpha(s.getArgb()) / 255f;
            Render2D.rect(ctx, ax - 1, py + (1 - a) * 63 - 1, BAR_W + 2, 2, 0xFFFFFFFF);
        }

        // chroma + hex
        int infoX = hx + BAR_W * 2 + 18;
        Render2D.text(ctx, "Regenbogen", infoX, py + 2, Theme.TEXT_DIM, false);
        chromaToggle.render(ctx, infoX, py + 14, s.isChroma());
        Render2D.text(ctx, String.format("#%08X", s.getArgb()), infoX, py + 34, Theme.TEXT_MUTED, false);
        Render2D.text(ctx, "Rechtsklick: zurücksetzen", infoX, py + 48, Theme.TEXT_MUTED, 0.75f, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isHovered(mx, my)) return false;
        if (my < y + baseHeight()) {
            if (button == 1) {
                s.reset();
                s.setChroma(false);
                syncFromSetting();
            } else {
                expanded = !expanded;
                if (expanded) syncFromSetting();
            }
            return true;
        }
        int px = x + 8;
        int py = y + baseHeight();
        int hx = px + SB_W + 6;
        int ax = hx + BAR_W + 6;
        int infoX = hx + BAR_W * 2 + 18;
        if (button == 1) {
            s.reset();
            syncFromSetting();
            return true;
        }
        if (mx >= px && mx < px + SB_W && my >= py && my < py + 64) dragging = 1;
        else if (mx >= hx && mx < hx + BAR_W && my >= py && my < py + 64) dragging = 2;
        else if (s.allowsAlpha() && mx >= ax && mx < ax + BAR_W && my >= py && my < py + 64) dragging = 3;
        else if (mx >= infoX && mx < infoX + Toggle.WIDTH && my >= py + 14 && my < py + 14 + Toggle.HEIGHT) {
            s.setChroma(!s.isChroma());
            return true;
        }
        mouseDragged(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (dragging == 0) return false;
        int px = x + 8;
        int py = y + baseHeight();
        float fy = (float) Math.max(0, Math.min(1, (my - py) / 63.0));
        int alpha = ColorUtil.alpha(s.getArgb());
        switch (dragging) {
            case 1 -> {
                sat = (float) Math.max(0, Math.min(1, (mx - px) / (SB_W - 1.0)));
                bri = 1 - fy;
            }
            case 2 -> hue = fy;
            case 3 -> alpha = Math.round((1 - fy) * 255);
            default -> { }
        }
        s.set(ColorUtil.withAlpha(ColorUtil.hsb(hue, sat, bri), s.allowsAlpha() ? alpha : 255));
        return true;
    }

    @Override
    public void mouseReleased(double mx, double my, int button) { dragging = 0; }
}
