package com.limbo2136.powerradar.radar;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;

/** Заявка одного радара на текущий серверный тик; список срезов копируется при создании. */
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
