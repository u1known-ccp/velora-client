package dev.velora.client;

import dev.velora.client.api.KeyListener;
import dev.velora.client.api.TickListener;
import dev.velora.client.hud.HudMod;
import dev.velora.core.VeloraCore;
import dev.velora.core.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-filtered lists of enabled modules per capability. Rebuilt only when a module is toggled
 * or a profile is loaded, so the per-frame / per-tick dispatch is a plain list iteration.
 */
public final class ModuleCache {
    private static volatile List<HudMod> hudMods = List.of();
    private static volatile List<TickListener> tickListeners = List.of();
    private static volatile List<KeyListener> keyListeners = List.of();
    private static volatile List<Module> allModules = List.of();

    private ModuleCache() { }

    public static void rebuild() {
        List<HudMod> huds = new ArrayList<>();
        List<TickListener> ticks = new ArrayList<>();
        List<KeyListener> keys = new ArrayList<>();
        List<Module> all = VeloraCore.get().modules().all();
        for (Module m : all) {
            if (!m.isEnabled()) continue;
            if (m instanceof HudMod h) huds.add(h);
            if (m instanceof TickListener t) ticks.add(t);
            if (m instanceof KeyListener k) keys.add(k);
        }
        hudMods = List.copyOf(huds);
        tickListeners = List.copyOf(ticks);
        keyListeners = List.copyOf(keys);
        allModules = all;
    }

    public static List<HudMod> hudMods() { return hudMods; }
    public static List<TickListener> tickListeners() { return tickListeners; }
    public static List<KeyListener> keyListeners() { return keyListeners; }
    public static List<Module> allModules() { return allModules; }
}
