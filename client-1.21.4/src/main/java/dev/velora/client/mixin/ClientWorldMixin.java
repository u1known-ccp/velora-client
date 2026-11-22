package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Time Changer: overrides {@code getLunarTime()} (default method of WorldAccess) for the client world only.
 * Sky angle, sky color and moon phase are derived from it; the real time of day is not modified,
 * so the vanilla tick ({@code setTimeOfDay(getTimeOfDay() + 1)}) keeps working.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    public long getLunarTime() {
        long real = ((ClientWorld) (Object) this).getLevelProperties().getTimeOfDay();
        if (Mods.ready() && Mods.timeChanger.isEnabled()) return Mods.timeChanger.timeOfDay(real);
        return real;
    }
}
