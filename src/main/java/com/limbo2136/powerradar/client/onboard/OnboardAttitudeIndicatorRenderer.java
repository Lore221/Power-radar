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
    private static final float MODULE_TEXTURE_SIZE = 64.0F;
    private static final float WINDOW_MIN_U = 0.0F / MODULE_TEXTURE_SIZE;
    private static final float WINDOW_MAX_U = 23.0F / MODULE_TEXTURE_SIZE;
    private static final float ATTITUDE_WINDOW_MIN_V = 0.0F / MODULE_TEXTURE_SIZE;
    private static final float ATTITUDE_WINDOW_MAX_V = 23.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_WINDOW_MIN_V = 41.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_WINDOW_MAX_V = 64.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_POINTER_MIN_U = 28.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_POINTER_MAX_U = 39.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_POINTER_MIN_V = 61.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_POINTER_MAX_V = 64.0F / MODULE_TEXTURE_SIZE;
    private static final float KAG_SURFACE_Y = 5.01F * SOURCE_PIXEL;
    private static final float KAG_POINTER_Y = 5.02F * SOURCE_PIXEL;
    private static final float KAG_POINTER_WIDTH = (WINDOW_MAX_X - WINDOW_MIN_X) * 11.0F / 23.0F;
    private static final float KAG_POINTER_HEIGHT = (WINDOW_MAX_Z - WINDOW_MIN_Z) * 3.0F / 23.0F;

    private static final ResourceLocation HOUSING_MODEL_LOCATION =
            PowerRadar.id("block/on_board_modules/attitude_indicator");
    private static final ResourceLocation STRIP_TEXTURE =
            PowerRadar.id("textures/block/on_board_modules/attitude_strip.png");
    private static final ResourceLocation MODULE_TEXTURE =
            PowerRadar.id("textures/block/on_board_modules/modules_4.png");
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
                buffers.getBuffer(RenderType.entityCutoutNoCull(STRIP_TEXTURE)),
                buffers.getBuffer(RenderType.entityCutoutNoCull(MODULE_TEXTURE)));
    }

    // Применяет ориентацию модели, рисует полосу и затем неподвижный корпус со стрелкой.
    void render(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer moduleConsumer,
            int packedLight,
            int packedOverlay,
            RenderState state,
            boolean kagMode
    ) {
        poseStack.pushPose();
        // Авторская коррекция: сторона north корпуса направлена вперёд и противоположна ориентации модулей.
        // Если направление модели housing изменится, корректировать нужно именно этот поворот на 180°.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        renderStrip(
                poseStack, state.stripConsumer, packedLight, packedOverlay, state.transform, kagMode);
        this.partialRenderer.render(
                HOUSING_MODEL, computer, poseStack, moduleConsumer, packedLight, packedOverlay);
        renderWindow(poseStack, state, packedLight, packedOverlay, kagMode);
        poseStack.popPose();
    }

    // Прямая индикация «ВсВС»: положительный крен вращает UV в плюс, а положительный
    // тангаж уменьшает V центра выборки и сдвигает видимую часть вдоль полосы.
    private static void renderStrip(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            AttitudeIndicatorAngleCache.Transform transform,
            boolean kagMode
    ) {
        float angleRadians = kagMode ? 0.0F : (float) Math.toRadians(transform.bankDegrees());
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

    private static void renderWindow(
            PoseStack poseStack,
            RenderState state,
            int packedLight,
            int packedOverlay,
            boolean kagMode
    ) {
        emitTopQuad(
                poseStack.last(), state.moduleTextureConsumer,
                WINDOW_MIN_X, WINDOW_MIN_Z, WINDOW_MAX_X, WINDOW_MAX_Z, KAG_SURFACE_Y,
                WINDOW_MIN_U,
                kagMode ? KAG_WINDOW_MIN_V : ATTITUDE_WINDOW_MIN_V,
                WINDOW_MAX_U,
                kagMode ? KAG_WINDOW_MAX_V : ATTITUDE_WINDOW_MAX_V,
                packedLight, packedOverlay);

        if (!kagMode) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.transform.bankDegrees()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        emitTopQuad(
                poseStack.last(), state.moduleTextureConsumer,
                0.5F - KAG_POINTER_WIDTH * 0.5F,
                0.5F - KAG_POINTER_HEIGHT * 0.5F,
                0.5F + KAG_POINTER_WIDTH * 0.5F,
                0.5F + KAG_POINTER_HEIGHT * 0.5F,
                KAG_POINTER_Y,
                KAG_POINTER_MIN_U, KAG_POINTER_MIN_V, KAG_POINTER_MAX_U, KAG_POINTER_MAX_V,
                packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void emitTopQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float minX,
            float minZ,
            float maxX,
            float maxZ,
            float y,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int packedLight,
            int packedOverlay
    ) {
        emitTopVertex(pose, consumer, minX, y, minZ, minU, minV, packedLight, packedOverlay);
        emitTopVertex(pose, consumer, minX, y, maxZ, minU, maxV, packedLight, packedOverlay);
        emitTopVertex(pose, consumer, maxX, y, maxZ, maxU, maxV, packedLight, packedOverlay);
        emitTopVertex(pose, consumer, maxX, y, minZ, maxU, minV, packedLight, packedOverlay);
    }

    private static void emitTopVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight,
            int packedOverlay
    ) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
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
            VertexConsumer stripConsumer,
            VertexConsumer moduleTextureConsumer
    ) {
    }
}
