package com.limbo2136.powerradar.radar;

import java.util.List;
import net.minecraft.world.phys.AABB;

public record RadarScanSlicePlan(AABB searchBox, List<AABB> slices) {
    public int sliceCount() {
        return this.slices.size();
    }
}
