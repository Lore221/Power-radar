package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.radar.PowerRadarRadarParameters;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.InventoryField;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.item.TooltipHelper;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class PowerRadarElectricalBlockItem extends BlockItem {
    private final Target tooltipTarget;

    public PowerRadarElectricalBlockItem(Block block, Item.Properties properties, Target tooltipTarget) {
        super(block, properties);
        this.tooltipTarget = tooltipTarget;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        boolean wearingGoggles = isClientPlayerWearingGoggles();
        boolean shiftDown = isShiftDown();
        List<PowerRadarTooltipSettings.Line> shiftText =
                PowerRadarTooltipSettings.inventoryShiftText(this.tooltipTarget);
        if (shiftDown) {
            for (PowerRadarTooltipSettings.Line line : shiftText) {
                appendWrappedText(tooltip, line);
            }
        } else if (!shiftText.isEmpty()) {
            appendShiftHint(tooltip);
        }

        boolean electricalSectionStarted = false;
        for (PowerRadarTooltipSettings.Line line
                : PowerRadarTooltipSettings.inventoryParameters(this.tooltipTarget)) {
            InventoryField field = (InventoryField) line.field();
            if (!electricalSectionStarted && isElectricalStat(field)) {
                tooltip.add(CommonComponents.EMPTY);
                electricalSectionStarted = true;
            }
            appendInventoryField(tooltip, field, wearingGoggles);
        }
    }

    // Преобразует выбранные в PowerRadarTooltipSettings поля в строки с актуальными параметрами блока.
    private void appendInventoryField(List<Component> tooltip, InventoryField field, boolean wearingGoggles) {
        switch (field) {
            case NOMINAL_POWER -> appendElectricalStat(
                    tooltip,
                    "power_radar.tooltip.nominal_power",
                    PowerRadarCeeFormatter.powerComponent(nominalPowerWatts()),
                    powerLevel(nominalPowerWatts()),
                    wearingGoggles);
            case NOMINAL_VOLTAGE -> appendElectricalStat(
                    tooltip,
                    "power_radar.tooltip.nominal_voltage",
                    PowerRadarCeeFormatter.voltageComponent(nominalVoltageVolts()),
                    scaledLevel(nominalVoltageVolts(), 200.0D),
                    wearingGoggles);
            case RANGE_BONUS -> tooltip.add(property(
                    "power_radar.tooltip.range_bonus",
                    Component.translatable("power_radar.unit.blocks_bonus", rangeBonusBlocks())));
            case INTERNAL_RESISTANCE -> appendElectricalStat(
                    tooltip,
                    "power_radar.tooltip.internal_resistance",
                    PowerRadarCeeFormatter.resistanceComponent(internalResistanceOhms()),
                    scaledLevel(internalResistanceOhms(), 300.0D),
                    wearingGoggles);
            case PROTECTION_ZONE -> tooltip.add(property(
                    "power_radar.tooltip.shell_alarm_zone",
                    Component.translatable(
                            "power_radar.tooltip.shell_alarm_dimensions",
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_WIDTH_BLOCKS,
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS,
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_DEPTH_BLOCKS)));
        }
    }

    // Электрические характеристики повторяют трёхсегментную шкалу CEE.
    // Очки открывают точное значение, без очков остаётся только словесная оценка уровня.
    private static void appendElectricalStat(
            List<Component> tooltip,
            String labelKey,
            Component exactValue,
            int level,
            boolean wearingGoggles
    ) {
        int safeLevel = Math.max(0, Math.min(3, level));
        ChatFormatting color = levelColor(safeLevel);
        Component displayedValue = wearingGoggles
                ? exactValue
                : Component.translatable(magnitudeKey(safeLevel));
        tooltip.add(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(TooltipHelper.makeProgressBar(3, safeLevel))
                .append(displayedValue)
                .withStyle(color));
    }

    private static boolean isElectricalStat(InventoryField field) {
        return field == InventoryField.NOMINAL_POWER
                || field == InventoryField.NOMINAL_VOLTAGE
                || field == InventoryField.INTERNAL_RESISTANCE;
    }

    // Пороговые значения полностью совпадают с ElectricStatsTooltipModifier из CEE.
    private static int powerLevel(double watts) {
        if (watts < 500.0D) {
            return 0;
        }
        if (watts < 1_000.0D) {
            return 1;
        }
        return watts < 7_500.0D ? 2 : 3;
    }

    private static int scaledLevel(double value, double step) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0;
        }
        return Math.max(0, Math.min(3, (int) Math.floor(value / step)));
    }

    private static ChatFormatting levelColor(int level) {
        return switch (level) {
            case 0 -> ChatFormatting.AQUA;
            case 1 -> ChatFormatting.YELLOW;
            case 2 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    private static String magnitudeKey(int level) {
        return switch (level) {
            case 0 -> "power_radar.tooltip.magnitude.very_low";
            case 1 -> "power_radar.tooltip.magnitude.low";
            case 2 -> "power_radar.tooltip.magnitude.moderate";
            default -> "power_radar.tooltip.magnitude.high";
        };
    }

    private double nominalPowerWatts() {
        return switch (this.tooltipTarget) {
            case RADAR_CONTROLLER -> PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts();
            case PHASED_ARRAY_PANEL -> PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts();
            case OVERVIEW_MODULE -> PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts();
            case MONITOR_CONTROLLER -> PowerRadarElectricalParameters.Ratings.monitorControllerPowerWatts();
            case RADAR_DISPLAY -> PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts();
            case LOGIC_DOCK -> PowerRadarElectricalParameters.Ratings.logicDockPowerWatts();
            case ONBOARD_COMPUTER -> PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts();
            case SHELL_ALARM -> PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts();
            case TARGET_CONTROLLER, INTERCEPTION_CONTROLLER, RADAR_LINK, MECHANICAL_SIREN,
                    TARGETING_CARD, ALLOWLIST_CARD, DISPLAY_CARD, INTERCEPTION_FUZE, LINKER -> 0.0D;
        };
    }

    private int rangeBonusBlocks() {
        return switch (this.tooltipTarget) {
            case PHASED_ARRAY_PANEL -> PowerRadarRadarParameters.phasedArrayPanelRangeBlocks();
            case OVERVIEW_MODULE -> PowerRadarRadarParameters.overviewModuleRangeBlocks();
            default -> 0;
        };
    }

    private double internalResistanceOhms() {
        return this.tooltipTarget == Target.INTERCEPTION_CONTROLLER
                ? PowerRadarElectricalParameters.Resistances.interceptionController()
                : PowerRadarElectricalParameters.Resistances.targetController();
    }

    private double nominalVoltageVolts() {
        return switch (this.tooltipTarget) {
            case RADAR_CONTROLLER, PHASED_ARRAY_PANEL, OVERVIEW_MODULE ->
                    PowerRadarElectricalParameters.Voltages.radar().nominal();
            case SHELL_ALARM -> PowerRadarElectricalParameters.Voltages.shellAlarm().nominal();
            case TARGET_CONTROLLER -> PowerRadarElectricalParameters.Voltages.targetController().fullSpeed();
            case INTERCEPTION_CONTROLLER ->
                    PowerRadarElectricalParameters.Voltages.interceptionController().fullSpeed();
            case MONITOR_CONTROLLER, RADAR_DISPLAY, LOGIC_DOCK, ONBOARD_COMPUTER ->
                    PowerRadarElectricalParameters.Voltages.monitor().nominal();
            case RADAR_LINK, MECHANICAL_SIREN,
                    TARGETING_CARD, ALLOWLIST_CARD, DISPLAY_CARD, INTERCEPTION_FUZE, LINKER -> 0.0D;
        };
    }

    private static Component property(String key, Component value) {
        return Component.translatable(key, value.copy().withStyle(ChatFormatting.DARK_AQUA))
                .withStyle(ChatFormatting.GRAY);
    }

    private static void appendShiftHint(List<Component> tooltip) {
        tooltip.add(Component.translatable("power_radar.tooltip.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    static void appendConfiguredText(Target target, List<Component> tooltip) {
        boolean shiftDown = isShiftDown();
        List<PowerRadarTooltipSettings.Line> shiftText =
                PowerRadarTooltipSettings.inventoryShiftText(target);
        if (!shiftDown && !shiftText.isEmpty()) {
            appendShiftHint(tooltip);
            return;
        }
        for (PowerRadarTooltipSettings.Line line : shiftText) {
            appendWrappedText(tooltip, line);
        }
    }

    // Штатный перенос Create ограничивает описание шириной 200 пикселей и сохраняет выделение через "_".
    private static void appendWrappedText(List<Component> tooltip, PowerRadarTooltipSettings.Line line) {
        tooltip.addAll(TooltipHelper.cutTextComponent(
                Component.translatable(line.translationKey()),
                TooltipHelper.styleFromColor(line.style()),
                TooltipHelper.styleFromColor(line.highlightStyle())));
    }

    // Клиентские классы читаются отражением, чтобы общий BlockItem не создавал прямую клиентскую зависимость.
    private static boolean isShiftDown() {
        try {
            Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen");
            Object result = screen.getMethod("hasShiftDown").invoke(null);
            return result instanceof Boolean value && value;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException exception) {
            return false;
        }
    }

    // Определяет отдельную раскладку Shift-подсказки, когда локальный игрок надел очки Create.
    private static boolean isClientPlayerWearingGoggles() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object player = minecraft.getField("player").get(instance);
            return player instanceof Player localPlayer && GogglesItem.isWearingGoggles(localPlayer);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException
                 | IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

}
