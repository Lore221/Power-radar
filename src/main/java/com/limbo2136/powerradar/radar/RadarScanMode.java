package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;

public enum RadarScanMode {
    GROUND,
    SKY,
    SURFACE_SCANNER;

    public RadarScanMode next() {
        return this == GROUND ? SKY : GROUND;
    }

    public String messageKey() {
        return switch (this) {
            case GROUND -> "message.power_radar.mode.ground";
            case SKY -> "message.power_radar.mode.sky";
            case SURFACE_SCANNER -> "message.power_radar.mode.surface_scanner";
        };
    }

    public int sectorAngleDegrees() {
        return switch (this) {
            case GROUND, SURFACE_SCANNER -> RadarConstants.sectorRadarGroundAngleDegrees();
            case SKY -> 60;
        };
    }

    public int verticalScanHeight() {
        return switch (this) {
            case GROUND, SURFACE_SCANNER -> PowerRadarCeeConstants.groundUpBlocks();
            case SKY -> PowerRadarCeeConstants.airMaxYOffset();
        };
    }

    public static RadarScanMode byName(String name) {
        for (RadarScanMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return GROUND;
    }
}
