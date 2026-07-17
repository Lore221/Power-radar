package com.limbo2136.powerradar.radar;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class RadarCoverageFilter {
    // Matches radar_overview_octagon.png: a 128px footprint with 29px corner cuts.
    private static final double OVERVIEW_DIAGONAL_LIMIT = 98.0D / 64.0D;
    private RadarCoverageFilter() {
    }

    public static boolean isEntityInCoverage(RadarScanProfile profile, RadarScanContext context, Entity entity) {
        return isPointInCoverage(profile, context, entity.position(), entity.getBlockX(), entity.getBlockZ());
    }

    public static boolean isPointInCoverage(RadarScanProfile profile, RadarScanContext context, Vec3 position) {
        return isPointInCoverage(profile, context, position, (int) Math.floor(position.x), (int) Math.floor(position.z));
    }

    private static boolean isPointInCoverage(
            RadarScanProfile profile,
            RadarScanContext context,
            Vec3 position,
            int blockX,
            int blockZ
    ) {
        Vec3 delta = position.subtract(context.radarOriginX(), context.radarOriginY(), context.radarOriginZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        boolean insideHorizontalCoverage = profile.structureType() == RadarStructureType.OVERVIEW
                ? isInsideOverviewFootprint(delta.x, delta.z, profile.range())
                : horizontalDistance <= profile.range();
        if (!insideHorizontalCoverage) {
            return false;
        }
        if (delta.y < profile.verticalMinOffset() || delta.y > profile.verticalMaxOffset()) {
            return false;
        }
        if (profile.scanMode() == RadarScanMode.SURFACE_SCANNER) {
            int surfaceY = context.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    blockX,
                    blockZ);
            if (position.y < surfaceY - 10.0D) {
                return false;
            }
        }
        return !profile.useFovCheck() || Math.abs(bearingDegrees(context.radarYawDegrees(), delta)) <= profile.sectorAngle() / 2.0;
    }

    public static boolean isInsideOverviewFootprint(double deltaX, double deltaZ, double range) {
        double safeRange = Math.max(1.0D, range);
        double normalizedX = Math.abs(deltaX) / safeRange;
        double normalizedZ = Math.abs(deltaZ) / safeRange;
        return normalizedX <= 1.0D
                && normalizedZ <= 1.0D
                && normalizedX + normalizedZ <= OVERVIEW_DIAGONAL_LIMIT;
    }

    public static double bearingDegrees(float yawDegrees, Vec3 delta) {
        double radians = Math.toRadians(yawDegrees);
        double forwardX = Math.sin(radians);
        double forwardZ = -Math.cos(radians);
        double rightX = Math.cos(radians);
        double rightZ = Math.sin(radians);
        double forwardDot = delta.x * forwardX + delta.z * forwardZ;
        double rightDot = delta.x * rightX + delta.z * rightZ;
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(rightDot, forwardDot)));
    }
}
