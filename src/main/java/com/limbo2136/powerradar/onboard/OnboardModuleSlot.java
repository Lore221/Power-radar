package com.limbo2136.powerradar.onboard;

import javax.annotation.Nullable;

/** Четыре области 4x4 модельных пикселя вокруг центрального экрана Onboard 8x8. */
public enum OnboardModuleSlot {
    FRONT_MIN_X(0, 1),
    REAR_MIN_X(0, 6),
    FRONT_MAX_X(12, 1),
    REAR_MAX_X(12, 6);

    public static final double SIDE_PIXELS = 4.0D;
    private final int panelX;
    private final int panelDepth;

    OnboardModuleSlot(int panelX, int panelDepth) {
        this.panelX = panelX;
        this.panelDepth = panelDepth;
    }

    public int index() {
        return ordinal();
    }

    public int panelX() {
        return this.panelX;
    }

    public int panelDepth() {
        return this.panelDepth;
    }

    @Nullable
    public static OnboardModuleSlot at(double panelX, double panelDepth) {
        for (OnboardModuleSlot slot : values()) {
            if (panelX >= slot.panelX && panelX <= slot.panelX + SIDE_PIXELS
                    && panelDepth >= slot.panelDepth && panelDepth <= slot.panelDepth + SIDE_PIXELS) {
                return slot;
            }
        }
        return null;
    }
}
