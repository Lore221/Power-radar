package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarMonitorBlockSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockStaticPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorSilhouettePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarMonitorClientHooks {
    private RadarMonitorClientHooks() {
    }

    public static void handleSnapshot(RadarMonitorSnapshotPayload payload) {
        SableSilhouetteClientCache.requestMissing(payload.monitorPos(), payload.targets());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RadarMonitorScreen screen && screen.isFor(payload.monitorPos())) {
            screen.updateSnapshot(payload);
            return;
        }
        minecraft.setScreen(new RadarMonitorScreen(payload));
    }

    public static void handleBlockSnapshot(RadarMonitorBlockSnapshotPayload payload) {
        RadarMonitorClientState.applySnapshot(payload.snapshot());
        SableSilhouetteClientCache.requestMissing(
                payload.snapshot().monitorPos(), payload.snapshot().targets());
    }

    public static void handleBlockStatic(RadarMonitorBlockStaticPayload payload) {
        RadarMonitorClientState.applyStatic(payload.snapshot());
    }

    public static void handleBlockTargets(RadarMonitorBlockTargetsPayload payload) {
        RadarMonitorClientState.applyTargets(payload);
        SableSilhouetteClientCache.requestMissing(payload.monitorPos(), payload.targets());
    }

    public static void handleBlockPose(RadarMonitorBlockPosePayload payload) {
        RadarMonitorClientState.applyPose(payload);
    }

    public static void handleSilhouette(RadarMonitorSilhouettePayload payload) {
        SableSilhouetteClientCache.apply(payload);
    }
}
