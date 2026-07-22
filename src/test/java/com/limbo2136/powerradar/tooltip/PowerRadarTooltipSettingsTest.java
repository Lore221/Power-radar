package com.limbo2136.powerradar.tooltip;

import java.util.List;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarTooltipSettingsTest {
    @Test
    void inventoryLayoutsAreSelectedByGoggleState() {
        assertEquals(List.of(
                        PowerRadarTooltipSettings.InventoryField.NOMINAL_POWER,
                        PowerRadarTooltipSettings.InventoryField.RANGE_BONUS),
                PowerRadarTooltipSettings.inventory(
                                PowerRadarTooltipSettings.Target.OVERVIEW_MODULE, false)
                        .stream()
                        .map(PowerRadarTooltipSettings.Line::field)
                        .toList());
        assertEquals(List.of(
                        PowerRadarTooltipSettings.InventoryField.NOMINAL_POWER,
                        PowerRadarTooltipSettings.InventoryField.RANGE_BONUS),
                PowerRadarTooltipSettings.inventory(
                                PowerRadarTooltipSettings.Target.OVERVIEW_MODULE, true)
                        .stream()
                        .map(PowerRadarTooltipSettings.Line::field)
                        .toList());
    }

    @Test
    void goggleLayoutPreservesConfiguredOrder() {
        assertEquals(List.of(
                        PowerRadarTooltipSettings.GoggleField.TITLE,
                        PowerRadarTooltipSettings.GoggleField.VOLTAGE,
                        PowerRadarTooltipSettings.GoggleField.CURRENT,
                        PowerRadarTooltipSettings.GoggleField.POWER,
                        PowerRadarTooltipSettings.GoggleField.STATUS),
                PowerRadarTooltipSettings.goggles(
                                PowerRadarTooltipSettings.Target.TARGET_CONTROLLER)
                        .stream()
                        .map(PowerRadarTooltipSettings.Line::field)
                        .toList());
    }

    @Test
    void authoredTextLineKeepsTranslationKeyAndStyle() {
        PowerRadarTooltipSettings.Line line = new PowerRadarTooltipSettings.Line(
                null,
                "tooltip.power_radar.authored_text",
                ChatFormatting.YELLOW);

        assertTrue(line.isText());
        assertEquals("tooltip.power_radar.authored_text", line.translationKey());
        assertEquals(ChatFormatting.YELLOW, line.style());
    }
}
