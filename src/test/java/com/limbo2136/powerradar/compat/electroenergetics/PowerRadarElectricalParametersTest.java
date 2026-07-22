package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarElectricalParametersTest {
    @Test
    void unloadedConfigUsesCatalogDefaults() {
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        600.0D, 400.0D, 430.0D, 700.0D, 650.0D),
                PowerRadarElectricalParameters.Voltages.radar());
        assertEquals(600.0D, PowerRadarElectricalParameters.Voltages.radarFullRange());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        24.0D, 18.0D, 20.0D, 30.0D, 28.0D),
                PowerRadarElectricalParameters.Voltages.monitor());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        400.0D, 300.0D, 320.0D, 400.0D, 380.0D),
                PowerRadarElectricalParameters.Voltages.shellAlarm());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(200.0D, 300.0D, 360.0D),
                PowerRadarElectricalParameters.Voltages.targetController());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(200.0D, 300.0D, 360.0D),
                PowerRadarElectricalParameters.Voltages.interceptionController());
    }

    @Test
    void everyElectricalBlockRatingComesFromTheCatalog() {
        assertEquals(30.0D, PowerRadarElectricalParameters.Resistances.targetController());
        assertEquals(30.0D, PowerRadarElectricalParameters.Resistances.interceptionController());
        assertEquals(1_000.0D, PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts());
        assertEquals(700.0D, PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts());
        assertEquals(700.0D, PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts());
        assertEquals(45.0D, PowerRadarElectricalParameters.Ratings.monitorControllerPowerWatts());
        assertEquals(5.0D, PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts());
        assertEquals(45.0D, PowerRadarElectricalParameters.Ratings.computingBlockPowerWatts());
        assertEquals(50.0D, PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts());
        assertEquals(45.0D, PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts());
    }

    @Test
    void existingServerConfigPathsStayCompatible() {
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("voltages.radar.nominal"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("voltages.monitor.minimum"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("voltages.shell_alarm.maximum"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("voltages.target_controller.full_speed"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("resistances.target_controller_ohms"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("ratings.onboard_computer_power_watts"));
    }
}
