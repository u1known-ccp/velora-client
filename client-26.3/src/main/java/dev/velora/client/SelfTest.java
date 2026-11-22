package dev.velora.client;

import dev.velora.client.gui.HudEditorScreen;
import dev.velora.client.gui.ModMenuScreen;
import dev.velora.core.VeloraCore;
import dev.velora.core.module.Module;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Automated smoke test for development builds, enabled with {@code -Dvelora.selftest=true}
 * (Gradle task {@code runSelftest}). Audits all mixins, walks through every Velora screen,
 * joins a test world with HUD elements enabled and writes screenshots to {@code run/screenshots}.
 * Never active in normal launches.
 */
final class SelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/SelfTest");
    private static final String WORLD = "VeloraSelfTest";
    private static int tick;
    private static int step;
    private static int waitUntil;

    private SelfTest() { }

    static boolean enabled() { return Boolean.getBoolean("velora.selftest"); }

    static void onStarted() {
        Minecraft mc = Minecraft.getInstance();
        // the test window usually has no focus – keep the game running and skip first-run dialogs
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        LOGGER.info("[SelfTest] Auditing mixins…");
        MixinEnvironment.getCurrentEnvironment().audit();
        LOGGER.info("[SelfTest] Mixin audit finished");
    }

    static void tick(Minecraft mc) {
        tick++;
        if (tick < waitUntil) return;
        try {
            run(mc);
        } catch (RuntimeException e) {
            LOGGER.error("[SelfTest] FAILED at step {}", step, e);
            mc.stop();
        }
    }

    private static void waitTicks(int ticks) { waitUntil = tick + ticks; }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "velora_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        LOGGER.info("[SelfTest] screenshot {}", name);
    }

    private static void run(Minecraft mc) {
        switch (step++) {
            case 0 -> waitTicks(60);
            case 1 -> { shot(mc, "01_title"); mc.gui.setScreen(new ModMenuScreen(mc.gui.screen())); waitTicks(20); }
            case 2 -> { shot(mc, "02_menu"); mc.gui.setScreen(ModMenuScreen.forModule(null, Mods.keystrokes)); waitTicks(20); }
            case 3 -> { shot(mc, "03_settings"); mc.gui.setScreen(ModMenuScreen.clientSettings(null)); waitTicks(20); }
            case 4 -> { shot(mc, "03b_client_settings"); mc.gui.setScreen(new HudEditorScreen(null)); waitTicks(20); }
            case 5 -> {
                shot(mc, "04_hud_editor");
                mc.gui.setScreen(null);
                if (mc.getLevelSource().levelExists(WORLD)) {
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> { });
                } else {
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, MinecraftServer.DEMO_SETTINGS,
                            WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions, null);
                }
                waitTicks(40);
            }
            case 6 -> {
                if (mc.level == null || mc.player == null) {
                    step = 6; // keep waiting for the world
                    waitTicks(20);
                    if (tick > 20 * 240) throw new IllegalStateException("World did not load in time");
                    return;
                }
                LOGGER.info("[SelfTest] world joined");
                for (Module m : new Module[]{Mods.fps, Mods.cps, Mods.keystrokes, Mods.armorStatus, Mods.coordinates, Mods.direction,
                        Mods.clock, Mods.ping, Mods.memory, Mods.biome, Mods.speedometer, Mods.toggleSprint, Mods.crosshair, Mods.waypoints}) {
                    m.setEnabled(true);
                }
                Mods.waypoints.addHere("Spawn");
                VeloraCore.get().config().client().equippedCape.set("velora_classic");
                waitTicks(100);
            }
            case 7 -> { shot(mc, "05_ingame_hud"); mc.gui.setScreen(ModMenuScreen.cosmetics(null)); waitTicks(20); }
            case 8 -> {
                shot(mc, "06_cosmetics");
                mc.gui.setScreen(null);
                Mods.fullbright.setEnabled(true);
                Mods.timeChanger.setEnabled(true);
                Mods.timeChanger.preset.set("Mitternacht");
                Mods.blockOverlay.setEnabled(true);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                Mods.nametags.setEnabled(true);
                waitTicks(40);
            }
            case 9 -> { shot(mc, "07_visuals_cape"); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.gui.setScreen(new HudEditorScreen(null)); waitTicks(20); }
            case 10 -> {
                shot(mc, "08_hud_editor_ingame");
                mc.gui.setScreen(null);
                Mods.fullbright.setEnabled(false);
                Mods.timeChanger.setEnabled(false);
                waitTicks(10);
            }
            case 11 -> {
                LOGGER.info("[SelfTest] PASSED – all steps completed");
                mc.stop();
            }
            default -> { }
        }
    }
}
