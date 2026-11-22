package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Optimizer: lower frame limit while unfocused / minimized. */
@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void velora$limit(CallbackInfoReturnable<Integer> cir) {
        if (Mods.ready() && Mods.fpsOptimizer.isEnabled()) cir.setReturnValue(Mods.fpsOptimizer.limit(cir.getReturnValue()));
    }
}
