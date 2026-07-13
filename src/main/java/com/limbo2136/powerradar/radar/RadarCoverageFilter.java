package com.limbo2136.powerradar.radar;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class RadarCoverageFilter {
    private RadarCoverageFilter() {
    }

    public static boolean isEntityInCoverage(RadarScanProfile profile, RadarScanContext context, Entity entity) {
        Vec3 delta = entity.position().subtract(context.radarOriginX(), context.radarOriginY(), context.radarOriginZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance > profile.range()) {
            return false;
        }
        if (delta.y < profile.verticalMinOffset() || delta.y > profile.verticalMaxOffset()) {
            return false;
        }
        if (profile.scanMode() == RadarScanMode.SURFACE_SCANNER) {
            int surfaceY = context.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    entity.getBlockX(),
                    entity.getBlockZ());
            if (entity.getY() < surfaceY - 10.0D) {
                return false;
            }
        }
        return !profile.useFovCheck() || Math.abs(bearingDegrees(context.radarYawDegrees(), delta)) <= profile.sectorAngle() / 2.0;
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
