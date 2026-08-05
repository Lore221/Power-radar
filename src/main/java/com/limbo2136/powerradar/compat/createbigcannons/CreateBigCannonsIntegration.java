package com.limbo2136.powerradar.compat.createbigcannons;

import net.neoforged.fml.ModList;

public final class CreateBigCannonsIntegration {
    public static final String MOD_ID = "createbigcannons";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private CreateBigCannonsIntegration() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}
