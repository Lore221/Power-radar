package com.limbo2136.powerradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.link.LinkRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Renders Create's two frequency items for Power Radar block entities. */
public final class PowerRadarFrequencyRenderer<T extends SmartBlockEntity> implements BlockEntityRenderer<T> {
    public PowerRadarFrequencyRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        LinkRenderer.renderOnBlockEntity(
                blockEntity, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT, packedOverlay);
    }
}
