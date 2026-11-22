package dev.velora.client.gui.widget;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.setting.Setting;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Lightweight, self-drawn row widget for one setting. Rows share a common look:
 * name (+ optional description) on the left, the control on the right.
 */
public abstract class SettingWidget {
    public static final int ROW_HEIGHT = 24;
    public static final int ROW_HEIGHT_DESC = 30;

    protected final Setting<?> setting;
    public int x;
    public int y;
    public int width;
    private final Animation hover = new Animation(Theme.duration(120), Easing.EASE_OUT_CUBIC, 0);

    protected SettingWidget(Setting<?> setting) {
        this.setting = setting;
    }

    public Setting<?> getSetting() { return setting; }

    public int getHeight() {
        return setting.getDescription().isEmpty() ? ROW_HEIGHT : ROW_HEIGHT_DESC;
    }

    public final void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        hover.animateTo(isHovered(mx, my) ? 1 : 0);
        int bg = ColorUtil.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.getFloat());
        Render2D.roundedRect(ctx, x, y, width, getHeight(), Theme.RADIUS, bg);
        int rowH = getHeight();
        boolean hasDesc = !setting.getDescription().isEmpty();
        int labelY = hasDesc ? y + 6 : y + (rowH - 8) / 2;
        int maxLabel = Math.max(40, width - controlWidth() - 24);
        Render2D.text(ctx, Render2D.ellipsize(setting.getName(), maxLabel), x + 8, labelY, Theme.TEXT, false);
        if (hasDesc) {
            Render2D.text(ctx, Render2D.ellipsize(setting.getDescription(), (int) (maxLabel / 0.75f)), x + 8, labelY + 11, Theme.TEXT_MUTED, 0.75f, false);
        }
        renderControl(ctx, mx, my, delta);
    }

    /** Approximate width of the control on the right (for label truncation). */
    protected int controlWidth() { return 60; }

    protected abstract void renderControl(GuiGraphicsExtractor ctx, int mx, int my, float delta);

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + getHeight();
    }

    /** Extra height used by expanded widgets (e.g. color picker) – included in {@link #getHeight()}. */
    public boolean mouseClicked(double mx, double my, int button) { return false; }

    public boolean mouseDragged(double mx, double my, int button) { return false; }

    public void mouseReleased(double mx, double my, int button) { }

    public boolean keyPressed(int key, int scanCode, int modifiers) { return false; }

    public boolean charTyped(char chr, int modifiers) { return false; }

    /** While true the widget owns the keyboard (ESC does not close the screen). */
    public boolean isCapturingKeyboard() { return false; }

    /** Called when another widget gains focus. */
    public void unfocus() { }

    /** Vertical center of the first row for controls. */
    protected int controlCenterY() {
        return y + (setting.getDescription().isEmpty() ? ROW_HEIGHT : ROW_HEIGHT_DESC) / 2;
    }
}
