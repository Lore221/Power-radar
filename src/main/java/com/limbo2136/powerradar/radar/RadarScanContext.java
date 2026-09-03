package com.limbo2136.powerradar.radar;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record RadarScanContext(
        ServerLevel level,
        ResourceLocation dimensionId,
        RadarId radarId,
        double radarOriginX,
        double radarOriginY,
        double radarOriginZ,
        Direction assemblyFacing,
        float radarYawDegrees,
        Vec3 radarForward,
        long gameTime
) {
}
