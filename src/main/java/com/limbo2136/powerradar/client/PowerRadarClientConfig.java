package com.limbo2136.powerradar.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PowerRadarClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue MONITOR_BLOCK_ALIGNED_VIEW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("monitor");
        MONITOR_BLOCK_ALIGNED_VIEW = builder
                .comment(
                        "Default false keeps monitor maps world-aligned with north at the top.",
                        "When true, monitor maps use the legacy block-facing-aligned view.")
                .define("block_aligned_view", false);
        builder.pop();
        SPEC = builder.build();
    }

    private PowerRadarClientConfig() {
    }

    public static boolean monitorBlockAlignedView() {
        return SPEC.isLoaded() ? MONITOR_BLOCK_ALIGNED_VIEW.get() : MONITOR_BLOCK_ALIGNED_VIEW.getDefault();
    }
}
