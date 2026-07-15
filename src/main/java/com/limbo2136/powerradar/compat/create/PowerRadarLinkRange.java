package com.limbo2136.powerradar.compat.create;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;

public final class PowerRadarLinkRange {
    private PowerRadarLinkRange() {
    }

    public static boolean withinRange(BlockPos first, BlockPos second) {
        return first.closerThan(second, AllConfigs.server().logistics.linkRange.get());
    }
}
