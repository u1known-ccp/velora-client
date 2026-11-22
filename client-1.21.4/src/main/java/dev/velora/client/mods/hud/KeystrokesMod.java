package dev.velora.client.mods.hud;

import dev.velora.client.hud.HudMod;
import dev.velora.client.input.ClickTracker;
import dev.velora.client.render.Render2D;
import dev.velora.core.hud.Anchor;
import dev.velora.core.module.Category;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/** WASD / mouse / space keystrokes with smooth press animation and CPS on the mouse buttons. */
public final class KeystrokesMod extends HudMod {
    private static final int GAP = 2;

    private final NumberSetting keySize = add(new NumberSetting("key_size", "Tastengröße", "", 22, 16, 32, 1, "px"));
    private final BooleanSetting showMouse = add(new BooleanSetting("show_mouse", "Maustasten", "", true));
    private final BooleanSetting showCps = add(new BooleanSetting("show_cps", "CPS auf Maustasten", "", true));
    private final BooleanSetting showSpace = add(new BooleanSetting("show_space", "Leertaste", "", true));
    private final ColorSetting keyColor = add(new ColorSetting("key_color", "Tastenfarbe", "", 0x780E0F13));
    private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Gedrückt-Farbe", "", 0xE0FFFFFF));
    private final ColorSetting pressedText = add(new ColorSetting("pressed_text", "Gedrückt-Textfarbe", "", 0xFF0E0F13));

    private final Animation[] anims = new Animation[7];

    public KeystrokesMod() {
        super("keystrokes", "Keystrokes", "Zeigt gedrückte Bewegungs- und Maustasten", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -4);
        background.set(false);
        for (int i = 0; i < anims.length; i++) anims[i] = new Animation(90, Easing.EASE_OUT_CUBIC, 0);
    }

    @Override
    public int getWidth(boolean preview) {
        return keySize.getInt() * 3 + GAP * 2;
    }

    @Override
    public int getHeight(boolean preview) {
        int k = keySize.getInt();
        int h = k * 2 + GAP;
        if (showMouse.isOn()) h += GAP + k;
        if (showSpace.isOn()) h += GAP + Math.max(8, k / 2);
        return h;
    }

    @Override
    protected void renderContent(DrawContext ctx, float tickDelta, boolean preview) {
        var o = MC.options;
        int k = keySize.getInt();
        int y = 0;
        key(ctx, 0, o.forwardKey, "W", k + GAP, y, k, k);
        y += k + GAP;
        key(ctx, 1, o.leftKey, "A", 0, y, k, k);
        key(ctx, 2, o.backKey, "S", k + GAP, y, k, k);
        key(ctx, 3, o.rightKey, "D", (k + GAP) * 2, y, k, k);
        y += k + GAP;
        if (showMouse.isOn()) {
            int w = (getWidth(preview) - GAP) / 2;
            String l = showCps.isOn() ? "LMB " + ClickTracker.leftCps() : "LMB";
            String r = showCps.isOn() ? "RMB " + ClickTracker.rightCps() : "RMB";
            key(ctx, 4, o.attackKey, l, 0, y, w, k);
            key(ctx, 5, o.useKey, r, w + GAP, y, getWidth(preview) - w - GAP, k);
            y += k + GAP;
        }
        if (showSpace.isOn()) {
            int h = Math.max(8, k / 2);
            key(ctx, 6, o.jumpKey, "", 0, y, getWidth(preview), h);
            int barW = getWidth(preview) / 3;
            int barColor = ColorUtil.lerp(text(), faded(pressedText.getRenderColor()), anims[6].getFloat());
            Render2D.rect(ctx, (getWidth(preview) - barW) / 2f, y + h / 2f - 0.5f, barW, 1, barColor);
        }
    }

    private void key(DrawContext ctx, int index, KeyBinding binding, String label, int x, int y, int w, int h) {
        Animation a = anims[index];
        a.animateTo(binding.isPressed() ? 1 : 0);
        float t = a.getFloat();
        int bg = ColorUtil.lerp(faded(keyColor.getRenderColor()), faded(pressedColor.getRenderColor()), t);
        Render2D.roundedRect(ctx, x, y, w, h, cornerRadius.getFloat(), bg);
        if (!label.isEmpty()) {
            int color = ColorUtil.lerp(text(), faded(pressedText.getRenderColor()), t);
            int tw = textWidth(label);
            ctx.drawText(MC.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, color, textShadow.isOn() && t < 0.5f);
        }
    }
}
