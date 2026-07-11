package com.limbo2136.powerradar.radar;

public record RadarStaleValidationResult(
        int staleValidatedCount,
        int removedDeadOrMissingCount,
        int removedExpiredCount
) {
    public static final RadarStaleValidationResult EMPTY = new RadarStaleValidationResult(0, 0, 0);
}
