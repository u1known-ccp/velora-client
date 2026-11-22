package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: visual rain/thunder strength on the client world only (integrated server untouched). */
@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow public abstract boolean isClient();

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void velora$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if (isClient() && Mods.ready() && Mods.weatherChanger.isEnabled()) cir.setReturnValue(Mods.weatherChanger.rain());
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void velora$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (isClient() && Mods.ready() && Mods.weatherChanger.isEnabled()) cir.setReturnValue(Mods.weatherChanger.thunder());
    }
}
