package dev.velora.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.velora.client.Mods;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3: the in-game HUD lives in {@link Hud} (formerly Gui/InGameHud). */
@Mixin(Hud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private Minecraft minecraft;

    // ------------------------------------------------------------------ crosshair

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void velora$crosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Mods.ready() && Mods.crosshair.replacesVanilla(minecraft)) ci.cancel();
    }

    // ------------------------------------------------------------------ potion effects

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void velora$hideVanillaEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Mods.ready() && Mods.potionEffects.hidesVanilla()) ci.cancel();
    }

    // ------------------------------------------------------------------ scoreboard

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void velora$hideScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Mods.ready() && Mods.scoreboard.isEnabled() && Mods.scoreboard.hide.isOn()) ci.cancel();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void velora$scoreboardPush(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        Matrix3x2fStack m = graphics.pose();
        m.pushMatrix();
        if (Mods.ready() && Mods.scoreboard.isEnabled()) {
            float scale = Mods.scoreboard.scale.getFloat();
            float w = graphics.guiWidth();
            float h = graphics.guiHeight() / 2f;
            m.translate(w, h + Mods.scoreboard.offsetY.getFloat());
            m.scale(scale, scale);
            m.translate(-w, -h);
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"))
    private void velora$scoreboardPop(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private void velora$scoreboardText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow,
                                       Operation<Void> original) {
        if (Mods.ready() && Mods.scoreboard.isEnabled()) {
            // the red numbers are drawn right-aligned at the far right edge of the sidebar
            boolean isScore = x + font.width(text) >= graphics.guiWidth() - 2;
            if (isScore && Mods.scoreboard.hideNumbers.isOn()) return;
            shadow = Mods.scoreboard.textShadow.isOn();
        }
        original.call(graphics, font, text, x, y, color, shadow);
    }

    @ModifyArg(method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), index = 4)
    private int velora$scoreboardHeader(int color) {
        return Mods.ready() && Mods.scoreboard.isEnabled() ? Mods.scoreboard.headerColor.getRenderColor() : color;
    }

    @ModifyArg(method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1), index = 4)
    private int velora$scoreboardBody(int color) {
        return Mods.ready() && Mods.scoreboard.isEnabled() ? Mods.scoreboard.bodyColor.getRenderColor() : color;
    }
}
