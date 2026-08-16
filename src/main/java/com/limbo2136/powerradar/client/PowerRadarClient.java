package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.bridge.AttitudeIndicatorPanelRenderBridge;
import com.limbo2136.powerradar.bridge.ClientPayloadBridge;
import com.limbo2136.powerradar.bridge.LogicDockPanelRenderBridge;
import com.limbo2136.powerradar.bridge.ShellAlarmIconBridge;
import com.limbo2136.powerradar.bridge.TooltipInputBridge;
import com.limbo2136.powerradar.bridge.TrajectoryIconBridge;
import com.limbo2136.powerradar.client.onboard.OnboardComputerRenderer;
import com.limbo2136.powerradar.client.panel.PowerRadarPanelAttachmentRenderer;
import com.limbo2136.powerradar.client.radarlink.RadarLinkClientRuntime;
import com.limbo2136.powerradar.client.compass.RadarCompassItemProperties;
import com.limbo2136.powerradar.client.compass.RadarCompassClientHooks;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModEntities;
import com.limbo2136.powerradar.registry.ModItems;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import java.util.function.Supplier;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.createmod.ponder.foundation.PonderIndex;
import com.limbo2136.powerradar.client.ponder.PowerRadarPonderPlugin;

@Mod(value = PowerRadar.MOD_ID, dist = Dist.CLIENT)
public final class PowerRadarClient {
    public PowerRadarClient(IEventBus modEventBus) {
        ClientPayloadBridge.configure(
                RadarMonitorClientHooks::handleSnapshot,
                RadarMonitorClientHooks::handleBlockSnapshot,
                RadarMonitorClientHooks::handleBlockStatic,
                RadarMonitorClientHooks::handleBlockTargets,
                RadarMonitorClientHooks::handleBlockPose,
                RadarMonitorClientHooks::handleSilhouette,
                TargetingCardClientHooks::open,
                AllowlistCardClientHooks::open,
                RadarCompassClientHooks::handleTarget);
        ShellAlarmIconBridge.configure(ShellAlarmIcons::dimensions);
        TrajectoryIconBridge.configure(TrajectoryIcons::icon);
        TooltipInputBridge.configure(
                Screen::hasShiftDown,
                () -> Minecraft.getInstance().player != null
                        && GogglesItem.isWearingGoggles(Minecraft.getInstance().player));
        modEventBus.addListener(PowerRadarClient::registerRenderers);
        modEventBus.addListener(PowerRadarClient::registerAdditionalModels);
        modEventBus.addListener(PowerRadarClient::registerPonder);
        modEventBus.addListener(PowerRadarClient::registerConfigScreen);
    }

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
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR_MONITOR_CONTROLLER.get(), RadarMonitorControllerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MECHANICAL_SIREN.get(), MechanicalSirenRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR_LINK.get(), RadarLinkRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LOGIC_DOCK.get(), LogicDockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ONBOARD_COMPUTER.get(), OnboardComputerRenderer::new);
        event.registerEntityRenderer(ModEntities.RADAR_STRUCTURE.get(), RadarStructureEntityRenderer::new);
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        OnboardComputerRenderer.registerAdditionalModels(event);
        RadarLinkRenderer.registerAdditionalModels(event);
        LogicDockRenderer.registerAdditionalModels(event);
        PowerRadarPanelAttachmentRenderer.registerAdditionalModels(event);
    }

    public static void registerPonder(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new PowerRadarPonderPlugin());
        event.enqueueWork(() -> {
            RadarCompassItemProperties.register();
            IncompleteOverviewModuleItemProperties.register();
            TooltipModifier.REGISTRY.register(
                    ModItems.MECHANICAL_SIREN.get(),
                    new KineticStats(ModBlocks.MECHANICAL_SIREN.get()));
        });
    }

    public static void registerConfigScreen(FMLLoadCompleteEvent event) {
        ModContainer container = ModList.get()
                .getModContainerById(PowerRadar.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Power Radar mod container is missing"));
        Supplier<IConfigScreenFactory> factory = () ->
                (ignored, previousScreen) -> new BaseConfigScreen(previousScreen, PowerRadar.MOD_ID);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }
}
