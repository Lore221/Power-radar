package com.limbo2136.powerradar.radar;

import java.util.List;
import net.minecraft.world.phys.AABB;

public record RadarScanSlicePlan(List<AABB> slices) {
    public RadarScanSlicePlan {
        slices = List.copyOf(slices);
    }
}
