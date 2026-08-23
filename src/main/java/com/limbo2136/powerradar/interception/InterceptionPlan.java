package com.limbo2136.powerradar.interception;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Зафиксированная мировая точка и абсолютное время встречи с угрозой. */
public record InterceptionPlan(UUID threatUuid, Vec3 aimPoint, double encounterGameTime) {
    public boolean matches(UUID threatUuid) {
        return threatUuid != null && threatUuid.equals(this.threatUuid);
    }

    public double remainingTicks(long gameTime) {
        return this.encounterGameTime - gameTime;
    }

    public static double signedTimingError(
            double remainingTicks,
            double aimTicks,
            double flightTicks
    ) {
        return aimTicks + flightTicks - remainingTicks;
    }

    public static boolean deadlineMissed(double signedTimingError, double toleranceTicks) {
        return signedTimingError > toleranceTicks;
    }

    public static double correctionReserveTicks(
            double remainingTicks,
            double aimTicks,
            double flightTicks
    ) {
        return remainingTicks - aimTicks - flightTicks;
    }

    public static boolean hasMeaningfullyLargerCorrectionReserve(
            double candidateReserveTicks,
            double currentReserveTicks,
            double minimumImprovementTicks
    ) {
        return candidateReserveTicks
                > currentReserveTicks + Math.max(0.0D, minimumImprovementTicks);
    }
}
