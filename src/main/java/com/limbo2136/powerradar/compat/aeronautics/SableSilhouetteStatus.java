package com.limbo2136.powerradar.compat.aeronautics;

/** Качество серверного снимка силуэта, доступного монитору. */
public enum SableSilhouetteStatus {
    BUILDING,
    DETAILED,
    FALLBACK_BOUNDS,
    UNAVAILABLE;

    public boolean drawable() {
        return this == BUILDING || this == DETAILED || this == FALLBACK_BOUNDS;
    }
}
