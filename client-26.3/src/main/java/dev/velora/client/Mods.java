package dev.velora.client;

import dev.velora.client.mods.hud.ArmorStatusMod;
import dev.velora.client.mods.hud.CoordinatesMod;
import dev.velora.client.mods.hud.CpsMod;
import dev.velora.client.mods.hud.DirectionMod;
import dev.velora.client.mods.hud.FpsMod;
import dev.velora.client.mods.hud.KeystrokesMod;
import dev.velora.client.mods.hud.PotionEffectsMod;
import dev.velora.client.mods.hud.ScoreboardMod;
import dev.velora.client.mods.hud.SimpleInfoMods;
import dev.velora.client.mods.performance.PerformanceMods;
import dev.velora.client.mods.pvp.AutoGGMod;
import dev.velora.client.mods.pvp.CombatDisplayMods;
import dev.velora.client.mods.pvp.CrosshairMod;
import dev.velora.client.mods.pvp.HitColorMod;
import dev.velora.client.mods.pvp.PerspectiveMod;
import dev.velora.client.mods.pvp.ToggleSneakMod;
import dev.velora.client.mods.pvp.ToggleSprintMod;
import dev.velora.client.mods.pvp.ZoomMod;
import dev.velora.client.mods.utility.UtilityMods;
import dev.velora.client.mods.utility.WaypointsMod;
import dev.velora.client.mods.visual.BlockOverlayMod;
import dev.velora.client.mods.visual.VisualMods;
import dev.velora.core.module.ModuleManager;

/**
 * Registers every module and exposes typed static references for the mixins
 * (avoids map lookups on hot paths such as rendering and input).
 */
public final class Mods {
    // HUD
    public static FpsMod fps;
    public static CpsMod cps;
    public static KeystrokesMod keystrokes;
    public static ArmorStatusMod armorStatus;
    public static PotionEffectsMod potionEffects;
    public static CoordinatesMod coordinates;
    public static DirectionMod direction;
    public static SimpleInfoMods.PingMod ping;
    public static SimpleInfoMods.MemoryMod memory;
    public static SimpleInfoMods.SpeedometerMod speedometer;
    public static SimpleInfoMods.BiomeMod biome;
    public static SimpleInfoMods.ClockMod clock;
    public static ScoreboardMod scoreboard;
    // PvP
    public static HitColorMod hitColor;
    public static CombatDisplayMods.ComboCounterMod combo;
    public static CombatDisplayMods.ReachDisplayMod reach;
    public static CrosshairMod crosshair;
    public static ToggleSprintMod toggleSprint;
    public static ToggleSneakMod toggleSneak;
    public static AutoGGMod autoGG;
    public static PerspectiveMod perspective;
    public static ZoomMod zoom;
    // Visuals
    public static VisualMods.FullbrightMod fullbright;
    public static VisualMods.TimeChangerMod timeChanger;
    public static VisualMods.WeatherChangerMod weatherChanger;
    public static BlockOverlayMod blockOverlay;
    public static VisualMods.NametagsMod nametags;
    // Performance
    public static PerformanceMods.FpsOptimizerMod fpsOptimizer;
    public static PerformanceMods.EntityCullingMod entityCulling;
    public static PerformanceMods.ParticleOptimizerMod particleOptimizer;
    public static PerformanceMods.RenderDistanceOptimizerMod renderDistanceOptimizer;
    // Utility
    public static UtilityMods.AutoTextMod autoText;
    public static UtilityMods.ChatCustomizerMod chatCustomizer;
    public static UtilityMods.CoordinatesClipboardMod coordsClipboard;
    public static SimpleInfoMods.ServerInfoMod serverInfo;
    public static UtilityMods.QuickJoinMod quickJoin;
    public static WaypointsMod waypoints;
    public static UtilityMods.ScreenshotManagerMod screenshots;

    private Mods() { }

    static void registerAll(ModuleManager m) {
        fps = m.register(new FpsMod());
        cps = m.register(new CpsMod());
        keystrokes = m.register(new KeystrokesMod());
        armorStatus = m.register(new ArmorStatusMod());
        potionEffects = m.register(new PotionEffectsMod());
        coordinates = m.register(new CoordinatesMod());
        direction = m.register(new DirectionMod());
        ping = m.register(new SimpleInfoMods.PingMod());
        memory = m.register(new SimpleInfoMods.MemoryMod());
        speedometer = m.register(new SimpleInfoMods.SpeedometerMod());
        biome = m.register(new SimpleInfoMods.BiomeMod());
        clock = m.register(new SimpleInfoMods.ClockMod());
        scoreboard = m.register(new ScoreboardMod());

        hitColor = m.register(new HitColorMod());
        combo = m.register(new CombatDisplayMods.ComboCounterMod());
        reach = m.register(new CombatDisplayMods.ReachDisplayMod());
        crosshair = m.register(new CrosshairMod());
        toggleSprint = m.register(new ToggleSprintMod());
        toggleSneak = m.register(new ToggleSneakMod());
        autoGG = m.register(new AutoGGMod());
        perspective = m.register(new PerspectiveMod());
        zoom = m.register(new ZoomMod());

        fullbright = m.register(new VisualMods.FullbrightMod());
        timeChanger = m.register(new VisualMods.TimeChangerMod());
        weatherChanger = m.register(new VisualMods.WeatherChangerMod());
        blockOverlay = m.register(new BlockOverlayMod());
        nametags = m.register(new VisualMods.NametagsMod());

        fpsOptimizer = m.register(new PerformanceMods.FpsOptimizerMod());
        entityCulling = m.register(new PerformanceMods.EntityCullingMod());
        particleOptimizer = m.register(new PerformanceMods.ParticleOptimizerMod());
        renderDistanceOptimizer = m.register(new PerformanceMods.RenderDistanceOptimizerMod());

        autoText = m.register(new UtilityMods.AutoTextMod());
        chatCustomizer = m.register(new UtilityMods.ChatCustomizerMod());
        coordsClipboard = m.register(new UtilityMods.CoordinatesClipboardMod());
        serverInfo = m.register(new SimpleInfoMods.ServerInfoMod());
        quickJoin = m.register(new UtilityMods.QuickJoinMod());
        waypoints = m.register(new WaypointsMod());
        screenshots = m.register(new UtilityMods.ScreenshotManagerMod());
    }

    /** True once registration happened (mixins may run before the core is ready). */
    public static boolean ready() { return zoom != null && dev.velora.core.VeloraCore.isReady(); }
}
