package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Zoom: divide the world FOV (hand FOV, changingFov=false, is left untouched). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void velora$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov || !Mods.ready()) return;
        double divisor = Mods.zoom.getDivisor();
        if (divisor > 1.0001) cir.setReturnValue((float) (cir.getReturnValue() / divisor));
    }
}
