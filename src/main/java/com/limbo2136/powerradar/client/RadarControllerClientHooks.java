package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarControllerSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarControllerClientHooks {
    private RadarControllerClientHooks() {
    }

    public static void handleSnapshot(RadarControllerSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RadarControllerScreen screen && screen.isFor(payload.controllerPos())) {
            screen.updateSnapshot(payload);
            return;
        }
        minecraft.setScreen(new RadarControllerScreen(payload));
    }
}
