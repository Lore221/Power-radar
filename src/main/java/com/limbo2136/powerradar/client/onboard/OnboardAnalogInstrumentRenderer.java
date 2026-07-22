package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.onboard.OnboardModuleType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Рисует основания и подвижные части стрелочных и линейных приборов. */
final class OnboardAnalogInstrumentRenderer {
    private static final float SOURCE_PIXEL = 1.0F / 16.0F;
    private static final float SPEEDOMETER_MAX_BLOCKS_PER_SECOND = 60.0F;
    private static final float SPEEDOMETER_MIN_ANGLE_DEGREES = 90.0F;
    private static final float SPEEDOMETER_MAX_ANGLE_DEGREES = -90.0F;
    private static final float ACCELEROMETER_MAX_BLOCKS_PER_SECOND_SQUARED = 55.0F;
    private static final float ACCELEROMETER_NEEDLE_TRAVEL_PIXELS = 24.0F;
    // Пять рисок образуют шесть интервалов по 5 блоков/с от нуля до каждого края.
    private static final float VARIOMETER_MAX_BLOCKS_PER_SECOND = 30.0F;
    private static final float VARIOMETER_NEEDLE_TRAVEL_PIXELS = 12.0F;

    private static final ResourceLocation COMPASS_MODEL_LOCATION = modelLocation("compass");
    private static final ResourceLocation COMPASS_NEEDLE_MODEL_LOCATION = modelLocation("needle");
    private static final ResourceLocation ALTIMETER_MODEL_LOCATION = modelLocation("altimeter");
    private static final ResourceLocation ALTIMETER_NEEDLE_MODEL_LOCATION = modelLocation("needle_2");
    private static final ResourceLocation SPEEDOMETER_MODEL_LOCATION = modelLocation("speedometer");
    private static final ResourceLocation SPEEDOMETER_NEEDLE_MODEL_LOCATION = modelLocation("needle_3");
    private static final ResourceLocation CLOCK_MODEL_LOCATION = modelLocation("clock");
    private static final ResourceLocation ACCELEROMETER_MODEL_LOCATION = modelLocation("accelerometer");
    private static final ResourceLocation ACCELEROMETER_NEEDLE_MODEL_LOCATION = modelLocation("needle_4");
    private static final ResourceLocation VARIOMETER_MODEL_LOCATION = modelLocation("variometer");
    private static final ResourceLocation VARIOMETER_NEEDLE_MODEL_LOCATION = modelLocation("needle_5");

    private static final PartialModel COMPASS_MODEL = PartialModel.of(COMPASS_MODEL_LOCATION);
    private static final PartialModel COMPASS_NEEDLE_MODEL = PartialModel.of(COMPASS_NEEDLE_MODEL_LOCATION);
    private static final PartialModel ALTIMETER_MODEL = PartialModel.of(ALTIMETER_MODEL_LOCATION);
    private static final PartialModel ALTIMETER_NEEDLE_MODEL = PartialModel.of(ALTIMETER_NEEDLE_MODEL_LOCATION);
    private static final PartialModel SPEEDOMETER_MODEL = PartialModel.of(SPEEDOMETER_MODEL_LOCATION);
    private static final PartialModel SPEEDOMETER_NEEDLE_MODEL = PartialModel.of(SPEEDOMETER_NEEDLE_MODEL_LOCATION);
    private static final PartialModel CLOCK_MODEL = PartialModel.of(CLOCK_MODEL_LOCATION);
    private static final PartialModel ACCELEROMETER_MODEL = PartialModel.of(ACCELEROMETER_MODEL_LOCATION);
    private static final PartialModel ACCELEROMETER_NEEDLE_MODEL = PartialModel.of(
            ACCELEROMETER_NEEDLE_MODEL_LOCATION);
    private static final PartialModel VARIOMETER_MODEL = PartialModel.of(VARIOMETER_MODEL_LOCATION);
    private static final PartialModel VARIOMETER_NEEDLE_MODEL = PartialModel.of(VARIOMETER_NEEDLE_MODEL_LOCATION);

    private final OnboardPartialModelRenderer partialRenderer;

