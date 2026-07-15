package com.limbo2136.powerradar.compat.aeronautics;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-owned supplemental geometry snapshot. It is intentionally not sent to monitors yet. */
public record SableSilhouetteSnapshot(
        ResourceLocation dimensionId,
        UUID structureUuid,
        int version,
        long builtGameTime,
        List<SableSilhouetteLine> lines,
        List<SableSilhouetteFill> fills
) {
    public SableSilhouetteSnapshot {
        lines = List.copyOf(lines);
        fills = List.copyOf(fills);
    }
}
