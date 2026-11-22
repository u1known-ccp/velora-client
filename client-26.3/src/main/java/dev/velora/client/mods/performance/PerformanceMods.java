package dev.velora.client.mods.performance;

import dev.velora.client.api.TickListener;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.NumberSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Performance modules. Each one has a measurable effect:
 * fewer frames when unfocused, fewer entities/particles rendered, adaptive view distance.
 */
public final class PerformanceMods {
    private PerformanceMods() { }

    /** Limits the frame rate while the window is unfocused or minimized (saves GPU/CPU/battery). */
    public static final class FpsOptimizerMod extends Module {
        private final NumberSetting unfocusedFps = add(new NumberSetting("unfocused_fps", "FPS ohne Fokus", "", 30, 5, 120, 5, " FPS"));
        private final NumberSetting minimizedFps = add(new NumberSetting("minimized_fps", "FPS minimiert", "", 5, 1, 30, 1, " FPS"));

        public FpsOptimizerMod() {
            super("fps_optimizer", "FPS Optimizer", "Weniger FPS im Hintergrund – spart Strom und Leistung", Category.PERFORMANCE, true);
        }

        public int limit(int vanillaLimit) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow().isIconified()) return Math.min(vanillaLimit, minimizedFps.getInt());
            if (!mc.isWindowActive()) return Math.min(vanillaLimit, unfocusedFps.getInt());
            return vanillaLimit;
        }
    }

    /** Skips rendering of entities beyond configurable distances (vanilla still ticks them). */
    public static final class EntityCullingMod extends Module {
        private final NumberSetting maxDistance = add(new NumberSetting("max_distance", "Max. Distanz (Mobs)", "", 64, 16, 256, 8, " m"));
        private final NumberSetting itemDistance = add(new NumberSetting("item_distance", "Max. Distanz (Items/XP)", "", 32, 8, 128, 4, " m"));
        private final BooleanSetting cullPlayers = add(new BooleanSetting("cull_players", "Spieler einbeziehen", "Aus = Spieler immer rendern (PvP)", false));
        private final NumberSetting playerDistance = add(new NumberSetting("player_distance", "Max. Distanz (Spieler)", "", 128, 32, 256, 8, " m"));
        private final BooleanSetting hideArmorStands = add(new BooleanSetting("armor_stands", "Ferne Rüstungsständer", "Wie Items behandeln (Hologramme bleiben nah sichtbar)", true));

        public EntityCullingMod() {
            super("entity_culling", "Entity Culling", "Rendert weit entfernte Entities nicht", Category.PERFORMANCE, false);
            playerDistance.visibleWhen(cullPlayers::isOn);
        }

        /** @return true if the entity should NOT be rendered. */
        public boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
            Minecraft mc = Minecraft.getInstance();
            if (entity == mc.player || entity == mc.getCameraEntity() || entity.isVehicle() || entity.isCurrentlyGlowing()) return false;
            double limit;
            if (entity instanceof Player) {
                if (!cullPlayers.isOn()) return false;
                limit = playerDistance.getDouble();
            } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb
                    || (hideArmorStands.isOn() && entity instanceof ArmorStand)) {
                limit = itemDistance.getDouble();
            } else {
                limit = maxDistance.getDouble();
            }
            return entity.distanceToSqr(camX, camY, camZ) > limit * limit;
        }
    }

    /** Reduces particle spawning by a density percentage and caps the total amount. */
    public static final class ParticleOptimizerMod extends Module {
        private final NumberSetting density = add(new NumberSetting("density", "Partikeldichte", "", 60, 0, 100, 5, "%"));
        private final NumberSetting maxParticles = add(new NumberSetting("max", "Maximale Anzahl", "", 4000, 250, 16000, 250));

        public ParticleOptimizerMod() {
            super("particle_optimizer", "Particle Optimizer", "Weniger Partikel – stabilere FPS in Kämpfen", Category.PERFORMANCE, false);
        }

        /** @return true if the particle should be dropped. */
        public boolean shouldDrop(int currentCount) {
            if (currentCount >= maxParticles.getInt()) return true;
            int d = density.getInt();
            return d < 100 && ThreadLocalRandom.current().nextInt(100) >= d;
        }
    }

    /**
     * Adapts the view distance to reach a target FPS. Changes are small and rate-limited
     * (one step per 4 seconds) and the original value is restored when disabled.
     */
    public static final class RenderDistanceOptimizerMod extends Module implements TickListener {
        private final NumberSetting targetFps = add(new NumberSetting("target_fps", "Ziel-FPS", "", 90, 30, 240, 10, " FPS"));
        private final NumberSetting minDistance = add(new NumberSetting("min", "Min. Sichtweite", "", 6, 2, 16, 1, " Chunks"));
        private final NumberSetting maxDistance = add(new NumberSetting("max", "Max. Sichtweite", "", 16, 4, 32, 1, " Chunks"));

        private int original = -1;
        private int ticks;
        private double avgFps = -1;

        public RenderDistanceOptimizerMod() {
            super("render_distance_optimizer", "Render Distance Optimizer", "Passt die Sichtweite automatisch an die FPS an", Category.PERFORMANCE, false);
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) original = mc.options.renderDistance().get();
            avgFps = -1;
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (original > 0 && mc.options != null) mc.options.renderDistance().set(original);
            original = -1;
        }

        @Override
        public void onTick(Minecraft mc) {
            if (mc.level == null) return;
            int fps = mc.getFps();
            avgFps = avgFps < 0 ? fps : avgFps * 0.9 + fps * 0.1;
            if (++ticks < 80) return;
            ticks = 0;
            int current = mc.options.renderDistance().get();
            int min = minDistance.getInt();
            int max = Math.max(min, maxDistance.getInt());
            int next = current;
            if (avgFps < targetFps.getInt() * 0.9 && current > min) next = current - 1;
            else if (avgFps > targetFps.getInt() * 1.35 && current < max) next = current + 1;
            next = Math.max(min, Math.min(max, next));
            if (next != current) mc.options.renderDistance().set(next);
        }
    }
}
