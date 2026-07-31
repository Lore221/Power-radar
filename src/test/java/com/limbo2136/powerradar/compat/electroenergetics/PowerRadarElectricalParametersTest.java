package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarElectricalParametersTest {
    @Test
    void unloadedConfigUsesRegisteredSpecDefaults() {
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        defaultDouble("voltages.radar.nominal"),
                        defaultDouble("voltages.radar.minimum"),
                        defaultDouble("voltages.radar.restart"),
                        defaultDouble("voltages.radar.maximum"),
                        defaultDouble("voltages.radar.overvoltage_recovery")),
                PowerRadarElectricalParameters.Voltages.radar());
        assertEquals(defaultDouble("voltages.radar.full_range"),
                PowerRadarElectricalParameters.Voltages.radarFullRange());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        defaultDouble("voltages.monitor.nominal"),
                        defaultDouble("voltages.monitor.minimum"),
                        defaultDouble("voltages.monitor.restart"),
                        defaultDouble("voltages.monitor.maximum"),
                        defaultDouble("voltages.monitor.overvoltage_recovery")),
                PowerRadarElectricalParameters.Voltages.monitor());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        defaultDouble("voltages.shell_alarm.nominal"),
                        defaultDouble("voltages.shell_alarm.minimum"),
                        defaultDouble("voltages.shell_alarm.restart"),
                        defaultDouble("voltages.shell_alarm.maximum"),
                        defaultDouble("voltages.shell_alarm.overvoltage_recovery")),
                PowerRadarElectricalParameters.Voltages.shellAlarm());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(
                        defaultDouble("voltages.target_controller.minimum"),
                        defaultDouble("voltages.target_controller.full_speed"),
                        defaultDouble("voltages.target_controller.maximum")),
                PowerRadarElectricalParameters.Voltages.targetController());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(
                        defaultDouble("voltages.interception_controller.minimum"),
                        defaultDouble("voltages.interception_controller.full_speed"),
                        defaultDouble("voltages.interception_controller.maximum")),
                PowerRadarElectricalParameters.Voltages.interceptionController());

        assertEquals(defaultDouble("resistances.target_controller_ohms"),
                PowerRadarElectricalParameters.Resistances.targetController());
        assertEquals(defaultDouble("resistances.interception_controller_ohms"),
                PowerRadarElectricalParameters.Resistances.interceptionController());
        assertEquals(defaultDouble("ratings.radar_controller_power_watts"),
                PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts());
        assertEquals(defaultDouble("ratings.phased_array_panel_power_watts"),
                PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts());
        assertEquals(defaultDouble("ratings.overview_module_power_watts"),
                PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts());
        assertEquals(defaultDouble("ratings.monitor_controller_power_watts"),
                PowerRadarElectricalParameters.Ratings.monitorControllerPowerWatts());
        assertEquals(defaultDouble("ratings.radar_display_power_watts"),
                PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts());
        assertEquals(defaultDouble("ratings.panel_radar_link_power_watts"),
                PowerRadarElectricalParameters.Ratings.panelRadarLinkPowerWatts());
        assertEquals(defaultDouble("ratings.logic_dock_power_watts"),
                PowerRadarElectricalParameters.Ratings.logicDockPowerWatts());
        assertEquals(defaultDouble("ratings.onboard_computer_power_watts"),
                PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts());
        assertEquals(defaultDouble("ratings.shell_alarm_power_watts"),
                PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts());
    }

    @Test
    void electricalCatalogValuesStayPhysicallyValid() {
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.radar());
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.monitor());
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.shellAlarm());
        assertDriveRangeValid(PowerRadarElectricalParameters.Voltages.targetController());
        assertDriveRangeValid(PowerRadarElectricalParameters.Voltages.interceptionController());

        PowerRadarElectricalParameters.LoadVoltageRange radar =
                PowerRadarElectricalParameters.Voltages.radar();
        assertTrue(PowerRadarElectricalParameters.Voltages.radarFullRange() >= radar.minimum());
        assertTrue(PowerRadarElectricalParameters.Voltages.radarFullRange() <= radar.maximum());

        assertTrue(PowerRadarElectricalParameters.Resistances.targetController() > 0.0D);
        assertTrue(PowerRadarElectricalParameters.Resistances.interceptionController() > 0.0D);
        for (double rating : allPowerRatings()) {
            assertTrue(Double.isFinite(rating));
            assertTrue(rating >= 0.0D);
        }
    }

    private static double defaultDouble(String path) {
        ModConfigSpec.ConfigValue<?> value = PowerRadarServerConfig.SPEC.getValues().get(path);
        assertTrue(value != null, () -> "Missing server config path: " + path);
        return ((Number) value.getDefault()).doubleValue();
    }

    private static void assertLoadRangeValid(PowerRadarElectricalParameters.LoadVoltageRange range) {
        assertTrue(Double.isFinite(range.nominal()));
        assertTrue(range.minimum() >= 0.0D);
        assertTrue(range.restart() >= range.minimum());
        assertTrue(range.nominal() >= range.minimum());
        assertTrue(range.nominal() <= range.maximum());
        assertTrue(range.overvoltageRecovery() >= range.minimum());
        assertTrue(range.overvoltageRecovery() <= range.maximum());
    }

    private static void assertDriveRangeValid(PowerRadarElectricalParameters.DriveVoltageRange range) {
        assertTrue(range.minimum() >= 0.0D);
        assertTrue(range.fullSpeed() >= range.minimum());
        assertTrue(range.maximum() >= range.fullSpeed());
    }

    private static List<Double> allPowerRatings() {
        return List.of(
                PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts(),
                PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts(),
                PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts(),
                PowerRadarElectricalParameters.Ratings.monitorControllerPowerWatts(),
                PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts(),
                PowerRadarElectricalParameters.Ratings.panelRadarLinkPowerWatts(),
                PowerRadarElectricalParameters.Ratings.logicDockPowerWatts(),
                PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts(),
                PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts());
    }
}
