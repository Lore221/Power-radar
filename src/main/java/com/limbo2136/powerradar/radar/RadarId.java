package com.limbo2136.powerradar.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record RadarId(ResourceLocation dimensionId, BlockPos controllerPos) {
    @Override
    public String toString() {
        return this.dimensionId + "@" + this.controllerPos.getX() + "," + this.controllerPos.getY() + "," + this.controllerPos.getZ();
    }
}
