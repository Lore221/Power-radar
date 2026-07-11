package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarMonitorBlockSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockStaticPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarMonitorClientHooks {
    private RadarMonitorClientHooks() {
    }

    public static void handleSnapshot(RadarMonitorSnapshotPayload payload) {
        RadarMonitorClientState.applySnapshot(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RadarMonitorScreen screen && screen.isFor(payload.monitorPos())) {
            screen.updateSnapshot(payload);
            return;
        }
        minecraft.setScreen(new RadarMonitorScreen(payload));
    }

    public static void handleBlockSnapshot(RadarMonitorBlockSnapshotPayload payload) {
        RadarMonitorClientState.applySnapshot(payload.snapshot());
    }

    public static void handleBlockStatic(RadarMonitorBlockStaticPayload payload) {
        RadarMonitorClientState.applySnapshot(payload.snapshot());
    }

    public static void handleBlockTargets(RadarMonitorBlockTargetsPayload payload) {
        RadarMonitorClientState.applyTargets(payload);
    }
}
