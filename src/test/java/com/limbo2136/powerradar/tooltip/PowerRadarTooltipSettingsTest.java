package com.limbo2136.powerradar.tooltip;

import java.util.List;
import java.util.HashSet;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarTooltipSettingsTest {
    @Test
    void editableLayoutsContainOnlyWellFormedLines() {
        for (PowerRadarTooltipSettings.Target target : PowerRadarTooltipSettings.Target.values()) {
            List<PowerRadarTooltipSettings.Line> parameters =
                    PowerRadarTooltipSettings.inventoryParameters(target);
            List<PowerRadarTooltipSettings.Line> shiftText =
                    PowerRadarTooltipSettings.inventoryShiftText(target);
            assertLayoutValid(parameters);
            assertLayoutValid(shiftText);
            assertLayoutValid(PowerRadarTooltipSettings.goggles(target));
            assertTrue(parameters.stream().noneMatch(PowerRadarTooltipSettings.Line::isText));
            assertTrue(shiftText.stream().allMatch(PowerRadarTooltipSettings.Line::isText));
        }
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

    private static void assertLayoutValid(List<PowerRadarTooltipSettings.Line> lines) {
        HashSet<PowerRadarTooltipSettings.Field> fields = new HashSet<>();
        for (PowerRadarTooltipSettings.Line line : lines) {
            assertTrue(line.isText() ^ (line.field() != null));
            if (line.isText()) {
                assertFalse(line.translationKey().isBlank());
                assertTrue(line.style() != null);
            } else {
                assertTrue(fields.add(line.field()), () -> "Duplicate tooltip field: " + line.field());
            }
        }
    }
}
