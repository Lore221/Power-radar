package com.limbo2136.powerradar.radar;

public final class RadarDisplayProjector {
    public static final int MONITOR_MAP_SIZE_BLOCKS = 1000;
    public static final int MONITOR_MAP_RADIUS_BLOCKS = MONITOR_MAP_SIZE_BLOCKS / 2;
    public static final int MONITOR_GRID_CELL_BLOCKS = 100;
    public static final int MONITOR_GRID_CELLS = MONITOR_MAP_SIZE_BLOCKS / MONITOR_GRID_CELL_BLOCKS;

    private RadarDisplayProjector() {
    }

    public static RadarDisplayProjection project(RadarMonitorDisplayData displayData, RadarDisplayTarget target) {
        return project(displayData, target, displayData.monitorViewYawDegrees());
    }

    public static RadarDisplayProjection project(RadarMonitorDisplayData displayData, RadarDisplayTarget target, float viewYawDegrees) {
        return project(displayData, target, viewYawDegrees, MONITOR_MAP_RADIUS_BLOCKS);
    }

    public static RadarDisplayProjection project(
            RadarMonitorDisplayData displayData,
            RadarDisplayTarget target,
            float viewYawDegrees,
            int mapRadiusBlocks
    ) {
        return project(displayData, target, viewYawDegrees, mapRadiusBlocks, 0.0D, 0.0D);
    }

    public static RadarDisplayProjection project(
            RadarMonitorDisplayData displayData,
            RadarDisplayTarget target,
            float viewYawDegrees,
            int mapRadiusBlocks,
            double centerOffsetX,
            double centerOffsetZ
    ) {
        return projectWorldPoint(
                displayData,
                target.dimensionId(),
                target.x(),
                target.y(),
                target.z(),
                target.displayAgeTicks() > 0,
                viewYawDegrees,
                mapRadiusBlocks,
                centerOffsetX,
                centerOffsetZ);
    }

    public static RadarDisplayProjection projectRadarOrigin(RadarMonitorDisplayData displayData) {
        return projectRadarOrigin(displayData, displayData.monitorViewYawDegrees());
    }

    public static RadarDisplayProjection projectRadarOrigin(RadarMonitorDisplayData displayData, float viewYawDegrees) {
        return projectRadarOrigin(displayData, viewYawDegrees, MONITOR_MAP_RADIUS_BLOCKS);
    }

    public static RadarDisplayProjection projectRadarOrigin(
            RadarMonitorDisplayData displayData,
            float viewYawDegrees,
            int mapRadiusBlocks
    ) {
        return projectRadarOrigin(displayData, viewYawDegrees, mapRadiusBlocks, 0.0D, 0.0D);
    }

    public static RadarDisplayProjection projectRadarOrigin(
            RadarMonitorDisplayData displayData,
            float viewYawDegrees,
            int mapRadiusBlocks,
            double centerOffsetX,
            double centerOffsetZ
    ) {
        return projectWorldPoint(
                displayData,
                displayData.radarDimensionId(),
                displayData.radarOriginX(),
                displayData.radarOriginY(),
                displayData.radarOriginZ(),
                false,
                viewYawDegrees,
                mapRadiusBlocks,
                centerOffsetX,
                centerOffsetZ);
    }

    public static RadarDisplayProjection projectWorldPoint(
            RadarMonitorDisplayData displayData,
            net.minecraft.resources.ResourceLocation dimensionId,
            double x,
            double y,
            double z,
            float viewYawDegrees,
            int mapRadiusBlocks,
            double centerOffsetX,
            double centerOffsetZ
    ) {
        return projectWorldPoint(
                displayData,
                dimensionId,
                x,
                y,
                z,
                false,
                viewYawDegrees,
                mapRadiusBlocks,
                centerOffsetX,
                centerOffsetZ);
    }

    private static RadarDisplayProjection projectWorldPoint(
            RadarMonitorDisplayData displayData,
            net.minecraft.resources.ResourceLocation dimensionId,
            double x,
            double y,
            double z,
            boolean stale,
            float viewYawDegrees,
            int mapRadiusBlocks,
            double centerOffsetX,
            double centerOffsetZ
    ) {
        if (!dimensionId.equals(displayData.radarDimensionId())) {
            return RadarDisplayProjection.HIDDEN;
        }
        int safeRadius = Math.max(1, mapRadiusBlocks);

        double dx = x - (displayData.monitorPos().getX() + 0.5D) - centerOffsetX;
        double dz = z - (displayData.monitorPos().getZ() + 0.5D) - centerOffsetZ;

        double viewRadians = Math.toRadians(viewYawDegrees);
        double upX = Math.sin(viewRadians);
        double upZ = -Math.cos(viewRadians);
        double rightX = Math.cos(viewRadians);
        double rightZ = Math.sin(viewRadians);
        double screenHorizontal = dx * rightX + dz * rightZ;
        double screenVertical = dx * upX + dz * upZ;
        double edgeDistance = Math.max(Math.abs(screenHorizontal), Math.abs(screenVertical));
        if (!stale && edgeDistance > safeRadius) {
            return RadarDisplayProjection.HIDDEN;
        }

        double radial = stale
                ? clamp(edgeDistance / safeRadius, 0.0, 1.0)
                : edgeDistance / safeRadius;
        double projectedX = stale
                ? clamp(screenHorizontal / safeRadius, -1.0, 1.0)
                : screenHorizontal / safeRadius;
        double projectedY = stale
                ? clamp(-screenVertical / safeRadius, -1.0, 1.0)
                : -screenVertical / safeRadius;
        return new RadarDisplayProjection(true, projectedX, projectedY, radial);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
