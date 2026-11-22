package dev.velora.client.mixin;

import dev.velora.client.Mods;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Toggle Sprint / Toggle Sneak: the vanilla key reports "pressed" while the toggle is active. */
@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void velora$toggleKeys(CallbackInfoReturnable<Boolean> cir) {
        if (!Mods.ready()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        Object self = this;
        if (self == mc.options.keySprint && Mods.toggleSprint.isSprintForced()) cir.setReturnValue(true);
        else if (self == mc.options.keyShift && Mods.toggleSneak.isSneakForced()) cir.setReturnValue(true);
    }
}
