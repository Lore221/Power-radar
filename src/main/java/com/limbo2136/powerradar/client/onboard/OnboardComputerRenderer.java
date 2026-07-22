package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.client.RadarMonitorControllerBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Quaternionf;

/** Координирует экран радара и отдельный рендерер приборной панели Onboard Computer. */
public final class OnboardComputerRenderer implements BlockEntityRenderer<OnboardComputerBlockEntity> {
    private static final float SCREEN_SIDE = 8.0F / 16.0F;
    private static final int MAP_RADIUS_BLOCKS = 100;
    // Плоскость монитора сначала укладывается на верхнюю грань, затем повторяет наклон модели.
    private static final float MONITOR_TOP_ROTATION_DEGREES = 67.5F;
    private static final double SCREEN_FACE_TRANSLATION = 0.496D;

    private final RadarMonitorControllerBlockEntityRenderer monitorRenderer;
    private final OnboardPanelModuleRenderer moduleRenderer;

    public OnboardComputerRenderer(BlockEntityRendererProvider.Context context) {
        this.monitorRenderer = new RadarMonitorControllerBlockEntityRenderer(context);
        this.moduleRenderer = new OnboardPanelModuleRenderer(context);
    }

    // Частичные модели приборов не упоминаются в blockstate, поэтому регистрируются явно.
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        OnboardPanelModuleRenderer.registerAdditionalModels(event);
    }

    @Override
    public void render(
            OnboardComputerBlockEntity computer,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = computer.getBlockState().getValue(OnboardComputerBlock.FACING);
        renderMonitor(computer, partialTick, poseStack, buffers, packedLight, packedOverlay, facing);
        this.moduleRenderer.render(
                computer, partialTick, poseStack, buffers, packedLight, packedOverlay, facing);
    }

    // Отображает общую карту радара на наклонном квадрате 8x8 пикселей.
    private void renderMonitor(
            OnboardComputerBlockEntity computer,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        Direction right = facing.getClockWise();
        double pivotX = 0.5D + facing.getStepX() * 0.5D;
        double pivotZ = 0.5D + facing.getStepZ() * 0.5D;
        poseStack.pushPose();
        poseStack.translate(pivotX, OnboardPanelModuleRenderer.PANEL_HEIGHT, pivotZ);
        poseStack.mulPose(new Quaternionf().rotateAxis(
                (float) Math.toRadians(MONITOR_TOP_ROTATION_DEGREES),
                right.getStepX(),
                0.0F,
                right.getStepZ()));
        poseStack.translate(-pivotX, -OnboardPanelModuleRenderer.PANEL_HEIGHT, -pivotZ);
        poseStack.translate(
                facing.getStepX() * SCREEN_FACE_TRANSLATION,
                OnboardPanelModuleRenderer.PANEL_HEIGHT
                        + (OnboardPanelModuleRenderer.PANEL_HEIGHT - SCREEN_SIDE) / 2.0D
                        + SCREEN_SIDE,
                facing.getStepZ() * SCREEN_FACE_TRANSLATION);
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(SCREEN_SIDE, -SCREEN_SIDE, SCREEN_SIDE);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        this.monitorRenderer.renderFullSurfaceWithFixedMapSize(
                computer,
                partialTick,
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                MAP_RADIUS_BLOCKS * 2);
        poseStack.popPose();
    }
}
