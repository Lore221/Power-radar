package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.client.RadarMonitorClientState;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.onboard.OnboardCombinedModuleType;
import com.limbo2136.powerradar.onboard.OnboardModuleColumn;
import com.limbo2136.powerradar.onboard.OnboardModuleSlot;
import com.limbo2136.powerradar.onboard.OnboardModuleType;
import com.limbo2136.powerradar.onboard.OnboardPanelGeometry;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Управляет раскладкой слотов и отрисовкой всех съёмных приборов панели. */
final class OnboardPanelModuleRenderer {
    static final float PANEL_HEIGHT = 11.0F / 16.0F;
    private static final float PANEL_TILT_DEGREES = -22.5F;
    private static final float SOURCE_PIXEL = 1.0F / 16.0F;
    private final OnboardAnalogInstrumentRenderer analogRenderer;
    private final OnboardAttitudeIndicatorRenderer attitudeRenderer;

    OnboardPanelModuleRenderer(BlockEntityRendererProvider.Context context) {
        OnboardPartialModelRenderer partialRenderer = new OnboardPartialModelRenderer(
                context.getBlockRenderDispatcher().getModelRenderer());
        this.analogRenderer = new OnboardAnalogInstrumentRenderer(partialRenderer);
        this.attitudeRenderer = new OnboardAttitudeIndicatorRenderer(partialRenderer);
    }

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        OnboardAnalogInstrumentRenderer.registerAdditionalModels(event);
        OnboardAttitudeIndicatorRenderer.registerAdditionalModels(event);
    }

    // Собирает общие данные один раз, затем отрисовывает составные и одиночные приборы.
    void render(
            OnboardComputerBlockEntity computer,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Direction facing
    ) {
        ModuleInventory inventory = inspectModules(computer);
        if (!inventory.hasAnyModule) {
            return;
        }

        ModulePoseContext poseContext = createPoseContext(
                computer, facing, partialTick, inventory.hasAttitudeIndicator);
        OnboardInstrumentNeedleCache.NeedleAngles instrumentAngles = this.analogRenderer.sampleAngles(
                computer,
                partialTick,
                poseContext.worldPosition.y,
                poseContext.worldVelocity.length(),
                poseContext.worldAcceleration.length(),
                poseContext.worldVelocity.y);
        OnboardCompassRenderContext compassContext = inventory.hasCompass && poseContext.level != null
                ? OnboardCompassRenderContext.create(
                        computer,
                        poseContext.level,
                        poseContext.worldPosition,
                        poseContext.worldYawAtZeroNeedleRotation,
                        partialTick)
                : OnboardCompassRenderContext.unavailable();
        OnboardAttitudeIndicatorRenderer.RenderState attitudeState = inventory.hasAttitudeIndicator
                ? this.attitudeRenderer.prepare(computer, facing, poseContext.localWorldUp, buffers)
                : null;
        VertexConsumer moduleConsumer = buffers.getBuffer(RenderType.cutoutMipped());
        VertexConsumer translucentModuleConsumer = buffers.getBuffer(RenderType.translucent());

        renderCombinedModules(
                computer,
                poseStack,
                moduleConsumer,
                translucentModuleConsumer,
                packedLight,
                packedOverlay,
                facing,
                instrumentAngles);
        renderSingleSlotModules(
                computer,
                poseStack,
                moduleConsumer,
                packedLight,
                packedOverlay,
                facing,
                partialTick,
                inventory.combinedModuleSlotMask,
                instrumentAngles,
                compassContext,
                attitudeState);
    }

    // Определяет наличие дорогих приборов и слоты, визуально занятые составными модулями.
    private static ModuleInventory inspectModules(OnboardComputerBlockEntity computer) {
        boolean hasAnyModule = false;
        boolean hasCompass = false;
        boolean hasAttitudeIndicator = false;
        for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
            OnboardModuleType type = OnboardModuleType.fromStack(computer.module(slot));
            if (type != null) {
                hasAnyModule = true;
                hasCompass |= type == OnboardModuleType.COMPASS;
                hasAttitudeIndicator |= type == OnboardModuleType.ATTITUDE_INDICATOR;
            }
        }

        int combinedModuleSlotMask = 0;
        for (OnboardModuleColumn column : OnboardModuleColumn.values()) {
            if (computer.hasAssembledModule(column)) {
                combinedModuleSlotMask |= 1 << column.frontSlot().index();
                combinedModuleSlotMask |= 1 << column.rearSlot().index();
            }
        }
        return new ModuleInventory(
                hasAnyModule, hasCompass, hasAttitudeIndicator, combinedModuleSlotMask);
    }

    private void renderCombinedModules(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer opaqueConsumer,
            VertexConsumer translucentConsumer,
            int packedLight,
            int packedOverlay,
            Direction facing,
            OnboardInstrumentNeedleCache.NeedleAngles angles
    ) {
        for (OnboardModuleColumn column : OnboardModuleColumn.values()) {
            OnboardCombinedModuleType type = computer.assembledModule(column);
            if (type == null) {
                continue;
            }
            poseStack.pushPose();
            applyColumnTransform(poseStack, facing, column);
            if (type == OnboardCombinedModuleType.ACCELEROMETER) {
                this.analogRenderer.renderAccelerometer(
                        computer,
                        poseStack,
                        opaqueConsumer,
                        translucentConsumer,
                        packedLight,
                        packedOverlay,
                        angles.accelerometerSourcePixels());
            } else if (type == OnboardCombinedModuleType.VARIOMETER) {
                this.analogRenderer.renderVariometer(
                        computer,
                        poseStack,
                        opaqueConsumer,
                        translucentConsumer,
                        packedLight,
                        packedOverlay,
                        angles.variometerSourcePixels());
            }
            poseStack.popPose();
        }
    }

    private void renderSingleSlotModules(
            OnboardComputerBlockEntity computer,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            Direction facing,
            float partialTick,
            int combinedModuleSlotMask,
            OnboardInstrumentNeedleCache.NeedleAngles angles,
            OnboardCompassRenderContext compassContext,
            OnboardAttitudeIndicatorRenderer.RenderState attitudeState
    ) {
        for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
            if ((combinedModuleSlotMask & 1 << slot.index()) != 0) {
                continue;
            }
            OnboardModuleType type = OnboardModuleType.fromStack(computer.module(slot));
            if (type == null) {
                continue;
            }

            poseStack.pushPose();
            applyModuleTransform(poseStack, facing, slot);
            if (type == OnboardModuleType.ATTITUDE_INDICATOR) {
                this.attitudeRenderer.render(
                        computer, poseStack, consumer, packedLight, packedOverlay, attitudeState);
                poseStack.popPose();
                continue;
            }

            this.analogRenderer.renderSingleModule(
                    type,
                    computer.module(slot),
                    computer,
                    poseStack,
                    consumer,
                    packedLight,
                    packedOverlay,
                    partialTick,
                    angles,
                    compassContext);
            poseStack.popPose();
        }
    }

    private static void applyColumnTransform(
            PoseStack poseStack,
            Direction facing,
            OnboardModuleColumn column
    ) {
        applyPanelTransform(poseStack, facing);
        poseStack.translate(
                column.panelX() / 16.0D,
                PANEL_HEIGHT,
                OnboardModuleColumn.CENTER_DEPTH_PIXELS / 16.0D);
        poseStack.scale(
                (float) OnboardPanelGeometry.MODULE_SCALE,
                (float) OnboardPanelGeometry.MODULE_SCALE,
                (float) OnboardPanelGeometry.MODULE_SCALE);
        poseStack.translate(0.0D, -OnboardPanelGeometry.MODULE_SINK_SOURCE_PIXELS * SOURCE_PIXEL, 0.0D);
    }

    private static void applyModuleTransform(PoseStack poseStack, Direction facing, OnboardModuleSlot slot) {
        applyPanelTransform(poseStack, facing);
        poseStack.translate(slot.panelX() / 16.0D, PANEL_HEIGHT, slot.panelDepth() / 16.0D);
        poseStack.scale(
                (float) OnboardPanelGeometry.MODULE_SCALE,
                (float) OnboardPanelGeometry.MODULE_SCALE,
                (float) OnboardPanelGeometry.MODULE_SCALE);
        poseStack.translate(0.0D, -OnboardPanelGeometry.MODULE_SINK_SOURCE_PIXELS * SOURCE_PIXEL, 0.0D);
    }

    private static void applyPanelTransform(PoseStack poseStack, Direction facing) {
        // Порядок матриц: поворот блока вокруг центра, затем наклон авторской панели вокруг её высоты.
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(modelYawDegrees(facing)));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        poseStack.translate(0.0D, PANEL_HEIGHT, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(PANEL_TILT_DEGREES));
        poseStack.translate(0.0D, -PANEL_HEIGHT, 0.0D);
    }

    static float modelYawDegrees(Direction facing) {
        return Mth.wrapDegrees(180.0F - facing.toYRot());
    }

    // Собирает один снимок движения и необязательную ориентацию для всех приборов блока.
    private static ModulePoseContext createPoseContext(
            OnboardComputerBlockEntity computer,
            Direction facing,
            float partialTick,
            boolean includeOrientation
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return new ModulePoseContext(
                    null,
                    Vec3.atCenterOf(computer.getBlockPos()),
                    Vec3.ZERO,
                    Vec3.ZERO,
                    RadarGeometry.yawDegrees(facing),
                    Vec3.ZERO);
        }

        RadarMonitorClientState.Entry clientState = RadarMonitorClientState.get(computer.getBlockPos());
        RadarMonitorBlockPosePayload.MonitorPose monitorPose = clientState == null
                ? null
                : clientState.interpolatedMonitorPose(partialTick);
        Vec3 velocity = clientState == null
                ? Vec3.ZERO
                : clientState.interpolatedMonitorVelocity(partialTick);
        Vec3 acceleration = clientState == null
                ? Vec3.ZERO
                : clientState.interpolatedMonitorAcceleration(partialTick);
        Vec3 position;
        float zeroNeedleYaw;
        if (monitorPose == null) {
            position = Vec3.atCenterOf(computer.getBlockPos());
            zeroNeedleYaw = RadarGeometry.yawDegrees(facing);
        } else {
            position = new Vec3(monitorPose.originX(), monitorPose.originY(), monitorPose.originZ());
            zeroNeedleYaw = RadarGeometry.normalizeDegrees(monitorPose.yawDegrees() + 180.0F);
        }
        Vec3 localWorldUp = includeOrientation
                ? SableRadarIntegration.interpolatedLocalDirection(
                        level, computer.getBlockPos(), new Vec3(0.0D, 1.0D, 0.0D), partialTick)
                : Vec3.ZERO;
        return new ModulePoseContext(
                level, position, velocity, acceleration, zeroNeedleYaw, localWorldUp);
    }

    private record ModuleInventory(
            boolean hasAnyModule,
            boolean hasCompass,
            boolean hasAttitudeIndicator,
            int combinedModuleSlotMask
    ) {
    }

    private record ModulePoseContext(
            ClientLevel level,
            Vec3 worldPosition,
            Vec3 worldVelocity,
            Vec3 worldAcceleration,
            float worldYawAtZeroNeedleRotation,
            Vec3 localWorldUp
    ) {
    }
}
