package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.radar.RadarTargetCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record RadarBlipRenderData(
        String stableKey,
        int screenX,
        int screenY,
        int color,
        double radialFraction,
        RadarTargetCategory category,
        int targetIndex,
        int displayAgeTicks
) {
}
