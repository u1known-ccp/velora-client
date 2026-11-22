package dev.velora.client.mods.utility;

import dev.velora.client.api.KeyListener;
import dev.velora.client.gui.VeloraTitleScreen;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ButtonSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.setting.TextSetting;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class UtilityMods {
    private UtilityMods() { }

    static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        MutableComponent prefix = Component.literal("[Velora] ").withStyle(ChatFormatting.LIGHT_PURPLE);
        mc.gui.hud.getChat().addClientSystemMessage(prefix.append(Component.literal(message).withStyle(ChatFormatting.GRAY)));
    }

    /** Up to six hotkeys that send predefined chat messages or commands. */
    public static final class AutoTextMod extends Module implements KeyListener {
        private static final int SLOTS = 6;
        private final KeybindSetting[] keys = new KeybindSetting[SLOTS];
        private final TextSetting[] texts = new TextSetting[SLOTS];
        private long lastSent;

        public AutoTextMod() {
            super("auto_text", "Auto Text", "Hotkeys für Nachrichten und Befehle", Category.UTILITY, false);
            for (int i = 0; i < SLOTS; i++) {
                keys[i] = add(new KeybindSetting("key_" + i, "Taste " + (i + 1), "", KeybindSetting.NONE));
                texts[i] = add(new TextSetting("text_" + i, "Text " + (i + 1), "Nachricht oder /befehl", i == 0 ? "/l" : "", 256));
            }
        }

        @Override
        public void onKey(int code, boolean pressed) {
            if (!pressed) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return;
            for (int i = 0; i < SLOTS; i++) {
                if (keys[i].getKey() != code) continue;
                String text = texts[i].get().trim();
                if (text.isEmpty()) return;
                long now = System.currentTimeMillis();
                if (now - lastSent < 750) return; // spam protection
                lastSent = now;
                if (text.startsWith("/")) mc.getConnection().sendCommand(text.substring(1));
                else mc.getConnection().sendChat(text);
                return;
            }
        }
    }

    /** Chat timestamps and longer chat history (hooks in ChatHudMixin). */
    public static final class ChatCustomizerMod extends Module {
        private final BooleanSetting timestamps = add(new BooleanSetting("timestamps", "Zeitstempel", "", true));
        private final ModeSetting format = add(new ModeSetting("format", "Zeitformat", "", "HH:mm", "HH:mm", "HH:mm:ss", "hh:mm a"));
        private final ColorSetting timestampColor = add(new ColorSetting("timestamp_color", "Zeitstempelfarbe", "", 0xFF8A90A6, false));
        private final NumberSetting history = add(new NumberSetting("history", "Chat-Verlauf", "Anzahl gespeicherter Nachrichten", 500, 100, 2000, 50));

        public ChatCustomizerMod() {
            super("chat_customizer", "Chat Customizer", "Zeitstempel und längerer Chat-Verlauf", Category.UTILITY, false);
            format.visibleWhen(timestamps::isOn);
            timestampColor.visibleWhen(timestamps::isOn);
        }

        public Component decorate(Component message) {
            if (!isEnabled() || !timestamps.isOn()) return message;
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(format.get(), Locale.GERMANY));
            int rgb = timestampColor.getArgb() & 0xFFFFFF;
            return Component.literal("[" + time + "] ").withStyle(s -> s.withColor(rgb)).append(message);
        }

        public int historyLimit() { return isEnabled() ? history.getInt() : 100; }
    }

    /** Copies the current coordinates to the clipboard. */
    public static final class CoordinatesClipboardMod extends Module implements KeyListener {
        private final KeybindSetting key = add(new KeybindSetting("key", "Taste", "", KeybindSetting.NONE));
        private final ModeSetting format = add(new ModeSetting("format", "Format", "", "X Y Z", "X Y Z", "X, Y, Z", "x: X y: Y z: Z"));

        public CoordinatesClipboardMod() {
            super("coords_clipboard", "Coordinates Clipboard", "Kopiert deine Koordinaten per Taste", Category.UTILITY, false);
        }

        @Override
        public void onKey(int code, boolean pressed) {
            if (!pressed || code != key.getKey()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
            String text = switch (format.get()) {
                case "X, Y, Z" -> x + ", " + y + ", " + z;
                case "x: X y: Y z: Z" -> "x: " + x + " y: " + y + " z: " + z;
                default -> x + " " + y + " " + z;
            };
            mc.keyboardHandler.setClipboard(text);
            feedback("Koordinaten kopiert: " + text);
        }
    }

    /** Connects to a configured server by hotkey or from the title screen. */
    public static final class QuickJoinMod extends Module implements KeyListener {
        public final TextSetting address = add(new TextSetting("address", "Server-Adresse", "", "localhost", 128));
        private final KeybindSetting key = add(new KeybindSetting("key", "Taste (im Spiel)", "Trennt und verbindet neu", KeybindSetting.NONE));

        public QuickJoinMod() {
            super("quick_join", "Quick Join", "Schnell auf deinen Lieblingsserver", Category.UTILITY, true);
        }

        @Override
        public void onKey(int code, boolean pressed) {
            if (pressed && code == key.getKey()) join(Minecraft.getInstance());
        }

        public void join(Minecraft mc) {
            String addr = address.get().trim();
            if (addr.isEmpty() || !ServerAddress.isValidAddress(addr)) {
                feedback("Ungültige Server-Adresse: " + addr);
                return;
            }
            if (mc.level != null) {
                mc.disconnectFromWorld(Component.literal("Quick Join"));
            }
            Screen parent = new VeloraTitleScreen();
            ServerData info = new ServerData("Quick Join", addr, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(addr), info, false, null);
        }
    }

    /** Screenshot helpers: open the screenshot folder, copy the path of the newest screenshot. */
    public static final class ScreenshotManagerMod extends Module implements KeyListener, dev.velora.client.api.TickListener {
        private final KeybindSetting openKey = add(new KeybindSetting("open_key", "Ordner öffnen (Taste)", "", KeybindSetting.NONE));
        private final BooleanSetting announce = add(new BooleanSetting("announce", "Pfad nach Screenshot kopieren", "Kopiert den Dateipfad in die Zwischenablage", true));

        public ScreenshotManagerMod() {
            super("screenshot_manager", "Screenshot Manager", "Screenshot-Ordner öffnen, Pfad kopieren", Category.UTILITY, false);
            add(new ButtonSetting("open", "Screenshot-Ordner", "", "Öffnen", ScreenshotManagerMod::openFolder));
            add(new ButtonSetting("copy_latest", "Neuester Screenshot", "Pfad in Zwischenablage", "Kopieren", () -> copyLatest(true)));
        }

        private static File folder() {
            return new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR);
        }

        static void openFolder() {
            File dir = folder();
            if (!dir.exists() && !dir.mkdirs()) return;
            com.mojang.blaze3d.Blaze3D.openPath(dir.toPath());
        }

        static void copyLatest(boolean feedback) {
            Path dir = folder().toPath();
            if (!Files.isDirectory(dir)) return;
            Path newest = null;
            long newestTime = Long.MIN_VALUE;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
                for (Path p : stream) {
                    long t = Files.getLastModifiedTime(p).toMillis();
                    if (t > newestTime) {
                        newestTime = t;
                        newest = p;
                    }
                }
            } catch (Exception e) {
                return;
            }
            if (newest != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(newest.toAbsolutePath().toString());
                if (feedback) feedback("Pfad kopiert: " + newest.getFileName());
            }
        }

        private int copyInTicks = -1;

        @Override
        public void onTick(Minecraft mc) {
            if (copyInTicks < 0) return;
            if (copyInTicks-- == 0) copyLatest(false);
        }

        @Override
        public void onKey(int code, boolean pressed) {
            if (!pressed) return;
            if (code == openKey.getKey()) openFolder();
            Minecraft mc = Minecraft.getInstance();
            if (announce.isOn() && mc.options.keyScreenshot.matches(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(code))) {
                // vanilla writes the PNG on an IO worker; copy the newest path a second later
                copyInTicks = 20;
            }
        }
    }
}
