package com.limbo2136.powerradar.radar;

public record RadarProjectedBlip(
        String stableKey,
        RadarTargetCategory category,
        double x,
        double y,
        double radialFraction,
        int displayAgeTicks,
        boolean manuallySelected
) {
}
