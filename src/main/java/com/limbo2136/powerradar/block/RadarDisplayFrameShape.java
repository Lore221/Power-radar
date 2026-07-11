package com.limbo2136.powerradar.block;

import net.minecraft.util.StringRepresentable;

public enum RadarDisplayFrameShape implements StringRepresentable {
    SINGLE("single"),
    CENTER("center"),
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right"),
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right");

    private final String serializedName;

    RadarDisplayFrameShape(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
