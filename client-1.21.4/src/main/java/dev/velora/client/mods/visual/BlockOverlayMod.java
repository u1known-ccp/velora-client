package dev.velora.client.mods.visual;

import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/** Custom block selection outline (color, alpha) with optional translucent fill. */
public final class BlockOverlayMod extends Module {
    private final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Umrandung", "", 0xCC7C5CFF));
    private final BooleanSetting fill = add(new BooleanSetting("fill", "Füllung", "", true));
    private final ColorSetting fillColor = add(new ColorSetting("fill_color", "Füllfarbe", "", 0x267C5CFF));

    public BlockOverlayMod() {
        super("block_overlay", "Block Overlay", "Farbige Block-Umrandung mit optionaler Füllung", Category.VISUAL, false);
        fillColor.visibleWhen(fill::isOn);
    }

    /**
     * Fabric BLOCK_OUTLINE callback. Returns false to cancel the vanilla outline when we draw our own.
     */
    public boolean render(WorldRenderContext ctx, BlockPos pos, BlockState state, double camX, double camY, double camZ) {
        if (!isEnabled() || ctx.world() == null || ctx.consumers() == null) return true;
        VoxelShape shape = state.getOutlineShape(ctx.world(), pos, ShapeContext.of(ctx.camera().getFocusedEntity()));
        if (shape.isEmpty()) return true;
        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return true;
        VertexConsumerProvider consumers = ctx.consumers();
        double ox = pos.getX() - camX, oy = pos.getY() - camY, oz = pos.getZ() - camZ;

        if (fill.isOn()) {
            int c = fillColor.getRenderColor();
            VertexConsumer quads = consumers.getBuffer(RenderLayer.getDebugFilledBox());
            for (Box b : shape.getBoundingBoxes()) {
                Box e = b.expand(0.002).offset(ox, oy, oz);
                VertexRendering.drawFilledBox(matrices, quads, e.minX, e.minY, e.minZ, e.maxX, e.maxY, e.maxZ,
                        ColorUtil.red(c) / 255f, ColorUtil.green(c) / 255f, ColorUtil.blue(c) / 255f, ColorUtil.alpha(c) / 255f);
            }
        }
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        VertexRendering.drawOutline(matrices, lines, shape, ox, oy, oz, outlineColor.getRenderColor());
        return false;
    }
}
