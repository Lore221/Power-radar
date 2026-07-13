package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarStructureType;
import net.minecraft.resources.ResourceLocation;

final class RadarCoverageSprite {
    private static final int LOGICAL_SIZE = 128;
    private static final ResourceLocation UI_ATLAS =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_ui/icons.png");
    private static final ResourceLocation OVERVIEW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_overview_octagon.png");

    private static final RadarCoverageSprite CONE_60 = centeredCone(1, 193, 63, 62);
    private static final RadarCoverageSprite CONE_90 = centeredCone(67, 193, 92, 62);
    private static final RadarCoverageSprite CONE_120 = centeredCone(1, 113, 110, 62);
    private static final RadarCoverageSprite OVERVIEW = new RadarCoverageSprite(
            OVERVIEW_TEXTURE, 128,
            0, 0, 128, 128,
            0, 0, 128, 128);

    private final ResourceLocation texture;
    private final int textureSize;
    private final int sourceX;
    private final int sourceY;
    private final int sourceWidth;
    private final int sourceHeight;
    private final float logicalMinX;
    private final float logicalMinY;
    private final float logicalMaxX;
    private final float logicalMaxY;

    private RadarCoverageSprite(
            ResourceLocation texture,
            int textureSize,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            float logicalMinX,
            float logicalMinY,
            float logicalMaxX,
            float logicalMaxY
    ) {
        this.texture = texture;
        this.textureSize = textureSize;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.logicalMinX = logicalMinX;
        this.logicalMinY = logicalMinY;
        this.logicalMaxX = logicalMaxX;
        this.logicalMaxY = logicalMaxY;
    }

    static RadarCoverageSprite forCoverage(RadarDisplayCoverage coverage) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return OVERVIEW;
        }
        if (coverage.sectorAngle() <= 60) {
            return CONE_60;
        }
        if (coverage.sectorAngle() <= 90) {
            return CONE_90;
        }
        return CONE_120;
    }

    private static RadarCoverageSprite centeredCone(int sourceX, int sourceY, int width, int height) {
        float logicalMinX = (LOGICAL_SIZE - width) / 2.0F;
        float logicalMinY = LOGICAL_SIZE / 2.0F - height;
        return new RadarCoverageSprite(
                UI_ATLAS, 256,
                sourceX, sourceY, width, height,
                logicalMinX, logicalMinY, logicalMinX + width, LOGICAL_SIZE / 2);
    }

    ResourceLocation texture() {
        return this.texture;
    }

    float minU() {
        return this.sourceX / (float) this.textureSize;
    }

    float maxU() {
        return (this.sourceX + this.sourceWidth) / (float) this.textureSize;
    }

    float minV() {
        return this.sourceY / (float) this.textureSize;
    }

    float maxV() {
        return (this.sourceY + this.sourceHeight) / (float) this.textureSize;
    }

    float minX(float center, float radius) {
        return center + (this.logicalMinX - LOGICAL_SIZE / 2.0F) * radius / (LOGICAL_SIZE / 2.0F);
    }

    float minY(float center, float radius) {
        return center + (this.logicalMinY - LOGICAL_SIZE / 2.0F) * radius / (LOGICAL_SIZE / 2.0F);
    }

    float maxX(float center, float radius) {
        return center + (this.logicalMaxX - LOGICAL_SIZE / 2.0F) * radius / (LOGICAL_SIZE / 2.0F);
    }

    float maxY(float center, float radius) {
        return center + (this.logicalMaxY - LOGICAL_SIZE / 2.0F) * radius / (LOGICAL_SIZE / 2.0F);
    }
}
