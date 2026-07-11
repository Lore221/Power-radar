package com.limbo2136.powerradar.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record RadarDisplayCoverage(
        RadarId radarId,
        BlockPos controllerPos,
        ResourceLocation dimensionId,
        double originX,
        double originY,
        double originZ,
        RadarOrientationState orientationState,
        int currentRange,
        int sectorAngle
) {
}
