package com.limbo2136.powerradar.radar;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;

public record RadarScanRequest(
        RadarId radarId,
        @Nullable RadarScanProfile discoveryProfile,
        @Nullable RadarScanProfile refreshProfile,
        RadarScanContext context,
        RadarTargetCache targetCache,
        List<AABB> slices,
        boolean publish,
        @Nullable Runnable publishCompletion
) {
    public RadarScanRequest {
        slices = List.copyOf(slices);
    }
}
