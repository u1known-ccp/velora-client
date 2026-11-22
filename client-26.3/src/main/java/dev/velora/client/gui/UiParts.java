package dev.velora.client.gui;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.util.Animation;
import dev.velora.core.util.ColorUtil;
import dev.velora.core.util.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;

/** Small reusable UI pieces for Velora screens. */
public final class UiParts {
    private UiParts() { }

    // ------------------------------------------------------------------ 26.3 input helpers (SDL3)

    /** Ctrl (or Cmd) held – modifier bits as used by {@code InputWithModifiers}. */
    public static boolean ctrl(int modifiers) { return (modifiers & 192) != 0; }

    public static boolean isPaste(int key, int modifiers) { return ctrl(modifiers) && key == InputConstants.KEY_V; }

    public static boolean isSelectAll(int key, int modifiers) { return ctrl(modifiers) && key == InputConstants.KEY_A; }

    /** SDL button (1 = left, 2 = middle, 3 = right, 4+ = side) to the classic 0 = left, 1 = right, 2 = middle numbering. */
    public static int legacyButton(int sdl) {
        return switch (sdl) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> sdl - 1; };
    }

    public static int sdlButton(int legacy) {
        return switch (legacy) { case 0 -> 1; case 1 -> 3; case 2 -> 2; default -> legacy + 1; };
    }

    /** Self drawn button with hover animation. */
    public static final class Button {
        public int x, y, w, h;
        public String label;
        public boolean primary;
        public boolean enabled = true;
        private final Runnable action;
        private final Animation hover = new Animation(Theme.duration(120), Easing.EASE_OUT_CUBIC, 0);

        public Button(String label, boolean primary, Runnable action) {
            this.label = label;
            this.primary = primary;
            this.action = action;
        }

        public Button at(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public boolean hovered(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public void render(GuiGraphicsExtractor ctx, int mx, int my) {
            hover.animateTo(enabled && hovered(mx, my) ? 1 : 0);
            float t = hover.getFloat();
            int bg;
            int fg;
            if (!enabled) {
                bg = Theme.SURFACE;
                fg = Theme.TEXT_MUTED;
            } else if (primary) {
                bg = ColorUtil.lerp(Theme.accent(), ColorUtil.brighten(Theme.accent(), 0.18f), t);
                fg = 0xFFFFFFFF;
            } else {
                bg = ColorUtil.lerp(Theme.SURFACE, Theme.SURFACE_ACTIVE, t);
                fg = Theme.TEXT;
            }
            Render2D.roundedRect(ctx, x, y, w, h, Theme.RADIUS, bg);
            Render2D.centeredText(ctx, Render2D.ellipsize(label, w - 8), x + w / 2f, y + (h - 8) / 2f, fg, 1f, false);
        }

        public boolean click(double mx, double my, int button) {
            if (button == 0 && enabled && hovered(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                action.run();
                return true;
            }
            return false;
        }
    }

    /** Single line text input (search field, profile names). */
    public static final class TextField {
        public int x, y, w, h;
        public String placeholder;
        private final StringBuilder text = new StringBuilder();
        private final int maxLength;
        public boolean focused;

        public TextField(String placeholder, int maxLength) {
            this.placeholder = placeholder;
            this.maxLength = maxLength;
        }

        public String get() { return text.toString(); }

        public void set(String value) {
            text.setLength(0);
            text.append(value == null ? "" : value);
        }

        public TextField at(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public void render(GuiGraphicsExtractor ctx, String icon) {
            Render2D.roundedRectBordered(ctx, x, y, w, h, Theme.RADIUS, Theme.SURFACE, focused ? Theme.accent() : Theme.BORDER, 1);
            int tx = x + 6;
            if (icon != null) {
                Render2D.text(ctx, icon, tx, y + (h - 8) / 2f, Theme.TEXT_MUTED, false);
                tx += Render2D.width(icon) + 4;
            }
            String visible = get();
            int max = x + w - 6 - tx;
            while (Render2D.width(visible) > max && !visible.isEmpty()) visible = visible.substring(1);
            if (visible.isEmpty() && !focused) {
                Render2D.text(ctx, placeholder, tx, y + (h - 8) / 2f, Theme.TEXT_MUTED, false);
            } else {
                Render2D.text(ctx, visible, tx, y + (h - 8) / 2f, Theme.TEXT, false);
            }
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.rect(ctx, tx + Render2D.width(visible), y + (h - 10) / 2f, 1, 10, Theme.accent());
            }
        }

        public boolean click(double mx, double my) {
            focused = mx >= x && mx < x + w && my >= y && my < y + h;
            return focused;
        }

        /** @return true if the key was consumed */
        public boolean key(int key, int modifiers) {
            if (!focused) return false;
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!text.isEmpty()) {
                    if (ctrl(modifiers)) text.setLength(0);
                    else text.setLength(text.length() - 1);
                }
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                focused = false;
                return true;
            }
            if (isPaste(key, modifiers)) {
                type(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            return true;
        }

        public boolean type(String s) {
            if (!focused) return false;
            for (char c : s.toCharArray()) {
                if (text.length() >= maxLength) break;
                if (c >= 0x20 && c != 0x7F && c != '§') text.append(c);
            }
            return true;
        }
    }

    /** Transient message at the bottom of a screen. */
    public static final class Toast {
        private String message;
        private int color;
        private long shownAt;

        public void show(String message, int color) {
            this.message = message;
            this.color = color;
            this.shownAt = System.currentTimeMillis();
        }

        public void render(GuiGraphicsExtractor ctx, int centerX, int y) {
            if (message == null) return;
            long age = System.currentTimeMillis() - shownAt;
            if (age > 2800) {
                message = null;
                return;
            }
            float alpha = age < 150 ? age / 150f : age > 2400 ? (2800 - age) / 400f : 1f;
            int w = Render2D.width(message) + 20;
            Render2D.roundedRect(ctx, centerX - w / 2f, y, w, 18, 9, ColorUtil.fade(0xF0181B22, alpha));
            Render2D.rect(ctx, centerX - w / 2f + 8, y + 7, 4, 4, ColorUtil.fade(color, alpha));
            Render2D.text(ctx, message, centerX - w / 2f + 16, y + 5, ColorUtil.fade(Theme.TEXT, Math.max(0.05f, alpha)), false);
        }
    }
}
