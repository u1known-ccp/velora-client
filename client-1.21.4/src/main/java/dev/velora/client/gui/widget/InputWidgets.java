package dev.velora.client.gui.widget;

import dev.velora.client.render.Render2D;
import dev.velora.client.render.Theme;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Keybind capture and single-line text input rows. */
public final class InputWidgets {
    private InputWidgets() { }

    public static String keyName(int code) {
        if (code == KeybindSetting.NONE) return "Keine";
        if (code >= KeybindSetting.MOUSE_OFFSET) {
            return InputUtil.Type.MOUSE.createFromCode(code - KeybindSetting.MOUSE_OFFSET).getLocalizedText().getString();
        }
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    public static final class KeybindWidget extends SettingWidget {
        private final KeybindSetting s;
        private boolean listening;

        public KeybindWidget(KeybindSetting s) {
            super(s);
            this.s = s;
        }

        private int bw() { return Math.max(60, Render2D.width(label()) + 16); }

        private String label() { return listening ? "Taste drücken…" : keyName(s.getKey()); }

        @Override protected int controlWidth() { return bw(); }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            int bw = bw();
            int bx = x + width - bw - 8;
            int by = controlCenterY() - 8;
            Render2D.roundedRectBordered(ctx, bx, by, bw, 16, Theme.RADIUS_SMALL, Theme.SIDEBAR,
                    listening ? Theme.accent() : Theme.BORDER, 1);
            Render2D.centeredText(ctx, label(), bx + bw / 2f, by + 4, listening ? Theme.accent() : Theme.TEXT, 1f, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (listening) {
                // left/right click are not allowed as binds; any other mouse button binds
                if (button > 1) s.set(KeybindSetting.MOUSE_OFFSET + button);
                listening = false;
                return true;
            }
            if (button == 0 && isHovered(mx, my)) {
                listening = true;
                return true;
            }
            if (button == 1 && isHovered(mx, my)) {
                s.set(KeybindSetting.NONE);
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!listening) return false;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                s.set(KeybindSetting.NONE);
                listening = false;
            } else {
                s.set(key);
                listening = false;
            }
            return true;
        }

        @Override public boolean isCapturingKeyboard() { return listening; }

        @Override public void unfocus() { listening = false; }
    }

    public static final class TextWidget extends SettingWidget {
        private static final int BOX = 150;
        private final TextSetting s;
        private boolean focused;
        private StringBuilder buffer;

        public TextWidget(TextSetting s) {
            super(s);
            this.s = s;
        }

        @Override protected int controlWidth() { return BOX; }

        private int boxX() { return x + width - BOX - 8; }

        @Override
        protected void renderControl(DrawContext ctx, int mx, int my, float delta) {
            int bx = boxX();
            int by = controlCenterY() - 8;
            Render2D.roundedRectBordered(ctx, bx, by, BOX, 16, Theme.RADIUS_SMALL, Theme.SIDEBAR,
                    focused ? Theme.accent() : Theme.BORDER, 1);
            String text = focused ? buffer.toString() : s.get();
            int max = BOX - 10;
            // show the end of the text while typing
            String visible = text;
            while (Render2D.width(visible) > max && !visible.isEmpty()) visible = focused ? visible.substring(1) : visible.substring(0, visible.length() - 1);
            if (!focused && !visible.equals(text)) visible = Render2D.ellipsize(text, max);
            if (text.isEmpty() && !focused) {
                Render2D.text(ctx, "leer", bx + 5, by + 4, Theme.TEXT_MUTED, false);
            } else {
                Render2D.text(ctx, visible, bx + 5, by + 4, Theme.TEXT, false);
            }
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = bx + 5 + Render2D.width(visible);
                Render2D.rect(ctx, cx, by + 3, 1, 10, Theme.accent());
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            boolean inside = isHovered(mx, my) && mx >= boxX();
            if (inside && !focused) {
                focused = true;
                buffer = new StringBuilder(s.get());
                return true;
            }
            if (!inside && focused) commit();
            return inside;
        }

        private void commit() {
            if (!focused) return;
            focused = false;
            s.set(buffer.toString());
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!focused) return false;
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> commit();
                case GLFW.GLFW_KEY_ESCAPE -> focused = false;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!buffer.isEmpty()) {
                        if (Screen.hasControlDown()) buffer.setLength(0);
                        else buffer.setLength(buffer.length() - 1);
                    }
                }
                default -> {
                    if (Screen.isPaste(key)) {
                        String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                        insert(clip);
                    } else if (Screen.isSelectAll(key)) {
                        buffer.setLength(0);
                    }
                }
            }
            return true;
        }

        private void insert(String text) {
            for (char c : text.toCharArray()) {
                if (buffer.length() >= s.getMaxLength()) break;
                if (c >= 0x20 && c != 0x7F && c != '§') buffer.append(c);
            }
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (!focused) return false;
            insert(String.valueOf(chr));
            return true;
        }

        @Override public boolean isCapturingKeyboard() { return focused; }

        @Override public void unfocus() { commit(); }
    }
}