    OnboardAnalogInstrumentRenderer(OnboardPartialModelRenderer partialRenderer) {
        this.partialRenderer = partialRenderer;
    }

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(COMPASS_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(COMPASS_NEEDLE_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(ALTIMETER_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(ALTIMETER_NEEDLE_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(SPEEDOMETER_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(SPEEDOMETER_NEEDLE_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(CLOCK_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(ACCELEROMETER_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(ACCELEROMETER_NEEDLE_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(VARIOMETER_MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(VARIOMETER_NEEDLE_MODEL_LOCATION));
    }

    // Рассчитывает целевые положения и применяет общую механическую инерцию стрелок.
    OnboardInstrumentNeedleCache.NeedleAngles sampleAngles(
            OnboardComputerBlockEntity computer,
            float partialTick,
            double worldHeight,
            double speed,
            double acceleration,
            double verticalSpeed
    ) {
        return OnboardInstrumentNeedleCache.sample(
                computer,
                partialTick,
                altimeterNeedleRotation(worldHeight),
                speedometerNeedleRotation(speed),
                accelerometerNeedleOffset(acceleration),
                variometerNeedleOffset(verticalSpeed));
    }

    void renderSingleModule(
            OnboardModuleType type,
            ItemStack stack,
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            float partialTick,
            OnboardInstrumentNeedleCache.NeedleAngles angles,
            OnboardCompassRenderContext compassContext
    ) {
        PartialModel baseModel;
        PartialModel needleModel;
        float needleRotation;
        if (type == OnboardModuleType.ALTIMETER) {
            baseModel = ALTIMETER_MODEL;
            needleModel = ALTIMETER_NEEDLE_MODEL;
            needleRotation = angles.altimeterDegrees();
        } else if (type == OnboardModuleType.SPEEDOMETER) {
            baseModel = SPEEDOMETER_MODEL;
            needleModel = SPEEDOMETER_NEEDLE_MODEL;
            needleRotation = angles.speedometerDegrees();
        } else if (type == OnboardModuleType.CLOCK) {
            baseModel = CLOCK_MODEL;
            needleModel = SPEEDOMETER_NEEDLE_MODEL;
            needleRotation = OnboardClockTimeCache.hourHandAngle(partialTick);
        } else {
            baseModel = COMPASS_MODEL;
            needleModel = COMPASS_NEEDLE_MODEL;
            needleRotation = compassContext.needleRotation(stack);
        }

        this.partialRenderer.render(
                baseModel, computer, poseStack, consumer, packedLight, packedOverlay);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(needleRotation));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        this.partialRenderer.render(
                needleModel, computer, poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    // Положение столбца и масштаб уже применены рендерером панели.
    void renderAccelerometer(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer opaqueConsumer,
            VertexConsumer translucentConsumer,
            int packedLight,
            int packedOverlay,
            float needleOffsetPixels
    ) {
        this.partialRenderer.render(
                ACCELEROMETER_MODEL, computer, poseStack, opaqueConsumer, packedLight, packedOverlay);
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, needleOffsetPixels * SOURCE_PIXEL);
        this.partialRenderer.renderTranslucent(
                ACCELEROMETER_NEEDLE_MODEL,
                computer,
                poseStack,
                translucentConsumer,
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    // Нулевая риска задана в needle_5; подъём двигает её к переднему, верхнему краю панели.
    void renderVariometer(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer opaqueConsumer,
            VertexConsumer translucentConsumer,
            int packedLight,
            int packedOverlay,
            float needleOffsetPixels
    ) {
        this.partialRenderer.render(
                VARIOMETER_MODEL, computer, poseStack, opaqueConsumer, packedLight, packedOverlay);
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, needleOffsetPixels * SOURCE_PIXEL);
        this.partialRenderer.renderTranslucent(
                VARIOMETER_NEEDLE_MODEL,
                computer,
                poseStack,
                translucentConsumer,
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    private static float altimeterNeedleRotation(double worldHeight) {
        return (float) (180.0D - worldHeight * (360.0D / 16.0D));
    }

    private static float speedometerNeedleRotation(double blocksPerSecond) {
        float fraction = Mth.clamp(
                (float) (blocksPerSecond / SPEEDOMETER_MAX_BLOCKS_PER_SECOND), 0.0F, 1.0F);
        return Mth.lerp(fraction, SPEEDOMETER_MIN_ANGLE_DEGREES, SPEEDOMETER_MAX_ANGLE_DEGREES);
    }

    private static float accelerometerNeedleOffset(double blocksPerSecondSquared) {
        float fraction = Mth.clamp(
                (float) (blocksPerSecondSquared / ACCELEROMETER_MAX_BLOCKS_PER_SECOND_SQUARED),
                0.0F,
                1.0F);
        return fraction * ACCELEROMETER_NEEDLE_TRAVEL_PIXELS;
    }

    private static float variometerNeedleOffset(double verticalBlocksPerSecond) {
        float fraction = Mth.clamp(
                (float) (verticalBlocksPerSecond / VARIOMETER_MAX_BLOCKS_PER_SECOND),
                -1.0F,
                1.0F);
        return fraction * VARIOMETER_NEEDLE_TRAVEL_PIXELS;
    }

    private static ResourceLocation modelLocation(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                PowerRadar.MOD_ID, "block/on_board_modules/" + name);
    }
}
