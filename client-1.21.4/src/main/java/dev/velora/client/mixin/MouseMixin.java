package dev.velora.client.mixin;

import dev.velora.client.Mods;
import dev.velora.client.input.ClickTracker;
import dev.velora.client.input.InputHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void velora$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window != client.getWindow().getHandle()) return;
        if (action == 1 && client.currentScreen == null && client.getOverlay() == null) ClickTracker.onClick(button);
        InputHandler.onMouseButton(button, action);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void velora$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != client.getWindow().getHandle() || client.currentScreen != null || !Mods.ready()) return;
        if (Mods.zoom.onScroll(vertical)) ci.cancel();
    }

    /** Zoom sensitivity and freelook: adjust or redirect the mouse look delta. */
    @ModifyArgs(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void velora$modifyLook(Args args) {
        if (!Mods.ready()) return;
        double dx = args.get(0);
        double dy = args.get(1);
        double sens = Mods.zoom.getSensitivityMultiplier();
        dx *= sens;
        dy *= sens;
        if (Mods.perspective.isActive()) {
            Mods.perspective.addDelta(dx, dy);
            dx = 0;
            dy = 0;
        }
        args.set(0, dx);
        args.set(1, dy);
    }
}
