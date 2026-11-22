package dev.velora.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.velora.client.Mods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS Optimizer: lower frame limit while unfocused / minimized. */
@Mixin(FramerateLimitTracker.class)
public abstract class InactivityFpsLimiterMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void velora$limit(CallbackInfoReturnable<Integer> cir) {
        if (Mods.ready() && Mods.fpsOptimizer.isEnabled()) cir.setReturnValue(Mods.fpsOptimizer.limit(cir.getReturnValue()));
    }
}
