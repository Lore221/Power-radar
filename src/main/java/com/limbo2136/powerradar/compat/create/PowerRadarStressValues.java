package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.registry.ModBlocks;
import com.simibubi.create.api.stress.BlockStressValues;

public final class PowerRadarStressValues {
    private PowerRadarStressValues() {
    }

    public static void register() {
        BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_SIREN.get(), () -> 2.0D);
    }
}
