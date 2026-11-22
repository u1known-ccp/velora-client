package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * 26.3: Freelook redirects the camera rotation in {@code alignWithEntity} (ordinal 0 = riding a new-style minecart,
 * ordinal 1 = normal case); Zoom divides the world FOV computed by {@code calculateFov}.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    private void velora$freelookMinecart(Args args) {
        apply(args);
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void velora$freelook(Args args) {
        apply(args);
    }

    private static void apply(Args args) {
        if (!Mods.ready() || !Mods.perspective.isActive()) return;
        args.set(0, Mods.perspective.getYaw());
        args.set(1, Mods.perspective.getPitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void velora$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (!Mods.ready()) return;
        double divisor = Mods.zoom.getDivisor();
        if (divisor > 1.0001) cir.setReturnValue((float) (cir.getReturnValue() / divisor));
    }
}
