package com.limbo2136.powerradar.radar;

public record RadarDisplayProjection(
        boolean visible,
        double x,
        double y,
        double radialFraction
) {
    public static final RadarDisplayProjection HIDDEN = new RadarDisplayProjection(false, 0.0, 0.0, 0.0);
}
