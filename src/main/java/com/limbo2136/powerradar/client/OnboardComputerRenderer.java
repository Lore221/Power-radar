package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

public final class OnboardComputerRenderer implements BlockEntityRenderer<OnboardComputerBlockEntity> {
    private static final float PANEL_HEIGHT = 11.0F / 16.0F;
    private static final float SCREEN_SIDE = 10.5F / 16.0F;
    private static final int MAP_RADIUS_BLOCKS = 100;
    // Maps the delegate's vertical monitor plane onto the model element's upper face:
    // +90 degrees to lay it flat with an outward normal, then the element's own
    // -22.5-degree X tilt. The negative Y scale preserves the map orientation.
    private static final float MONITOR_TOP_ROTATION_DEGREES = 67.5F;
    private static final double SCREEN_FACE_TRANSLATION = 0.496D;
    private final RadarMonitorControllerBlockEntityRenderer delegate;

    public OnboardComputerRenderer(BlockEntityRendererProvider.Context context) {
        this.delegate = new RadarMonitorControllerBlockEntityRenderer(context);
    }

    @Override public void render(OnboardComputerBlockEntity computer, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = computer.getBlockState().getValue(OnboardComputerBlock.FACING);
        Direction right = facing.getClockWise();
        double pivotX = 0.5D + facing.getStepX() * 0.5D;
        double pivotZ = 0.5D + facing.getStepZ() * 0.5D;
        poseStack.pushPose();
        poseStack.translate(pivotX, PANEL_HEIGHT, pivotZ);
        poseStack.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(MONITOR_TOP_ROTATION_DEGREES),
                right.getStepX(), 0.0F, right.getStepZ()));
        poseStack.translate(-pivotX, -PANEL_HEIGHT, -pivotZ);
        poseStack.translate(facing.getStepX() * SCREEN_FACE_TRANSLATION,
                PANEL_HEIGHT + (PANEL_HEIGHT - SCREEN_SIDE) / 2.0D + SCREEN_SIDE,
                facing.getStepZ() * SCREEN_FACE_TRANSLATION);
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(SCREEN_SIDE, -SCREEN_SIDE, SCREEN_SIDE);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        this.delegate.renderFullSurfaceWithFixedMapSize(
                computer, partialTick, poseStack, buffers, packedLight, packedOverlay,
                MAP_RADIUS_BLOCKS * 2);
        poseStack.popPose();
    }
}
