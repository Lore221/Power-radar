package com.limbo2136.powerradar.radar;

public enum TargetTrajectoryMode {
    FLAT,
    HIGH_ARC;

    public static TargetTrajectoryMode byName(String name) {
        if (name == null || name.isBlank()) {
            return FLAT;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException exception) {
            return FLAT;
        }
    }
}
