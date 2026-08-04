package com.limbo2136.powerradar.compat.electroenergetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerRadarCeeLoadMathTest {
    private static final PowerRadarElectricalParameters.LoadVoltageRange RANGE =
            new PowerRadarElectricalParameters.LoadVoltageRange(220.0D, 180.0D, 250.0D);

    @Test
    void classifiesVoltageDirectlyWithoutHysteresis() {
        assertEquals(
                PowerRadarCeeState.OVERVOLTAGE,
                PowerRadarCeeLoadMath.resolveState(true, 251.0D, RANGE));
        assertEquals(
                PowerRadarCeeState.POWERED,
                PowerRadarCeeLoadMath.resolveState(true, 250.0D, RANGE));
        assertEquals(
                PowerRadarCeeState.UNDERVOLTAGE,
                PowerRadarCeeLoadMath.resolveState(true, 179.0D, RANGE));
        assertEquals(
                PowerRadarCeeState.POWERED,
                PowerRadarCeeLoadMath.resolveState(true, 180.0D, RANGE));
    }
}
