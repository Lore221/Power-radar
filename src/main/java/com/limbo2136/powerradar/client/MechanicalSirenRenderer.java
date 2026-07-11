package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.block.MechanicalSirenBlock;
import com.limbo2136.powerradar.block.entity.MechanicalSirenBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalSirenRenderer extends KineticBlockEntityRenderer<MechanicalSirenBlockEntity> {
    public MechanicalSirenRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            MechanicalSirenBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        BlockState state = blockEntity.getBlockState();
        Direction rear = state.getValue(MechanicalSirenBlock.HORIZONTAL_FACING).getOpposite();
        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, rear);
        renderRotatingBuffer(blockEntity, shaft, poseStack, buffer.getBuffer(RenderType.solid()), light);
    }
}
