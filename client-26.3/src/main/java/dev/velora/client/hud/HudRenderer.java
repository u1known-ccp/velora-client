package dev.velora.client.hud;

import dev.velora.client.ModuleCache;
import dev.velora.client.Mods;
import dev.velora.client.gui.HudEditorScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws all enabled HUD elements during normal gameplay (registered on Fabric's HudRenderCallback). */
public final class HudRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/HUD");
    private static final Set<String> FAILED = new HashSet<>();

    private HudRenderer() { }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || !Mods.ready()) return;
        if (Mods.crosshair.replacesVanilla(mc)) {
            try {
                Mods.crosshair.render(ctx, mc);
            } catch (RuntimeException e) {
                if (FAILED.add("crosshair")) LOGGER.error("Crosshair failed to render", e);
            }
        }
        if (mc.gui.screen() instanceof HudEditorScreen || mc.gui.hud.getDebugOverlay().showDebugScreen()) return;
        float delta = tickCounter.getGameTimeDeltaPartialTick(false);
        for (HudMod mod : ModuleCache.hudMods()) {
            if (mod.requiresWorld() && (mc.level == null || mc.player == null)) continue;
            if (!mod.hasContent()) continue;
            try {
                mod.render(ctx, delta, false);
            } catch (RuntimeException e) {
                // Never crash the game because of a HUD element; log once per element.
                if (FAILED.add(mod.getId())) LOGGER.error("HUD element {} failed to render", mod.getId(), e);
            }
        }
    }
}
