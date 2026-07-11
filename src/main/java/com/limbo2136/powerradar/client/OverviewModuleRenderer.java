package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.block.entity.OverviewModuleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class OverviewModuleRenderer implements BlockEntityRenderer<OverviewModuleBlockEntity> {
    public OverviewModuleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            OverviewModuleBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        ModelBlockRenderer modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = buffer.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        modelRenderer.renderModel(
                poseStack.last(),
                consumer,
                state,
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                RenderType.translucent());
        poseStack.popPose();
    }
}
