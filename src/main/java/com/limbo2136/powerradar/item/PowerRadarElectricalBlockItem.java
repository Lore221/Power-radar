package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.radar.PowerRadarRadarParameters;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.InventoryField;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.ChatFormatting;
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
        if (!isShiftDown()) {
            tooltip.add(Component.translatable("power_radar.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        boolean wearingGoggles = isClientPlayerWearingGoggles();
        for (PowerRadarTooltipSettings.Line line
                : PowerRadarTooltipSettings.inventory(this.tooltipTarget, wearingGoggles)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            appendInventoryField(tooltip, (InventoryField) line.field());
        }
    }

    // Преобразует выбранные в PowerRadarTooltipSettings поля в строки с актуальными параметрами блока.
    private void appendInventoryField(List<Component> tooltip, InventoryField field) {
        switch (field) {
            case NOMINAL_POWER -> tooltip.add(property(
                    "power_radar.tooltip.nominal_power",
                    PowerRadarCeeFormatter.powerComponent(nominalPowerWatts())));
            case RANGE_BONUS -> tooltip.add(property(
                    "power_radar.tooltip.range_bonus",
                    Component.translatable("power_radar.unit.blocks_bonus", rangeBonusBlocks())));
            case INTERNAL_RESISTANCE -> tooltip.add(property(
                    "power_radar.tooltip.internal_resistance",
                    PowerRadarCeeFormatter.resistanceComponent(internalResistanceOhms())));
            case WORKING_VOLTAGE -> {
                PowerRadarElectricalParameters.DriveVoltageRange drive = driveVoltageRange();
                PowerRadarElectricalParameters.LoadVoltageRange load = loadVoltageRange();
                double minimum = drive != null ? drive.minimum() : load.minimum();
                double maximum = drive != null ? drive.maximum() : load.maximum();
                tooltip.add(property("power_radar.tooltip.working_voltage",
                        Component.literal(PowerRadarCeeFormatter.voltageRange(minimum, maximum))));
            }
            case AUTOCANNON_MIN_DISTANCE -> tooltip.add(property(
                    "power_radar.tooltip.autocannon_min_distance",
                    Component.translatable("power_radar.unit.blocks",
                            formatBlocks(PowerRadarServerConfig.autocannonMinFiringDistanceBlocks()))));
            case BIG_CANNON_MIN_DISTANCE -> tooltip.add(property(
                    "power_radar.tooltip.big_cannon_min_distance",
                    Component.translatable("power_radar.unit.blocks",
                            formatBlocks(PowerRadarServerConfig.bigCannonMinFiringDistanceBlocks()))));
            case PROTECTION_ZONE -> tooltip.add(property(
                    "power_radar.tooltip.shell_alarm_zone",
                    Component.translatable(
                            "power_radar.tooltip.shell_alarm_dimensions",
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_WIDTH_BLOCKS,
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS,
                            PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_DEPTH_BLOCKS)));
        }
    }

    private double nominalPowerWatts() {
        return switch (this.tooltipTarget) {
            case RADAR_CONTROLLER -> PowerRadarElectricalParameters.Ratings.radarControllerPowerWatts();
            case PHASED_ARRAY_PANEL -> PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts();
            case OVERVIEW_MODULE -> PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts();
            case MONITOR_CONTROLLER -> PowerRadarElectricalParameters.Ratings.monitorControllerPowerWatts();
            case RADAR_DISPLAY -> PowerRadarElectricalParameters.Ratings.radarDisplayPowerWatts();
            case COMPUTING_BLOCK -> PowerRadarElectricalParameters.Ratings.computingBlockPowerWatts();
            case ONBOARD_COMPUTER -> PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts();
            case SHELL_ALARM -> PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts();
            case TARGET_CONTROLLER, INTERCEPTION_CONTROLLER -> 0.0D;
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

    private PowerRadarElectricalParameters.DriveVoltageRange driveVoltageRange() {
        return switch (this.tooltipTarget) {
            case TARGET_CONTROLLER -> PowerRadarElectricalParameters.Voltages.targetController();
            case INTERCEPTION_CONTROLLER -> PowerRadarElectricalParameters.Voltages.interceptionController();
            default -> null;
        };
    }

    private PowerRadarElectricalParameters.LoadVoltageRange loadVoltageRange() {
        return this.tooltipTarget == Target.SHELL_ALARM
                ? PowerRadarElectricalParameters.Voltages.shellAlarm()
                : PowerRadarElectricalParameters.Voltages.monitor();
    }

    private static String formatBlocks(double blocks) {
        return blocks == Math.rint(blocks)
                ? Long.toString(Math.round(blocks))
                : String.format(java.util.Locale.ROOT, "%.1f", blocks);
    }

    private static Component property(String key, Component value) {
        return Component.translatable(key, value.copy().withStyle(ChatFormatting.DARK_AQUA))
                .withStyle(ChatFormatting.GRAY);
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
