package dev.velora.client.mixin;

import dev.velora.client.Mods;
import dev.velora.client.input.ClickTracker;
import dev.velora.client.input.InputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** 26.3 (SDL3): mouse events arrive as {@link MouseButtonInfo}; left = 1, right = 3. */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void velora$onMouseButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (handle != minecraft.getWindow().handle()) return;
        if (action == 1 && minecraft.gui.screen() == null && minecraft.gui.overlay() == null) ClickTracker.onClick(info.button());
        InputHandler.onMouseButton(info.button(), action);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void velora$onMouseScroll(long handle, double horizontal, double vertical, CallbackInfo ci) {
        if (handle != minecraft.getWindow().handle() || minecraft.gui.screen() != null || !Mods.ready()) return;
        if (Mods.zoom.onScroll(vertical)) ci.cancel();
    }

    /** Zoom sensitivity and freelook: adjust or redirect the mouse look delta. */
    @ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
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
