package com.limbo2136.powerradar.compat.electroenergetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerRadarCeeLoadMathTest {
    private static final PowerRadarElectricalParameters.LoadVoltageRange RANGE =
            new PowerRadarElectricalParameters.LoadVoltageRange(220.0D, 180.0D, 200.0D, 250.0D, 240.0D);

    @Test
    void overvoltageShutdownWaitsForRecoveryThreshold() {
        assertEquals(
                PowerRadarCeeState.OVERVOLTAGE,
                PowerRadarCeeLoadMath.resolveState(true, PowerRadarCeeState.OVERVOLTAGE, 245.0D, RANGE));
        assertEquals(
                PowerRadarCeeState.POWERED,
                PowerRadarCeeLoadMath.resolveState(true, PowerRadarCeeState.OVERVOLTAGE, 230.0D, RANGE));
    }

    @Test
    void recoveredOvervoltageDoesNotStartAtUndervoltage() {
        assertEquals(
                PowerRadarCeeState.UNDERVOLTAGE,
                PowerRadarCeeLoadMath.resolveState(true, PowerRadarCeeState.OVERVOLTAGE, 150.0D, RANGE));
    }
}
