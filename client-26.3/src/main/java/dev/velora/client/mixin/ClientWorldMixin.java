package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time Changer (26.3): sun, moon and sky colors are evaluated from the client-side world clocks.
 * Only the value read on the client is changed – the real time is still received from the server.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientWorldMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void velora$timeChanger(CallbackInfoReturnable<Long> cir) {
        if (Mods.ready() && Mods.timeChanger.isEnabled()) cir.setReturnValue(Mods.timeChanger.timeOfDay(cir.getReturnValue()));
    }
}
