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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Рисует только динамическое внутреннее свечение ламп Radar Link. */
public final class RadarLinkRenderer implements BlockEntityRenderer<RadarLinkBlockEntity> {
    private static final ResourceLocation HORIZONTAL_RED_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/radar_link/glow_horizontal_red");
    private static final ResourceLocation HORIZONTAL_GREEN_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/radar_link/glow_horizontal_green");
    private static final ResourceLocation VERTICAL_RED_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/radar_link/glow_vertical_red");
    private static final ResourceLocation VERTICAL_GREEN_LOCATION =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "block/radar_link/glow_vertical_green");

    private static final PartialModel HORIZONTAL_RED = PartialModel.of(HORIZONTAL_RED_LOCATION);
    private static final PartialModel HORIZONTAL_GREEN = PartialModel.of(HORIZONTAL_GREEN_LOCATION);
    private static final PartialModel VERTICAL_RED = PartialModel.of(VERTICAL_RED_LOCATION);
    private static final PartialModel VERTICAL_GREEN = PartialModel.of(VERTICAL_GREEN_LOCATION);

    public RadarLinkRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(HORIZONTAL_RED_LOCATION));
        event.register(ModelResourceLocation.standalone(HORIZONTAL_GREEN_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_RED_LOCATION));
        event.register(ModelResourceLocation.standalone(VERTICAL_GREEN_LOCATION));
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
        if (redGlow <= 0.0F && greenGlow <= 0.0F) {
            return;
        }

        Direction facing = link.getBlockState().getValue(RadarLinkBlock.FACING);
        boolean vertical = facing.getAxis() == Direction.Axis.Y;

        poseStack.pushPose();
        applyBlockstateRotation(poseStack, facing);
        if (redGlow > 0.0F) {
            renderGlow(vertical ? VERTICAL_RED : HORIZONTAL_RED, link, poseStack, buffers, redGlow);
        }
        if (greenGlow > 0.0F) {
            renderGlow(vertical ? VERTICAL_GREEN : HORIZONTAL_GREEN, link, poseStack, buffers, greenGlow);
        }
        poseStack.popPose();
    }

    // Повторяет x/y-повороты из blockstates/radar_link.json вокруг центра блока.
    // При изменении ориентации основной модели нужно синхронно обновить эту таблицу.
    private static void applyBlockstateRotation(PoseStack poseStack, Direction facing) {
        float xRotation = facing == Direction.UP ? 180.0F : 0.0F;
        float yRotation = switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (xRotation != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(xRotation));
        }
        if (yRotation != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
        }
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static void renderGlow(
            PartialModel model,
            RadarLinkBlockEntity link,
            PoseStack poseStack,
            MultiBufferSource buffers,
            float glow
    ) {
        int alpha = Math.round(255.0F * glow);
        SuperByteBuffer geometry = CachedBuffers.partial(model, link.getBlockState());
        geometry
                .light(LightTexture.FULL_BRIGHT)
                .color(255, 255, 255, alpha)
                .disableDiffuse()
                .renderInto(poseStack, buffers.getBuffer(RenderTypes.additive()));
    }
}
