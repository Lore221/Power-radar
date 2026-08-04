package com.limbo2136.powerradar.client;

import net.minecraft.core.Direction;

/** Преобразует baked blockstate-повороты Radar Link в углы PoseStack. */
final class RadarLinkModelRotation {
    private RadarLinkModelRotation() {
    }

    static float horizontalFacingYDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    static float verticalModelYDegrees(Direction modelFacing) {
        return switch (modelFacing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
