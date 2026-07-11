package com.limbo2136.powerradar.radar;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class RadarGeometry {
    private RadarGeometry() {
    }

    public static float yawDegrees(Direction direction) {
        return switch (direction) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            case NORTH, UP, DOWN -> 0.0F;
        };
    }

    public static float normalizeDegrees(float degrees) {
        float wrapped = Mth.wrapDegrees(degrees);
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    public static float relativeDegrees(float yawDegrees, float referenceYawDegrees) {
        return normalizeDegrees(yawDegrees - referenceYawDegrees);
    }
}
