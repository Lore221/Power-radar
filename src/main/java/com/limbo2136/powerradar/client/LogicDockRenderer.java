package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.LogicDockBlock;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Рисует карты, физически вставленные в фиксированные слоты док-станции. */
public final class LogicDockRenderer implements BlockEntityRenderer<LogicDockBlockEntity> {
    private static final ResourceLocation TARGETING_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/logic_dock/targeting_card");
    private static final ResourceLocation DISPLAY_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/logic_dock/display_card");
    private static final ResourceLocation ALLOWLIST_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/logic_dock/allowlist_card");

    private static final PartialModel TARGETING = PartialModel.of(TARGETING_LOCATION);
    private static final PartialModel DISPLAY = PartialModel.of(DISPLAY_LOCATION);
    private static final PartialModel ALLOWLIST = PartialModel.of(ALLOWLIST_LOCATION);

    public LogicDockRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(TARGETING_LOCATION));
        event.register(ModelResourceLocation.standalone(DISPLAY_LOCATION));
        event.register(ModelResourceLocation.standalone(ALLOWLIST_LOCATION));
    }

    @Override
    public void render(
            LogicDockBlockEntity dock,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (!dock.hasCard(0) && !dock.hasCard(1) && !dock.hasCard(2)) {
            return;
        }

        Direction facing = dock.getBlockState().getValue(LogicDockBlock.FACING);
        int cardLight = packedLight;
        if (dock.getLevel() != null) {
            // Ячейка док-станции затемнена ее корпусом, поэтому карты берут свет
            // из свободного блока непосредственно перед лицевой стороной.
            cardLight = LevelRenderer.getLightColor(
                    dock.getLevel(),
                    dock.getBlockPos().relative(facing)
            );
        }

        poseStack.pushPose();
        applyBlockstateRotation(poseStack, facing);
        if (dock.hasCard(0)) {
            renderCard(TARGETING, dock, poseStack, buffers, cardLight, packedOverlay);
        }
        if (dock.hasCard(1)) {
            renderCard(DISPLAY, dock, poseStack, buffers, cardLight, packedOverlay);
        }
        if (dock.hasCard(2)) {
            renderCard(ALLOWLIST, dock, poseStack, buffers, cardLight, packedOverlay);
        }
        poseStack.popPose();
    }

    // Повторяет y-повороты из blockstates/logic_dock.json вокруг центра блока.
    private static void applyBlockstateRotation(PoseStack poseStack, Direction facing) {
        float rotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        if (rotation == 0.0F) {
            return;
        }
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-rotation));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static void renderCard(
            PartialModel model,
            LogicDockBlockEntity dock,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        SuperByteBuffer geometry = CachedBuffers.partial(model, dock.getBlockState());
        // Обычное затенение граней сохраняет объем модели при любом внешнем освещении.
        geometry.light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutoutMipped()));
    }
}
