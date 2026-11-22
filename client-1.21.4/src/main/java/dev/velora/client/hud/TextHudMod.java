package dev.velora.client.hud;

import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Single line "Label: Value" HUD element. The value is cached and only recomputed every
 * {@link #refreshIntervalMs()} – most values do not need per-frame updates.
 */
public abstract class TextHudMod extends HudMod {
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;

    protected final BooleanSetting showLabel;
    protected final BooleanSetting brackets;
    protected final ColorSetting labelColor;

    private String cachedValue = "";
    private long lastRefresh;

    protected TextHudMod(String id, String name, String description, Category category, boolean enabledByDefault,
                         Anchor anchor, float offsetX, float offsetY) {
        super(id, name, description, category, enabledByDefault, anchor, offsetX, offsetY);
        showLabel = add(new BooleanSetting("show_label", "Bezeichnung anzeigen", "", true));
        brackets = add(new BooleanSetting("brackets", "Klammern", "Text in [eckige Klammern] setzen", false));
        labelColor = add(new ColorSetting("label_color", "Bezeichnungsfarbe", "", 0xFFB9A8FF));
        labelColor.visibleWhen(showLabel::isOn);
    }

    /** Label shown before the value, e.g. "FPS". Empty for none. */
    protected abstract String label();

    /** Current value text. In preview mode return realistic sample data if no live data exists. */
    protected abstract String value(boolean preview);

    protected long refreshIntervalMs() { return 100; }

    /** Label shown after the value instead of before it (e.g. "120 FPS"). */
    protected boolean labelAfterValue() { return false; }

    private String currentValue(boolean preview) {
        long now = System.currentTimeMillis();
        if (preview || now - lastRefresh >= refreshIntervalMs()) {
            String v;
            try {
                v = value(preview);
            } catch (RuntimeException e) {
                v = "?";
            }
            cachedValue = v == null ? "" : v;
            lastRefresh = preview ? 0 : now;
        }
        return cachedValue;
    }

    private String labelPart() {
        String l = label();
        if (!showLabel.isOn() || l.isEmpty()) return "";
        return labelAfterValue() ? " " + l : l + ": ";
    }

    @Override
    public int getWidth(boolean preview) {
        String text = labelPart() + currentValue(preview);
        if (brackets.isOn()) text = "[" + text + "]";
        return textWidth(text) + PADDING_X * 2;
    }

    @Override
    public int getHeight(boolean preview) {
        return MC.textRenderer.fontHeight - 1 + PADDING_Y * 2;
    }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        String label = labelPart();
        String value = currentValue(preview);
        int x = PADDING_X;
        int y = PADDING_Y;
        int textColor = text();
        int lColor = faded(labelColor.getRenderColor());
        if (brackets.isOn()) {
            drawString(ctx, "[", x, y, textColor);
            x += textWidth("[");
        }
        if (labelAfterValue()) {
            drawString(ctx, value, x, y, textColor);
            x += textWidth(value);
            if (!label.isEmpty()) {
                drawString(ctx, label, x, y, lColor);
                x += textWidth(label);
            }
        } else {
            if (!label.isEmpty()) {
                drawString(ctx, label, x, y, lColor);
                x += textWidth(label);
            }
            drawString(ctx, value, x, y, textColor);
            x += textWidth(value);
        }
        if (brackets.isOn()) drawString(ctx, "]", x, y, textColor);
    }
}
