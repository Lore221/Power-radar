package com.limbo2136.powerradar.client.radarlink;

import com.limbo2136.powerradar.PowerRadar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = PowerRadar.MOD_ID, value = Dist.CLIENT)
public final class RadarLinkClientEvents {
    private RadarLinkClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RadarLinkClientRuntime.init();
        RadarLinkClientOutlineHandler.tick();
    }
}
