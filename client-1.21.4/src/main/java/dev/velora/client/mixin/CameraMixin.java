package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Freelook: the camera uses the free rotation instead of the player's rotation.
 * Ordinal 0 = riding an experimental minecart, ordinal 1 = normal case (see Camera#update).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0))
    private void velora$freelookMinecart(Args args) {
        apply(args);
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 1))
    private void velora$freelook(Args args) {
        apply(args);
    }

    private static void apply(Args args) {
        if (!Mods.ready() || !Mods.perspective.isActive()) return;
        args.set(0, Mods.perspective.getYaw());
        args.set(1, Mods.perspective.getPitch());
    }
}
