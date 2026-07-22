package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Отправляет частичные модели приборов с уже подготовленной матрицей положения. */
final class OnboardPartialModelRenderer {
    private final ModelBlockRenderer modelRenderer;

    OnboardPartialModelRenderer(ModelBlockRenderer modelRenderer) {
        this.modelRenderer = modelRenderer;
    }

    void render(
            PartialModel model,
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay
    ) {
        render(model, computer, poseStack, consumer, packedLight, packedOverlay, RenderType.cutoutMipped());
    }

    // Полупрозрачные шкалы требуют согласованных буфера и render type, иначе альфа станет вырезкой.
    void renderTranslucent(
            PartialModel model,
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay
    ) {
        render(model, computer, poseStack, consumer, packedLight, packedOverlay, RenderType.translucent());
    }

    private void render(
            PartialModel model,
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            RenderType renderType
    ) {
        this.modelRenderer.renderModel(
                poseStack.last(),
                consumer,
                computer.getBlockState(),
                model.get(),
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                renderType);
    }
}
