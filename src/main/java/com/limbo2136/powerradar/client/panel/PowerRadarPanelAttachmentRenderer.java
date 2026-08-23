package com.limbo2136.powerradar.client.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.client.RadarMonitorRenderer;
import com.limbo2136.powerradar.client.instrument.AttitudeIndicatorAngleCache;
import com.limbo2136.powerradar.client.radarlink.RadarLinkClientOutlineHandler;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.panel.AttitudeIndicatorPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.LogicDockPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarDisplayPanelAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Клиентская отрисовка корпусов и карты модулей электрического щитка. */
public final class PowerRadarPanelAttachmentRenderer {
    private static final double FRAME_DEPTH = 1.0D / 512.0D;
    private static final double FRAME_Z = 15.05D / 16.0D;
    private static final double DISPLAY_FRAME_MIN_X = 1.5D / 16.0D;
    private static final double DISPLAY_FRAME_MAX_X = 14.5D / 16.0D;
    private static final double DISPLAY_FRAME_MIN_Y = 0.5D / 16.0D;
    private static final double DISPLAY_FRAME_MAX_Y = 14.5D / 16.0D;
    private static final double LOGIC_DOCK_FRAME_MIN_X = 1.5D / 16.0D;
    private static final double LOGIC_DOCK_FRAME_MAX_X = 8.0D / 16.0D;
    private static final double LOGIC_DOCK_FRAME_MIN_Y = 1.5D / 16.0D;
    private static final double LOGIC_DOCK_FRAME_MAX_Y = 14.5D / 16.0D;
    private static final ResourceLocation RADAR_DISPLAY_LOCATION =
            PowerRadar.id("block/electrical_panel/radar_display");
    private static final ResourceLocation ATTITUDE_INDICATOR_LOCATION =
            PowerRadar.id("block/electrical_panel/attitude_indicator");
    private static final ResourceLocation ATTITUDE_SPHERE_LOCATION =
            PowerRadar.id("block/electrical_panel/attitude_sphere");
    private static final ResourceLocation LOGIC_DOCK_LOCATION =
            PowerRadar.id("block/electrical_panel/logic_dock");
    private static final ResourceLocation LOGIC_DOCK_TARGETING_CARD_LOCATION =
            PowerRadar.id("block/electrical_panel/targeting_card");
    private static final ResourceLocation LOGIC_DOCK_DISPLAY_CARD_LOCATION =
            PowerRadar.id("block/electrical_panel/display_card");
    private static final ResourceLocation LOGIC_DOCK_ALLOWLIST_CARD_LOCATION =
            PowerRadar.id("block/electrical_panel/allowlist_card");
    private static final PartialModel RADAR_DISPLAY = PartialModel.of(RADAR_DISPLAY_LOCATION);
    private static final PartialModel ATTITUDE_INDICATOR = PartialModel.of(ATTITUDE_INDICATOR_LOCATION);
    private static final PartialModel ATTITUDE_SPHERE = PartialModel.of(ATTITUDE_SPHERE_LOCATION);
    private static final ResourceLocation ATTITUDE_TEXTURE =
            PowerRadar.id("textures/block/on_board_modules/modules_4.png");
    private static final float TEXTURE_SIZE = 64.0F;
    private static final float WINDOW_MIN_U = 0.0F / TEXTURE_SIZE;
    private static final float WINDOW_MAX_U = 23.0F / TEXTURE_SIZE;
    private static final float ATTITUDE_WINDOW_MIN_V = 0.0F / TEXTURE_SIZE;
    private static final float ATTITUDE_WINDOW_MAX_V = 23.0F / TEXTURE_SIZE;
    private static final float KAG_WINDOW_MIN_V = 41.0F / TEXTURE_SIZE;
    private static final float KAG_WINDOW_MAX_V = 64.0F / TEXTURE_SIZE;
    private static final float KAG_POINTER_MIN_U = 28.0F / TEXTURE_SIZE;
    private static final float KAG_POINTER_MAX_U = 39.0F / TEXTURE_SIZE;
    private static final float KAG_POINTER_MIN_V = 61.0F / TEXTURE_SIZE;
    private static final float KAG_POINTER_MAX_V = 64.0F / TEXTURE_SIZE;
    private static final float PANEL_WINDOW_MIN = 4.0F / 16.0F;
    private static final float PANEL_WINDOW_MAX = 12.0F / 16.0F;
    private static final float PANEL_WINDOW_Z = 6.99F / 16.0F;
    private static final float PANEL_POINTER_WIDTH = (PANEL_WINDOW_MAX - PANEL_WINDOW_MIN) * 11.0F / 23.0F;
    private static final float PANEL_POINTER_HEIGHT = (PANEL_WINDOW_MAX - PANEL_WINDOW_MIN) * 3.0F / 23.0F;
    private static final PartialModel LOGIC_DOCK = PartialModel.of(LOGIC_DOCK_LOCATION);
    private static final PartialModel LOGIC_DOCK_TARGETING_CARD =
            PartialModel.of(LOGIC_DOCK_TARGETING_CARD_LOCATION);
    private static final PartialModel LOGIC_DOCK_DISPLAY_CARD =
            PartialModel.of(LOGIC_DOCK_DISPLAY_CARD_LOCATION);
    private static final PartialModel LOGIC_DOCK_ALLOWLIST_CARD =
            PartialModel.of(LOGIC_DOCK_ALLOWLIST_CARD_LOCATION);
    // Центр OBJ — [0, 8, 3.3], а найденный в модели корпуса центр окна — [8, 8, 11.3].
    private static final double SPHERE_SOURCE_PIVOT_Y = 8.0D / 16.0D;
    private static final double SPHERE_SOURCE_PIVOT_Z = 3.3D / 16.0D;
    private static final double SPHERE_RENDER_CENTER_Z = 11.3D / 16.0D;
    // Сфера сохраняет мировой горизонт, поэтому визуально вращается против крена и тангажа щитка.
    private static final float SPHERE_BANK_SIGN = -1.0F;
    private static final float SPHERE_PITCH_SIGN = -1.0F;
    private static final RadarMonitorRenderer MONITOR_RENDERER =
            new RadarMonitorRenderer(null);

