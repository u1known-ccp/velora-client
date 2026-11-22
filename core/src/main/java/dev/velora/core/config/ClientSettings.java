package dev.velora.core.config;

import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.setting.SettingContainer;
import dev.velora.core.setting.TextSetting;

/** Global (profile independent) client settings, stored in {@code config/velora/client.json}. */
public final class ClientSettings extends SettingContainer {
    /** GLFW_KEY_RIGHT_SHIFT */
    public static final int DEFAULT_MENU_KEY = 344;

    /**
     * Key codes are platform specific (GLFW up to 1.21.x, SDL3 scancodes from 26.x). The platform layer sets
     * its Right-Shift code here before the core is created.
     */
    public static volatile int platformDefaultMenuKey = DEFAULT_MENU_KEY;

    public final ColorSetting accentColor = add(new ColorSetting("accent_color", "Akzentfarbe", "Farbe für Hervorhebungen im gesamten Client", 0xFF7C5CFF, false));
    public final KeybindSetting menuKey = add(new KeybindSetting("menu_key", "Menü-Taste", "Öffnet das Velora-Menü", platformDefaultMenuKey));
    public final BooleanSetting animations = add(new BooleanSetting("animations", "Animationen", "Übergänge und Hover-Animationen", true));
    public final NumberSetting animationSpeed = add(new NumberSetting("animation_speed", "Animationsgeschwindigkeit", "", 1.0, 0.5, 2.0, 0.1, "x"));
    public final BooleanSetting customTitleScreen = add(new BooleanSetting("custom_title_screen", "Velora-Hauptmenü", "Eigenen Titelbildschirm verwenden", true));
    public final BooleanSetting dimBackground = add(new BooleanSetting("dim_background", "Hintergrund abdunkeln", "Spielwelt hinter Menüs abdunkeln", true));
    public final ModeSetting menuLayout = add(new ModeSetting("menu_layout", "Menü-Layout", "", "Karten", "Karten", "Liste"));

    public final NumberSetting hudGridSize = add(new NumberSetting("hud_grid_size", "Rastergröße", "Rasterabstand im HUD-Editor", 8, 2, 32, 1, "px"));
    public final BooleanSetting hudSnap = add(new BooleanSetting("hud_snap", "Am Raster ausrichten", "Elemente im HUD-Editor einrasten lassen", true));
    public final BooleanSetting hudGuides = add(new BooleanSetting("hud_guides", "Hilfslinien", "Ausrichtungslinien zu Bildschirmmitte und anderen Elementen", true));

    public final BooleanSetting cosmeticsEnabled = add(new BooleanSetting("cosmetics_enabled", "Cosmetics anzeigen", "Capes und andere Cosmetics rendern", true));
    public final TextSetting equippedCape = add(new TextSetting("equipped_cape", "Cape", "", "", 64));
    public final BooleanSetting showOthersCosmetics = add(new BooleanSetting("show_others_cosmetics", "Cosmetics anderer Spieler", "Benötigt ein Backend", true));

    public final BooleanSetting backendEnabled = add(new BooleanSetting("backend_enabled", "Backend verwenden", "News, Cosmetics und Sync über einen Velora-Server", false));
    public final TextSetting backendUrl = add(new TextSetting("backend_url", "Backend-URL", "z.B. http://localhost:8787", "http://localhost:8787", 256));
    public final BooleanSetting statistics = add(new BooleanSetting("statistics", "Anonyme Statistiken", "Sendet nur Client-Version und Minecraft-Version", false));

    public final BooleanSetting showWatermark = add(new BooleanSetting("show_watermark", "Wasserzeichen", "Kleines Velora-Logo oben links im Spiel", false));
}
