package dev.velora.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.velora.client.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    // ------------------------------------------------------------------ crosshair

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void velora$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Mods.ready() && Mods.crosshair.replacesVanilla(client)) ci.cancel();
    }

    // ------------------------------------------------------------------ potion effects

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void velora$hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Mods.ready() && Mods.potionEffects.hidesVanilla()) ci.cancel();
    }

    // ------------------------------------------------------------------ scoreboard

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true)
    private void velora$hideScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Mods.ready() && Mods.scoreboard.isEnabled() && Mods.scoreboard.hide.isOn()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"))
    private void velora$scoreboardPush(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        MatrixStack m = context.getMatrices();
        m.push();
        if (Mods.ready() && Mods.scoreboard.isEnabled()) {
            float scale = Mods.scoreboard.scale.getFloat();
            float w = context.getScaledWindowWidth();
            float h = context.getScaledWindowHeight() / 2f;
            m.translate(w, h + Mods.scoreboard.offsetY.getFloat(), 0);
            m.scale(scale, scale, 1f);
            m.translate(-w, -h, 0);
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("TAIL"))
    private void velora$scoreboardPop(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        context.getMatrices().pop();
    }

    @WrapOperation(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"))
    private int velora$scoreboardText(DrawContext ctx, TextRenderer renderer, Text text, int x, int y, int color, boolean shadow,
                                      Operation<Integer> original) {
        if (Mods.ready() && Mods.scoreboard.isEnabled()) {
            // the red numbers are drawn right-aligned at the far right edge of the sidebar
            boolean isScore = x + renderer.getWidth(text) >= ctx.getScaledWindowWidth() - 2;
            if (isScore && Mods.scoreboard.hideNumbers.isOn()) return 0;
            shadow = Mods.scoreboard.textShadow.isOn();
        }
        return original.call(ctx, renderer, text, x, y, color, shadow);
    }

    @ModifyArg(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0), index = 4)
    private int velora$scoreboardHeader(int color) {
        return Mods.ready() && Mods.scoreboard.isEnabled() ? Mods.scoreboard.headerColor.getRenderColor() : color;
    }

    @ModifyArg(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1), index = 4)
    private int velora$scoreboardBody(int color) {
        return Mods.ready() && Mods.scoreboard.isEnabled() ? Mods.scoreboard.bodyColor.getRenderColor() : color;
    }
}
