package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.bridge.AttitudeIndicatorPanelRenderBridge;
import com.limbo2136.powerradar.bridge.LogicDockPanelRenderBridge;
import com.limbo2136.powerradar.client.onboard.OnboardComputerRenderer;
import com.limbo2136.powerradar.client.panel.PowerRadarPanelAttachmentRenderer;
import com.limbo2136.powerradar.client.radarlink.RadarLinkClientRuntime;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = PowerRadar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PowerRadarClient {
    private PowerRadarClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        AttitudeIndicatorPanelRenderBridge.setHandler(
                (attachment, panel, partialTicks, poseStack, buffers, packedLight, packedOverlay) ->
                        PowerRadarPanelAttachmentRenderer.renderAttitudeIndicator(
                                attachment,
                                panel,
                                partialTicks,
                                (com.mojang.blaze3d.vertex.PoseStack) poseStack,
                                (net.minecraft.client.renderer.MultiBufferSource) buffers,
                                packedLight,
                                packedOverlay));
        LogicDockPanelRenderBridge.setHandler(
                (attachment, panel, poseStack, buffers, packedLight, packedOverlay) ->
                        PowerRadarPanelAttachmentRenderer.renderLogicDock(
                                attachment,
                                panel,
                                (com.mojang.blaze3d.vertex.PoseStack) poseStack,
                                (net.minecraft.client.renderer.MultiBufferSource) buffers,
                                packedLight,
                                packedOverlay));
        RadarLinkClientRuntime.init();
        MechanicalSirenClientAudioRuntime.init();
        event.registerBlockEntityRenderer(ModBlockEntities.OVERVIEW_MODULE.get(), OverviewModuleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR_MONITOR_CONTROLLER.get(), RadarMonitorControllerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MECHANICAL_SIREN.get(), MechanicalSirenRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR_LINK.get(), RadarLinkRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LOGIC_DOCK.get(), LogicDockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ONBOARD_COMPUTER.get(), OnboardComputerRenderer::new);
        event.registerEntityRenderer(ModEntities.RADAR_STRUCTURE.get(), RadarStructureEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        OnboardComputerRenderer.registerAdditionalModels(event);
        RadarLinkRenderer.registerAdditionalModels(event);
        LogicDockRenderer.registerAdditionalModels(event);
        PowerRadarPanelAttachmentRenderer.registerAdditionalModels(event);
    }
}
