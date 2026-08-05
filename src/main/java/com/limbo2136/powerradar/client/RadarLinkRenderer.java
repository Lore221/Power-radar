package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Рисует full-bright колбу и внешний additive-свет ламп Radar Link. */
public final class RadarLinkRenderer implements BlockEntityRenderer<RadarLinkBlockEntity> {
    private static final ResourceLocation HORIZONTAL_RED_TUBE_LOCATION =
            PowerRadar.id("block/radar_link/tube_horizontal_red");
    private static final ResourceLocation HORIZONTAL_GREEN_TUBE_LOCATION =
            PowerRadar.id("block/radar_link/tube_horizontal_green");
    private static final ResourceLocation VERTICAL_RED_TUBE_LOCATION =
            PowerRadar.id("block/radar_link/tube_vertical_red");
    private static final ResourceLocation VERTICAL_GREEN_TUBE_LOCATION =
            PowerRadar.id("block/radar_link/tube_vertical_green");
    private static final ResourceLocation HORIZONTAL_RED_GLOW_LOCATION =
            PowerRadar.id("block/radar_link/glow_horizontal_red");
    private static final ResourceLocation HORIZONTAL_GREEN_GLOW_LOCATION =
            PowerRadar.id("block/radar_link/glow_horizontal_green");
    private static final ResourceLocation VERTICAL_RED_GLOW_LOCATION =
            PowerRadar.id("block/radar_link/glow_vertical_red");
    private static final ResourceLocation VERTICAL_GREEN_GLOW_LOCATION =
            PowerRadar.id("block/radar_link/glow_vertical_green");

    private static final PartialModel HORIZONTAL_RED_TUBE = PartialModel.of(HORIZONTAL_RED_TUBE_LOCATION);
    private static final PartialModel HORIZONTAL_GREEN_TUBE = PartialModel.of(HORIZONTAL_GREEN_TUBE_LOCATION);
    private static final PartialModel VERTICAL_RED_TUBE = PartialModel.of(VERTICAL_RED_TUBE_LOCATION);
    private static final PartialModel VERTICAL_GREEN_TUBE = PartialModel.of(VERTICAL_GREEN_TUBE_LOCATION);
    private static final PartialModel HORIZONTAL_RED_GLOW = PartialModel.of(HORIZONTAL_RED_GLOW_LOCATION);
    private static final PartialModel HORIZONTAL_GREEN_GLOW = PartialModel.of(HORIZONTAL_GREEN_GLOW_LOCATION);
    private static final PartialModel VERTICAL_RED_GLOW = PartialModel.of(VERTICAL_RED_GLOW_LOCATION);
    private static final PartialModel VERTICAL_GREEN_GLOW = PartialModel.of(VERTICAL_GREEN_GLOW_LOCATION);

    public RadarLinkRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(HORIZONTAL_RED_TUBE_LOCATION));
        event.register(ModelResourceLocation.standalone(HORIZONTAL_GREEN_TUBE_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_RED_TUBE_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_GREEN_TUBE_LOCATION));
        event.register(ModelResourceLocation.standalone(HORIZONTAL_RED_GLOW_LOCATION));
        event.register(ModelResourceLocation.standalone(HORIZONTAL_GREEN_GLOW_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_RED_GLOW_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_GREEN_GLOW_LOCATION));
    }

    @Override
    public void render(
            RadarLinkBlockEntity link,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        float redGlow = link.redLampGlow(partialTick);
        float greenGlow = link.greenLampGlow(partialTick);
        if (redGlow < 0.125F && greenGlow < 0.125F) {
            return;
        }

        Direction facing = link.getBlockState().getValue(RadarLinkBlock.FACING);
        boolean vertical = facing.getAxis() == Direction.Axis.Y;

        poseStack.pushPose();
        applyBlockstateRotation(poseStack, link.getBlockState());
        if (redGlow >= 0.125F) {
            renderLamp(
                    vertical ? VERTICAL_RED_TUBE : HORIZONTAL_RED_TUBE,
                    vertical ? VERTICAL_RED_GLOW : HORIZONTAL_RED_GLOW,
                    link,
                    poseStack,
                    buffers,
                    redGlow);
        }
        if (greenGlow >= 0.125F) {
            renderLamp(
                    vertical ? VERTICAL_GREEN_TUBE : HORIZONTAL_GREEN_TUBE,
                    vertical ? VERTICAL_GREEN_GLOW : HORIZONTAL_GREEN_GLOW,
                    link,
                    poseStack,
                    buffers,
                    greenGlow);
        }
        poseStack.popPose();
    }

    // Повторяет x/y-повороты из blockstates/radar_link.json вокруг центра блока.
    // Y-угол инвертирован: положительный поворот PoseStack направлен противоположно
    // положительному Y-повороту baked blockstate.
    // При изменении ориентации основной модели нужно синхронно обновить эту таблицу.
    private static void applyBlockstateRotation(PoseStack poseStack, BlockState state) {
        Direction facing = state.getValue(RadarLinkBlock.FACING);
        float xRotation = facing == Direction.UP ? 180.0F : 0.0F;
        float yRotation;
        if (facing.getAxis() == Direction.Axis.Y) {
            yRotation = RadarLinkModelRotation.verticalModelYDegrees(state.getValue(RadarLinkBlock.MODEL_FACING));
        } else {
            yRotation = RadarLinkModelRotation.horizontalFacingYDegrees(facing);
        }

        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (xRotation != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(xRotation));
        }
        if (yRotation != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
        }
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static void renderLamp(
            PartialModel tubeModel,
            PartialModel glowModel,
            RadarLinkBlockEntity link,
            PoseStack poseStack,
            MultiBufferSource buffers,
            float glow
    ) {
        // Как у Create Display Link: tube полностью повторяет статическую колбу,
        // остаётся full-bright без изменения альфы и отключается целиком на пороге.
        CachedBuffers.partial(tubeModel, link.getBlockState())
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(poseStack, buffers.getBuffer(RenderType.translucent()));

        float shapedGlow = (float) (1.0D - 2.0D * Math.pow(glow - 0.75F, 2.0D));
        int color = (int) (200.0F * Mth.clamp(shapedGlow, -1.0F, 1.0F));
        SuperByteBuffer glowGeometry = CachedBuffers.partial(glowModel, link.getBlockState());
        glowGeometry
                .light(LightTexture.FULL_BRIGHT)
                .color(color, color, color, 255)
                .disableDiffuse()
                .renderInto(poseStack, buffers.getBuffer(RenderTypes.additive()));
    }
}
