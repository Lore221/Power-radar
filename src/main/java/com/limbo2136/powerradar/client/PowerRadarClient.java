package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.client.radarlink.RadarLinkClientRuntime;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = PowerRadar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PowerRadarClient {
    private PowerRadarClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RadarLinkClientRuntime.init();
        event.registerBlockEntityRenderer(ModBlockEntities.OVERVIEW_MODULE.get(), OverviewModuleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RADAR_MONITOR_CONTROLLER.get(), RadarMonitorControllerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MECHANICAL_SIREN.get(), MechanicalSirenRenderer::new);
    }
}
