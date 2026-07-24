package com.limbo2136.powerradar.onboard;

import javax.annotation.Nullable;

/** Один боковой столбец панели 4x8 модельных пикселей из двух соседних слотов. */
public enum OnboardModuleColumn {
    MIN_X(OnboardModuleSlot.FRONT_MIN_X, OnboardModuleSlot.REAR_MIN_X),
    MAX_X(OnboardModuleSlot.FRONT_MAX_X, OnboardModuleSlot.REAR_MAX_X);

    public static final double CENTER_DEPTH_PIXELS = 5.5D;
    private static final double MIN_DEPTH_PIXELS = 1.0D;
    private static final double MAX_DEPTH_PIXELS = 10.0D;

    private final OnboardModuleSlot frontSlot;
    private final OnboardModuleSlot rearSlot;

    OnboardModuleColumn(OnboardModuleSlot frontSlot, OnboardModuleSlot rearSlot) {
        this.frontSlot = frontSlot;
        this.rearSlot = rearSlot;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public int panelX() {
        return this.frontSlot.panelX();
    }

    public OnboardModuleSlot frontSlot() {
        return this.frontSlot;
    }

    public OnboardModuleSlot rearSlot() {
        return this.rearSlot;
    }

    public boolean contains(OnboardModuleSlot slot) {
        return slot == this.frontSlot || slot == this.rearSlot;
    }

    @Nullable
    public static OnboardModuleColumn containing(OnboardModuleSlot slot) {
        for (OnboardModuleColumn column : values()) {
            if (column.contains(slot)) {
                return column;
            }
        }
        return null;
    }

    @Nullable
    public static OnboardModuleColumn at(double panelX, double panelDepth) {
        if (panelDepth < MIN_DEPTH_PIXELS || panelDepth > MAX_DEPTH_PIXELS) {
            return null;
        }
        for (OnboardModuleColumn column : values()) {
            if (panelX >= column.panelX()
                    && panelX <= column.panelX() + OnboardModuleSlot.SIDE_PIXELS) {
                return column;
            }
        }
        return null;
    }
}
