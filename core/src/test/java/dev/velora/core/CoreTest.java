package dev.velora.core;

import com.google.gson.JsonObject;
import dev.velora.core.config.ConfigManager;
import dev.velora.core.event.EventBus;
import dev.velora.core.hud.Anchor;
import dev.velora.core.hud.HudModule;
import dev.velora.core.hud.HudPosition;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.module.ModuleManager;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.setting.TextSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {

    static final class DemoModule extends Module {
        final BooleanSetting flag = add(new BooleanSetting("flag", "Flag", "", false));
        final NumberSetting number = add(new NumberSetting("number", "Number", "", 5, 0, 10, 0.5));
        final ModeSetting mode = add(new ModeSetting("mode", "Mode", "", "A", "A", "B", "C"));
        final ColorSetting color = add(new ColorSetting("color", "Color", "", 0xFF112233));
        int enables;

        DemoModule() { super("demo", "Demo", "Test module", Category.UTILITY, false); }

        @Override protected void onEnable() { enables++; }
    }

    static final class DemoHud extends HudModule {
        DemoHud() { super("demo_hud", "Demo HUD", "", Category.HUD, true, Anchor.TOP_RIGHT, -4, 4); }
    }

    @Test
    void numberSettingClampsAndSnaps() {
        NumberSetting s = new NumberSetting("n", "n", "", 1, 0, 2, 0.25);
        s.set(1.13);
        assertEquals(1.25, s.get());
        s.set(99.0);
        assertEquals(2.0, s.get());
        s.set(Double.NaN);
        assertEquals(1.0, s.get());
    }

    @Test
    void textSettingStripsControlAndFormattingChars() {
        TextSetting t = new TextSetting("t", "t", "", "", 5);
        t.set("a§cb\nc123456");
        assertEquals("acbc1", t.get());
    }

    @Test
    void modeSettingCycles() {
        ModeSetting m = new ModeSetting("m", "m", "", "A", "A", "B");
        m.cycle(1);
        assertEquals("B", m.get());
        m.cycle(1);
        assertEquals("A", m.get());
        m.set("nonsense");
        assertEquals("A", m.get());
    }

    @Test
    void moduleRoundTrip() {
        DemoModule a = new DemoModule();
        a.flag.set(true);
        a.number.set(7.5);
        a.mode.set("C");
        a.color.setChroma(true);
        a.setEnabled(true);
        a.setFavorite(true);
        JsonObject saved = a.save();

        DemoModule b = new DemoModule();
        b.load(saved);
        assertTrue(b.isEnabled());
        assertTrue(b.isFavorite());
        assertTrue(b.flag.isOn());
        assertEquals(7.5, b.number.get());
        assertEquals("C", b.mode.get());
        assertTrue(b.color.isChroma());
        assertEquals(1, b.enables);
    }

    @Test
    void hudPositionReanchorsToNearestCorner() {
        HudPosition p = new HudPosition(Anchor.TOP_LEFT, 2, 2);
        // element 40x10 dragged to the bottom right
        p.setAbsolute(950, 530, 40, 10, 1000, 550);
        assertEquals(Anchor.BOTTOM_RIGHT, p.getAnchor());
        assertEquals(950, p.resolveX(1000, 40), 0.001);
        assertEquals(530, p.resolveY(550, 10), 0.001);
        // after a resize, the element stays glued to the bottom right corner
        assertEquals(1870, p.resolveX(1920, 40), 0.001);
    }

    @Test
    void profilesPersistAndSwitch(@TempDir Path dir) throws Exception {
        ModuleManager modules = new ModuleManager();
        DemoModule demo = modules.register(new DemoModule());
        DemoHud hud = modules.register(new DemoHud());
        ConfigManager config = new ConfigManager(dir, modules, new EventBus(), Runnable::run);
        config.load();
        assertTrue(hud.isEnabled(), "enabledByDefault should apply on first start");

        demo.setEnabled(true);
        demo.number.set(3.0);
        config.saveNow();

        String pvp = config.createProfile("PvP", false);
        config.switchProfile(pvp);
        assertFalse(demo.isEnabled(), "fresh profile uses defaults");
        assertEquals(5.0, demo.number.get());

        config.switchProfile("Default");
        assertTrue(demo.isEnabled());
        assertEquals(3.0, demo.number.get());

        Path exported = dir.resolve("export.json");
        config.exportProfile("Default", exported);
        assertTrue(Files.size(exported) > 0);
        String imported = config.importProfile(exported);
        assertTrue(config.profiles().list().contains(imported));
        config.shutdown();
    }

    @Test
    void corruptedConfigFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("client.json"), "{ this is not json");
        ModuleManager modules = new ModuleManager();
        modules.register(new DemoModule());
        ConfigManager config = new ConfigManager(dir, modules, new EventBus(), Runnable::run);
        assertDoesNotThrow(config::load);
        assertEquals("Default", config.profiles().getActive());
        config.shutdown();
    }
}
