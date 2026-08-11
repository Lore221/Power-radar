package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import net.minecraft.resources.ResourceLocation;

final class RadarBlipSprite {
    static final int ATLAS_SIZE = 256;
    static final int CELL_SIZE = 8;
    static final ResourceLocation ATLAS =
            PowerRadar.id("textures/gui/radar_ui/icons.png");

    static final RadarBlipSprite RADAR = new RadarBlipSprite(239, 252, 4, 4);
    static final RadarBlipSprite ENTITY = new RadarBlipSprite(244, 252, 4, 4);
    static final RadarBlipSprite PROJECTILE = new RadarBlipSprite(249, 252, 3, 4);
    static final RadarBlipSprite UNKNOWN = new RadarBlipSprite(253, 253, 3, 3);
    static final RadarBlipSprite HOVERED_FRAME = new RadarBlipSprite(230, 248, 8, 8);
    static final RadarBlipSprite SELECTED_FRAME = new RadarBlipSprite(221, 248, 8, 8);

    private final int sourceX;
    private final int sourceY;
    private final int width;
    private final int height;

    private RadarBlipSprite(int sourceX, int sourceY, int width, int height) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.width = width;
        this.height = height;
    }

    static RadarBlipSprite forCategory(RadarTargetCategory category) {
        return switch (category) {
            case PROJECTILE -> PROJECTILE;
            case RADAR -> RADAR;
            case SABLE_STRUCTURE, UNKNOWN -> UNKNOWN;
            default -> ENTITY;
        };
    }

    int sourceX() {
        return this.sourceX;
    }

    int sourceY() {
        return this.sourceY;
    }

    int width() {
        return this.width;
    }

    int height() {
        return this.height;
    }

    float minU() {
        return this.sourceX / (float) ATLAS_SIZE;
    }

    float maxU() {
        return (this.sourceX + this.width) / (float) ATLAS_SIZE;
    }

    float minV() {
        return this.sourceY / (float) ATLAS_SIZE;
    }

    float maxV() {
        return (this.sourceY + this.height) / (float) ATLAS_SIZE;
    }
}
