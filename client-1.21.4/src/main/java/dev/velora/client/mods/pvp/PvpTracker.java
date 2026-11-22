package dev.velora.client.mods.pvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Box box = entity.getBoundingBox();
        double cx = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double cy = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        lastReach = eye.distanceTo(new Vec3d(cx, cy, cz));
        lastReachTime = System.currentTimeMillis();
        if (entity instanceof LivingEntity living) {
            if (living != target) {
                target = living;
                combo = 0;
            }
            lastTargetHurt = living.hurtTime;
        }
    }

    public static void tick(MinecraftClient mc) {
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
