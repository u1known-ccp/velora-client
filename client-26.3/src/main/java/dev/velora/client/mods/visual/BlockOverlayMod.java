package dev.velora.client.mods.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.velora.core.module.Category;
import dev.velora.core.module.Module;
import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ColorSetting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Custom block selection outline (color, alpha) with optional translucent fill.
 * 26.3 renders through submit nodes: we submit our own outline (and fill quads) and cancel the vanilla outline.
 */
public final class BlockOverlayMod extends Module {
    private final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Umrandung", "", 0xCC7C5CFF));
    private final BooleanSetting fill = add(new BooleanSetting("fill", "Füllung", "", true));
    private final ColorSetting fillColor = add(new ColorSetting("fill_color", "Füllfarbe", "", 0x267C5CFF));

    public BlockOverlayMod() {
        super("block_overlay", "Block Overlay", "Farbige Block-Umrandung mit optionaler Füllung", Category.VISUAL, false);
        fillColor.visibleWhen(fill::isOn);
    }

    /** Fabric BEFORE_BLOCK_OUTLINE callback. Returns false to cancel the vanilla outline when we draw our own. */
    public boolean render(LevelRenderContext ctx, BlockOutlineRenderState state) {
        if (!isEnabled() || state == null || state.shape().isEmpty()) return true;
        PoseStack poseStack = ctx.poseStack();
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        BlockPos pos = state.pos();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        if (fill.isOn()) {
            int c = fillColor.getRenderColor();
            ctx.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                for (AABB b : state.shape().toAabbs()) filledBox(pose, buffer, b.inflate(0.002), c);
            });
        }
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        ctx.submitNodeCollector().submitShapeOutline(poseStack, state.shape(), RenderTypes.linesTranslucent(),
                outlineColor.getRenderColor(), width, state.isTranslucent());
        poseStack.popPose();
        return false;
    }

    private static void filledBox(PoseStack.Pose pose, VertexConsumer b, AABB box, int c) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // 6 faces as quads
        quad(pose, b, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, b, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, b, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, b, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, b, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, b, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, int c,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.addVertex(pose, ax, ay, az).setColor(c);
        b.addVertex(pose, bx, by, bz).setColor(c);
        b.addVertex(pose, cx, cy, cz).setColor(c);
        b.addVertex(pose, dx, dy, dz).setColor(c);
    }
}
