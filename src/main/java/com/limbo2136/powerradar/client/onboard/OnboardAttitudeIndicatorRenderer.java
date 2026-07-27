package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.client.instrument.AttitudeIndicatorAngleCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Отрисовывает компактный текстурный авиагоризонт OnBoard Computer. */
final class OnboardAttitudeIndicatorRenderer {
    private static final float SOURCE_PIXEL = 1.0F / 16.0F;

    // Границы повторяют верхнюю грань элемента "window" из attitude_indicator.json.
    private static final float WINDOW_MIN_X = 2.0F * SOURCE_PIXEL;
    private static final float WINDOW_MIN_Z = 2.0F * SOURCE_PIXEL;
    private static final float WINDOW_MAX_X = 14.0F * SOURCE_PIXEL;
    private static final float WINDOW_MAX_Z = 14.0F * SOURCE_PIXEL;

    // Полоса лежит на полпикселя ниже верхней грани window и не перекрывает его стрелку.
    // Все UV-константы ниже заданы в пикселях авторской текстуры 21x52 и нормализуются при записи вершины.
    private static final float STRIP_QUAD_Y = 4.5F * SOURCE_PIXEL;
    private static final float STRIP_TEXTURE_WIDTH = 21.0F;
    private static final float STRIP_TEXTURE_HEIGHT = 52.0F;
    private static final float STRIP_U_CENTER_PIXELS = 10.5F;
    private static final float STRIP_SAMPLE_RADIUS_PIXELS = 6.5F;
    private static final float STRIP_LEVEL_V_CENTER_PIXELS = 19.5F;
    private static final float STRIP_PIXELS_PER_QUARTER_TURN = 13.0F;

    private static final ResourceLocation HOUSING_MODEL_LOCATION = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "block/on_board_modules/attitude_indicator");
    private static final ResourceLocation STRIP_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/block/on_board_modules/attitude_strip.png");
    private static final PartialModel HOUSING_MODEL = PartialModel.of(HOUSING_MODEL_LOCATION);

    private final OnboardPartialModelRenderer partialRenderer;

    OnboardAttitudeIndicatorRenderer(OnboardPartialModelRenderer partialRenderer) {
        this.partialRenderer = partialRenderer;
    }

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(HOUSING_MODEL_LOCATION));
    }

    RenderState prepare(
            OnboardComputerBlockEntity computer,
            Direction facing,
            Vec3 localWorldUp,
            MultiBufferSource buffers
    ) {
        return new RenderState(
                AttitudeIndicatorAngleCache.sample(computer, facing, localWorldUp),
                buffers.getBuffer(RenderType.entityCutoutNoCull(STRIP_TEXTURE)));
    }

    // Применяет ориентацию модели, рисует полосу и затем неподвижный корпус со стрелкой.
    void render(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer moduleConsumer,
            int packedLight,
            int packedOverlay,
            RenderState state
    ) {
        poseStack.pushPose();
        // Авторская коррекция: сторона north корпуса направлена вперёд и противоположна ориентации модулей.
        // Если направление модели housing изменится, корректировать нужно именно этот поворот на 180°.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        renderStrip(poseStack, state.stripConsumer, packedLight, packedOverlay, state.transform);
        this.partialRenderer.render(
                HOUSING_MODEL, computer, poseStack, moduleConsumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    // Прямая индикация «ВсВС»: положительный крен вращает UV в плюс, а положительный
    // тангаж уменьшает V центра выборки и сдвигает видимую часть вдоль полосы.
    private static void renderStrip(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            AttitudeIndicatorAngleCache.Transform transform
    ) {
        float angleRadians = (float) Math.toRadians(transform.bankDegrees());
        float cosine = Mth.cos(angleRadians);
        float sine = Mth.sin(angleRadians);
        float vCenter = STRIP_LEVEL_V_CENTER_PIXELS
                - transform.pitchDegrees() / 90.0F * STRIP_PIXELS_PER_QUARTER_TURN;
        PoseStack.Pose pose = poseStack.last();

        emitStripVertex(pose, consumer, WINDOW_MIN_X, STRIP_QUAD_Y, WINDOW_MIN_Z,
                -1.0F, -1.0F, cosine, sine, vCenter, packedLight, packedOverlay);
        emitStripVertex(pose, consumer, WINDOW_MIN_X, STRIP_QUAD_Y, WINDOW_MAX_Z,
                -1.0F, 1.0F, cosine, sine, vCenter, packedLight, packedOverlay);
        emitStripVertex(pose, consumer, WINDOW_MAX_X, STRIP_QUAD_Y, WINDOW_MAX_Z,
                1.0F, 1.0F, cosine, sine, vCenter, packedLight, packedOverlay);
        emitStripVertex(pose, consumer, WINDOW_MAX_X, STRIP_QUAD_Y, WINDOW_MIN_Z,
                1.0F, -1.0F, cosine, sine, vCenter, packedLight, packedOverlay);
    }

    private static void emitStripVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            float horizontalSign,
            float verticalSign,
            float cosine,
            float sine,
            float vCenter,
            int packedLight,
            int packedOverlay
    ) {
        // Знаки выбирают угол квадратной UV-выборки 13x13; затем выборка вращается вокруг U=10.5.
        float sourceX = horizontalSign * STRIP_SAMPLE_RADIUS_PIXELS;
        float sourceY = verticalSign * STRIP_SAMPLE_RADIUS_PIXELS;
        float rotatedX = sourceX * cosine - sourceY * sine;
        float rotatedY = sourceX * sine + sourceY * cosine;
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(
                        (STRIP_U_CENTER_PIXELS + rotatedX) / STRIP_TEXTURE_WIDTH,
                        (vCenter + rotatedY) / STRIP_TEXTURE_HEIGHT)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Общие данные режима, вычисленные один раз за отрисовку блока. */
    record RenderState(
            AttitudeIndicatorAngleCache.Transform transform,
            VertexConsumer stripConsumer
    ) {
    }
}
