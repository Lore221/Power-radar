package com.limbo2136.powerradar.api.threat;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record InterceptionWindow(
        UUID threatUuid,
        Vec3 aimPoint,
        double targetTicks,
        double aimTicks,
        double flightTicks,
        double timingErrorTicks
) {
    public boolean valid(double maxTimingErrorTicks) {
        return this.timingErrorTicks <= maxTimingErrorTicks;
    }
}
