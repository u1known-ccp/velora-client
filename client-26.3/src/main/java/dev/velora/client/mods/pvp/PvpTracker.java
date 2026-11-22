package dev.velora.client.mods.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Purely observational combat statistics for the local player (combo + reach display).
 * Nothing here changes attacks, hit boxes or packets.
 */
public final class PvpTracker {
    private static final long COMBO_TIMEOUT_MS = 2500;

    private static LivingEntity target;
    private static int lastTargetHurt;
    private static int lastSelfHurt;
    private static int combo;
    private static long lastHitTime;
    private static double lastReach = -1;
    private static long lastReachTime;

    private PvpTracker() { }

    /** Called from AttackEntityCallback (client side, local player only). */
    public static void onAttack(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(1.0f);
        AABB box = entity.getBoundingBox();
        double cx = Mth.clamp(eye.x, box.minX, box.maxX);
        double cy = Mth.clamp(eye.y, box.minY, box.maxY);
        double cz = Mth.clamp(eye.z, box.minZ, box.maxZ);
        lastReach = eye.distanceTo(new Vec3(cx, cy, cz));
        lastReachTime = System.currentTimeMillis();
        if (entity instanceof LivingEntity living) {
            if (living != target) {
                target = living;
                combo = 0;
            }
            lastTargetHurt = living.hurtTime;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            reset();
            return;
        }
        int selfHurt = mc.player.hurtTime;
        if (selfHurt > lastSelfHurt) combo = 0; // we got hit → combo broken
        lastSelfHurt = selfHurt;

        if (target != null) {
            if (!target.isAlive() || target.isRemoved()) {
                target = null;
            } else {
                int hurt = target.hurtTime;
                if (hurt > lastTargetHurt) {
                    combo++;
                    lastHitTime = System.currentTimeMillis();
                }
                lastTargetHurt = hurt;
            }
        }
        if (combo > 0 && System.currentTimeMillis() - lastHitTime > COMBO_TIMEOUT_MS) combo = 0;
    }

    public static void reset() {
        target = null;
        combo = 0;
        lastTargetHurt = 0;
        lastSelfHurt = 0;
    }

    public static int combo() { return combo; }

    public static double lastReach() { return lastReach; }

    public static long lastReachAgeMs() { return System.currentTimeMillis() - lastReachTime; }
}
