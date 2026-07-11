package com.limbo2136.powerradar.api.radar;

import com.limbo2136.powerradar.radar.RadarCoverageShape;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarStructureType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record RadarCoverage(
        Vec3 origin,
        int rangeBlocks,
        int verticalMinOffset,
        int verticalMaxOffset,
        Direction facing,
        float yawDegrees,
        RadarCoverageShape shape,
        RadarScanMode scanMode,
        RadarStructureType structureType
) {
    public boolean active() {
        return this.rangeBlocks > 0;
    }
}
