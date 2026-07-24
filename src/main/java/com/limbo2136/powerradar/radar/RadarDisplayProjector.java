package com.limbo2136.powerradar.radar;

/**
 * Проецирует мировые X/Z на квадратную карту монитора в диапазоне [-1; 1].
 * Высота цели не влияет на положение метки, а центр карты всегда совпадает с блоком монитора.
 */
public final class RadarDisplayProjector {
    public static final int MONITOR_MAP_SIZE_BLOCKS = 300;
    public static final int MONITOR_MAP_RADIUS_BLOCKS = MONITOR_MAP_SIZE_BLOCKS / 2;
    public static final int MONITOR_GRID_CELL_BLOCKS = 100;
    public static final int MONITOR_GRID_CELLS = MONITOR_MAP_SIZE_BLOCKS / MONITOR_GRID_CELL_BLOCKS;
    public static final int MIN_MONITOR_MAP_SIZE_BLOCKS = 100;
    public static final int MAX_MONITOR_MAP_SIZE_BLOCKS = 10_000;
    public static final int MINIMUM_RADAR_REFERENCE_MAP_SIZE_BLOCKS =
            (RadarModuleConstants.BASE_RANGE_BLOCKS + RadarModuleConstants.PHASED_ARRAY_RANGE_BONUS_BLOCKS) * 2;

    private RadarDisplayProjector() {
    }

    public static int maximumRadarRange(RadarMonitorDisplayData displayData) {
        if (displayData == null) {
            return 0;
        }
        int maximumRange = Math.max(0, displayData.currentRange());
        for (RadarDisplayCoverage coverage : displayData.coverages()) {
            maximumRange = Math.max(maximumRange, coverage.currentRange());
        }
        return maximumRange;
    }

    public static int recommendedMapSizeBlocks(RadarMonitorDisplayData displayData) {
        int requiredDiameter = maximumRadarRange(displayData) * 2;
        int roundedToGrid = ((requiredDiameter + MONITOR_GRID_CELL_BLOCKS - 1) / MONITOR_GRID_CELL_BLOCKS)
                * MONITOR_GRID_CELL_BLOCKS;
        return Math.max(MIN_MONITOR_MAP_SIZE_BLOCKS, Math.min(MAX_MONITOR_MAP_SIZE_BLOCKS, roundedToGrid));
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

    public static RadarDisplayProjection projectWorldPointUnclipped(
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
        if (!dimensionId.equals(displayData.radarDimensionId())) {
            return RadarDisplayProjection.HIDDEN;
        }
        return projectWorldPointCoordinates(
                displayData, x, z, viewYawDegrees, mapRadiusBlocks, centerOffsetX, centerOffsetZ, false, false);
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
        return projectWorldPointCoordinates(
                displayData, x, z, viewYawDegrees, mapRadiusBlocks, centerOffsetX, centerOffsetZ, stale, true);
    }

    private static RadarDisplayProjection projectWorldPointCoordinates(
            RadarMonitorDisplayData displayData,
            double x,
            double z,
            float viewYawDegrees,
            int mapRadiusBlocks,
            double centerOffsetX,
            double centerOffsetZ,
            boolean stale,
            boolean clipFreshToBounds
    ) {
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
        if (!stale && clipFreshToBounds && edgeDistance > safeRadius) {
            return RadarDisplayProjection.HIDDEN;
        }

        // Свежие цели за границей скрываются, а устаревшие остаются прижатыми к краю до затухания.
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
