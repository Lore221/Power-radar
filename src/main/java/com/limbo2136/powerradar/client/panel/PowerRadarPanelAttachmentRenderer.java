package com.limbo2136.powerradar.client.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.client.RadarMonitorControllerBlockEntityRenderer;
import com.limbo2136.powerradar.client.instrument.AttitudeIndicatorAngleCache;
import com.limbo2136.powerradar.client.radarlink.PanelRadarLinkClientCache;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.panel.AttitudeIndicatorPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.LogicDockPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarDisplayPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarLinkPanelAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private static final ResourceLocation RADAR_LINK_LOCATION =
            PowerRadar.id("block/electrical_panel/radar_link");
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
    private static final PartialModel RADAR_LINK = PartialModel.of(RADAR_LINK_LOCATION);
    private static final PartialModel RADAR_DISPLAY = PartialModel.of(RADAR_DISPLAY_LOCATION);
    private static final PartialModel ATTITUDE_INDICATOR = PartialModel.of(ATTITUDE_INDICATOR_LOCATION);
    private static final PartialModel ATTITUDE_SPHERE = PartialModel.of(ATTITUDE_SPHERE_LOCATION);
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
    private static final RadarMonitorControllerBlockEntityRenderer MONITOR_RENDERER =
            new RadarMonitorControllerBlockEntityRenderer(null);

    private PowerRadarPanelAttachmentRenderer() {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(RADAR_LINK_LOCATION));
        event.register(ModelResourceLocation.standalone(RADAR_DISPLAY_LOCATION));
        event.register(ModelResourceLocation.standalone(ATTITUDE_INDICATOR_LOCATION));
        event.register(ModelResourceLocation.standalone(ATTITUDE_SPHERE_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_TARGETING_CARD_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_DISPLAY_CARD_LOCATION));
        event.register(ModelResourceLocation.standalone(LOGIC_DOCK_ALLOWLIST_CARD_LOCATION));
    }

    /** Обновляет индекс подсветки по фактическому слоту, поэтому один щиток может хранить несколько Link. */
    public static void tickLinkClient(
            RadarLinkPanelAttachment attachment,
            ElectricalPanelBlockEntity panel
    ) {
        if (!(panel.getLevel() instanceof ClientLevel clientLevel)) {
            return;
        }
        PanelRadarLinkClientCache.registerOrUpdate(
                clientLevel,
                panel.getBlockPos(),
                attachment.slot,
                attachment.networkId());
    }

    public static void renderLink(
            RadarLinkPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        attachment.transformPose(poseStack, panel);
        CachedBuffers.partial(RADAR_LINK, panel.getBlockState())
                .light(packedLight)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
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
        renderAttitudeSphere(panel, poseStack, buffers, packedLight, packedOverlay, transform);
        CachedBuffers.partial(ATTITUDE_INDICATOR, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
    }

    private static void renderAttitudeSphere(
            ElectricalPanelBlockEntity panel,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            AttitudeIndicatorAngleCache.Transform transform
    ) {
        poseStack.pushPose();
        // Переносим исходный центр OBJ точно в найденный центр окна корпуса.
        poseStack.translate(0.5D, SPHERE_SOURCE_PIVOT_Y, SPHERE_RENDER_CENTER_Z);
        // У лицевой панели крен идёт вокруг нормали Z, тангаж — вокруг горизонтали X.
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.bankDegrees() * SPHERE_BANK_SIGN));
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.pitchDegrees() * SPHERE_PITCH_SIGN));
        poseStack.translate(0.0D, -SPHERE_SOURCE_PIVOT_Y, -SPHERE_SOURCE_PIVOT_Z);
        CachedBuffers.partial(ATTITUDE_SPHERE, panel.getBlockState())
                .light(packedLight)
                .overlay(packedOverlay)
                .renderInto(poseStack, buffers.getBuffer(RenderType.cutout()));
        poseStack.popPose();
    }
}