    private PowerRadarPanelAttachmentRenderer() {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(RADAR_DISPLAY_LOCATION));
        event.register(ModelResourceLocation.standalone(ATTITUDE_INDICATOR_LOCATION));
        event.register(ModelResourceLocation.standalone(ATTITUDE_SPHERE_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_TARGETING_CARD_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_DISPLAY_CARD_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_ALLOWLIST_CARD_LOCATION));
    }

    public static void renderDisplay(
            RadarDisplayPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        attachment.transformPose(poseStack, panel);
        CachedBuffers.partial(RADAR_DISPLAY, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
        renderNetworkFrame(
                attachment.networkId(), poseStack, buffers,
                DISPLAY_FRAME_MIN_X, DISPLAY_FRAME_MIN_Y,
                DISPLAY_FRAME_MAX_X, DISPLAY_FRAME_MAX_Y);

        // Окно модели занимает x/y 3..13 и лежит на z=10; карта рисуется в этом квадрате.
        poseStack.translate(3.0D / 16.0D, 3.0D / 16.0D, 2.0D / 16.0D);
        poseStack.scale(10.0F / 16.0F, 10.0F / 16.0F, 1.0F);
        MONITOR_RENDERER.renderPanelSurface(
                panel.getBlockPos(), partialTicks, poseStack, buffers, packedLight);
    }

    public static void renderLogicDock(
            LogicDockPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = panel.getBlockState().getValue(ElectricalPanelBlock.FACING);
        int cardLight = packedLight;
        if (panel.getLevel() != null) {
            // Ячейка щитка затемнена его корпусом, поэтому карты берут внешний свет
            // из блока непосредственно перед лицевой стороной панели.
            cardLight = LevelRenderer.getLightColor(
                    panel.getLevel(),
                    panel.getBlockPos().relative(facing)
            );
        }

        attachment.transformPose(poseStack, panel);
        CachedBuffers.partial(LOGIC_DOCK, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
        renderNetworkFrame(
                attachment.networkId(), poseStack, buffers,
                LOGIC_DOCK_FRAME_MIN_X, LOGIC_DOCK_FRAME_MIN_Y,
                LOGIC_DOCK_FRAME_MAX_X, LOGIC_DOCK_FRAME_MAX_Y);

        if (attachment.hasCard(0)) {
            renderLogicDockCard(
                    LOGIC_DOCK_TARGETING_CARD, panel, poseStack, buffers, cardLight, packedOverlay);
        }
        if (attachment.hasCard(1)) {
            renderLogicDockCard(
                    LOGIC_DOCK_DISPLAY_CARD, panel, poseStack, buffers, cardLight, packedOverlay);
        }
        if (attachment.hasCard(2)) {
            renderLogicDockCard(
                    LOGIC_DOCK_ALLOWLIST_CARD, panel, poseStack, buffers, cardLight, packedOverlay);
        }
    }

    private static void renderLogicDockCard(
            PartialModel card,
            ElectricalPanelBlockEntity panel,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        CachedBuffers.partial(card, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    public static void renderAttitudeIndicator(
            AttitudeIndicatorPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = panel.getBlockState().getValue(ElectricalPanelBlock.FACING);
        AttitudeIndicatorAngleCache.Transform transform = AttitudeIndicatorAngleCache.sample(
                attachment,
                facing,
                SableRadarIntegration.interpolatedLocalDirection(
                        panel.getLevel(),
                        panel.getBlockPos(),
                        new Vec3(0.0D, 1.0D, 0.0D),
                        partialTicks));

        attachment.transformPose(poseStack, panel);
        renderAttitudeSphere(
                panel, poseStack, buffers, packedLight, packedOverlay, transform, attachment.kagMode());
        CachedBuffers.partial(ATTITUDE_INDICATOR, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
        renderPanelWindow(
                poseStack, buffers, packedLight, packedOverlay, transform, attachment.kagMode());
    }

    private static void renderAttitudeSphere(
            ElectricalPanelBlockEntity panel,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            AttitudeIndicatorAngleCache.Transform transform,
            boolean kagMode
    ) {
        poseStack.pushPose();
        // Переносим исходный центр OBJ точно в найденный центр окна корпуса.
        poseStack.translate(0.5D, SPHERE_SOURCE_PIVOT_Y, SPHERE_RENDER_CENTER_Z);
        // У лицевой панели крен идёт вокруг нормали Z, тангаж — вокруг горизонтали X.
        if (!kagMode) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(transform.bankDegrees() * SPHERE_BANK_SIGN));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.pitchDegrees() * SPHERE_PITCH_SIGN));
        poseStack.translate(0.0D, -SPHERE_SOURCE_PIVOT_Y, -SPHERE_SOURCE_PIVOT_Z);
        CachedBuffers.partial(ATTITUDE_SPHERE, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
        poseStack.popPose();
    }

    private static void renderNetworkFrame(
            java.util.UUID networkId,
            PoseStack poseStack,
            MultiBufferSource buffers,
            double minX,
            double minY,
            double maxX,
            double maxY
    ) {
        if (!RadarLinkClientOutlineHandler.isSelectedRadarNetwork(networkId)) {
            return;
        }
        int color = RadarLinkClientOutlineHandler.radarPulseColor();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(
                poseStack,
                buffers.getBuffer(RenderType.lines()),
                minX, minY, FRAME_Z,
                maxX, maxY, FRAME_Z + FRAME_DEPTH,
                red, green, blue, 1.0F);
    }

    private static void renderPanelWindow(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            AttitudeIndicatorAngleCache.Transform transform,
            boolean kagMode
    ) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(ATTITUDE_TEXTURE));
        emitPanelQuad(
                poseStack.last(), consumer,
                PANEL_WINDOW_MIN, PANEL_WINDOW_MIN, PANEL_WINDOW_MAX, PANEL_WINDOW_MAX,
                WINDOW_MIN_U,
                kagMode ? KAG_WINDOW_MIN_V : ATTITUDE_WINDOW_MIN_V,
                WINDOW_MAX_U,
                kagMode ? KAG_WINDOW_MAX_V : ATTITUDE_WINDOW_MAX_V,
                packedLight, packedOverlay);

        if (!kagMode) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, -0.001D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.bankDegrees() * -SPHERE_BANK_SIGN));
        poseStack.translate(-0.5D, -0.5D, 0.0D);
        emitPanelQuad(
                poseStack.last(), consumer,
                0.5F - PANEL_POINTER_WIDTH * 0.5F,
                0.5F - PANEL_POINTER_HEIGHT * 0.5F,
                0.5F + PANEL_POINTER_WIDTH * 0.5F,
                0.5F + PANEL_POINTER_HEIGHT * 0.5F,
                KAG_POINTER_MIN_U, KAG_POINTER_MIN_V, KAG_POINTER_MAX_U, KAG_POINTER_MAX_V,
                packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void emitPanelQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int packedLight,
            int packedOverlay
    ) {
        emitPanelVertex(pose, consumer, minX, minY, minU, maxV, packedLight, packedOverlay);
        emitPanelVertex(pose, consumer, minX, maxY, minU, minV, packedLight, packedOverlay);
        emitPanelVertex(pose, consumer, maxX, maxY, maxU, minV, packedLight, packedOverlay);
        emitPanelVertex(pose, consumer, maxX, minY, maxU, maxV, packedLight, packedOverlay);
    }

    private static void emitPanelVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float u,
            float v,
            int packedLight,
            int packedOverlay
    ) {
        consumer.addVertex(pose.pose(), x, y, PANEL_WINDOW_Z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }
}
