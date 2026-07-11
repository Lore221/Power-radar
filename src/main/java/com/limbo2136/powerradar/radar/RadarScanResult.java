package com.limbo2136.powerradar.radar;

import net.minecraft.core.Direction;

public record RadarScanResult(
        boolean assembled,
        int validPanelCount,
        int range,
        Direction facing,
        int candidateCount,
        int acceptedCount,
        int updatedTrackCount,
        int addedTrackCount,
        int ignoredItemCount,
        int ignoredCategoryCount,
        int staleValidatedCount,
        int removedDeadOrMissingCount,
        int removedExpiredCount,
        double aabbSizeX,
        double aabbSizeY,
        double aabbSizeZ,
        int aabbSliceCount,
        int broadQueryCount,
        int typedQueryCount,
        int cacheSizeAfter,
        long getEntitiesDurationNanos,
        long filteringDurationNanos,
        long staleValidationDurationNanos,
        long totalScanDurationNanos
) {
    public static RadarScanResult inactive(Direction facing, int cacheSizeAfter) {
        return new RadarScanResult(false, 0, 0, facing, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0.0, 0.0, 0.0, 0, 0, 0, cacheSizeAfter, 0L, 0L, 0L, 0L);
    }
}
