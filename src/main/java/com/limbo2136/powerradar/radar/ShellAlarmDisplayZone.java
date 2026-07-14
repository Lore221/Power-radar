package com.limbo2136.powerradar.radar;

import net.minecraft.resources.ResourceLocation;

public record ShellAlarmDisplayZone(
        ResourceLocation dimensionId,
        double centerX,
        double centerY,
        double centerZ,
        int sideBlocks
) {
}
