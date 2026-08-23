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
                        defaultDouble("voltages.radar.maximum")),
                PowerRadarElectricalParameters.Voltages.radar());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        defaultDouble("voltages.radar_display.nominal"),
                        defaultDouble("voltages.radar_display.minimum"),
                        defaultDouble("voltages.radar_display.maximum")),
                PowerRadarElectricalParameters.Voltages.radarDisplay());
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                        defaultDouble("voltages.shell_alarm.nominal"),
                        defaultDouble("voltages.shell_alarm.minimum"),
                        defaultDouble("voltages.shell_alarm.maximum")),
                PowerRadarElectricalParameters.Voltages.shellAlarm());
        assertLoadDefaults("logic_dock", PowerRadarElectricalParameters.Voltages.logicDock());
        assertLoadDefaults("onboard_computer", PowerRadarElectricalParameters.Voltages.onboardComputer());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(
                        defaultDouble("voltages.target_controller.minimum"),
                        defaultDouble("voltages.target_controller.nominal"),
                        defaultDouble("voltages.target_controller.maximum")),
                PowerRadarElectricalParameters.Voltages.targetController());
        assertEquals(new PowerRadarElectricalParameters.DriveVoltageRange(
                        defaultDouble("voltages.interception_controller.minimum"),
                        defaultDouble("voltages.interception_controller.nominal"),
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
        assertEquals(defaultDouble("ratings.radar_display_base_power_watts"),
                PowerRadarElectricalParameters.Ratings.radarDisplayBasePowerWatts());
        assertEquals(defaultDouble("ratings.radar_display_power_watts"),
                PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts());
        assertEquals(defaultDouble("ratings.panel_radar_display_power_watts"),
                PowerRadarElectricalParameters.Ratings.panelRadarDisplayPowerWatts());
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
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.radarDisplay());
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.shellAlarm());
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.logicDock());
        assertLoadRangeValid(PowerRadarElectricalParameters.Voltages.onboardComputer());
        assertDriveRangeValid(PowerRadarElectricalParameters.Voltages.targetController());
        assertDriveRangeValid(PowerRadarElectricalParameters.Voltages.interceptionController());

        assertTrue(PowerRadarElectricalParameters.Resistances.targetController() > 0.0D);
        assertTrue(PowerRadarElectricalParameters.Resistances.interceptionController() > 0.0D);
        for (double rating : allPowerRatings()) {
            assertTrue(Double.isFinite(rating));
            assertTrue(rating >= 0.0D);
        }
    }

    @Test
    void nominalRatingCreatesFixedResistanceInsteadOfConstantPower() {
        double resistance = PowerRadarCeeConstants.nominalResistanceOhms(220.0D, 250.0D);

        assertEquals(193.6D, resistance, 0.000_001D);
        assertEquals(250.0D, PowerRadarCeeConstants.powerWatts(220.0D, resistance), 0.000_001D);
        assertEquals(62.5D, PowerRadarCeeConstants.powerWatts(110.0D, resistance), 0.000_001D);
        assertEquals(0.568_181_818D, PowerRadarCeeConstants.currentAmps(110.0D, resistance), 0.000_001D);
    }

    private static double defaultDouble(String path) {
        ModConfigSpec.ConfigValue<?> value = PowerRadarServerConfig.SPEC.getValues().get(path);
        assertTrue(value != null, () -> "Missing server config path: " + path);
        return ((Number) value.getDefault()).doubleValue();
    }

    @Test
    void rootDisplayUsesBasePowerAndOnlyAdditionalBlocksAddPanelPower() {
        double basePower = PowerRadarElectricalParameters.Ratings.radarDisplayBasePowerWatts();
        double additionalPower = PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts();

        assertEquals(0.0D, PowerRadarCeeConstants.monitorNominalPowerWatts(0));
        assertEquals(basePower, PowerRadarCeeConstants.monitorNominalPowerWatts(1));
        assertEquals(basePower + additionalPower, PowerRadarCeeConstants.monitorNominalPowerWatts(2));
        assertEquals(basePower + 8.0D * additionalPower,
                PowerRadarCeeConstants.monitorNominalPowerWatts(9));
    }

    private static void assertLoadDefaults(
            String path,
            PowerRadarElectricalParameters.LoadVoltageRange actual
    ) {
        assertEquals(new PowerRadarElectricalParameters.LoadVoltageRange(
                defaultDouble("voltages." + path + ".nominal"),
                defaultDouble("voltages." + path + ".minimum"),
                defaultDouble("voltages." + path + ".maximum")), actual);
    }

    private static void assertLoadRangeValid(PowerRadarElectricalParameters.LoadVoltageRange range) {
        assertTrue(Double.isFinite(range.nominal()));
        assertTrue(range.minimum() >= 0.0D);
        assertTrue(range.nominal() >= range.minimum());
        assertTrue(range.nominal() <= range.maximum());
    }

    private static void assertDriveRangeValid(PowerRadarElectricalParameters.DriveVoltageRange range) {
        assertTrue(range.minimum() >= 0.0D);
        assertTrue(range.nominal() >= range.minimum());
        assertTrue(range.maximum() >= range.nominal());
    }

    private static List<Double> allPowerRatings() {
        return List.of(
                PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts(),
                PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts(),
                PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts(),
                PowerRadarElectricalParameters.Ratings.radarDisplayBasePowerWatts(),
                PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts(),
                PowerRadarElectricalParameters.Ratings.panelRadarDisplayPowerWatts(),
                PowerRadarElectricalParameters.Ratings.logicDockPowerWatts(),
                PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts(),
                PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts());
    }
}
