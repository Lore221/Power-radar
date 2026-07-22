package com.limbo2136.powerradar.compat.electroenergetics;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

/** Standard two-terminal layout used by every Power Radar CEE block. */
public record PowerRadarCeeTerminalPair(Vec3 positivePosition, Vec3 negativePosition) {
    public static final int POSITIVE = 0;
    public static final int NEGATIVE = 1;

    public Map<Integer, Vec3> positions() {
        return Map.of(POSITIVE, this.positivePosition, NEGATIVE, this.negativePosition);
    }

    public Vec3 position(int node) {
        return node == POSITIVE ? this.positivePosition : this.negativePosition;
    }

    public MutableComponent label(int node) {
        return Component.translatable(node == POSITIVE
                ? "power_radar.cee.node.power_positive"
                : "power_radar.cee.node.power_negative");
    }
}
