package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class PowerRadarElectricalBlockItem extends BlockItem {
    public enum TooltipKind {
        RADAR_CONTROLLER,
        BASIC_RADAR_PANEL,
        MONITOR_CONTROLLER,
        RADAR_DISPLAY,
        TARGET_CONTROLLER,
        INTERCEPTION_CONTROLLER,
        SHELL_ALARM
    }

    private final TooltipKind tooltipKind;

    public PowerRadarElectricalBlockItem(Block block, Item.Properties properties, TooltipKind tooltipKind) {
        super(block, properties);
        this.tooltipKind = tooltipKind;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!isShiftDown()) {
            tooltip.add(Component.translatable("power_radar.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        switch (this.tooltipKind) {
            case RADAR_CONTROLLER -> tooltip.add(property(
                    "power_radar.tooltip.nominal_power",
                    PowerRadarCeeFormatter.powerComponent(PowerRadarCeeConstants.radarControllerPowerWatts())));
            case BASIC_RADAR_PANEL -> {
                tooltip.add(property(
                        "power_radar.tooltip.nominal_power",
                        PowerRadarCeeFormatter.powerComponent(PowerRadarCeeConstants.basicRadarPanelPowerWatts())));
                tooltip.add(property(
                        "power_radar.tooltip.range_bonus",
                        Component.translatable("power_radar.unit.blocks_bonus",
                                PowerRadarCeeConstants.basicPanelRangeBonusBlocks())));
            }
            case MONITOR_CONTROLLER -> tooltip.add(property(
                    "power_radar.tooltip.nominal_power",
                    PowerRadarCeeFormatter.powerComponent(PowerRadarCeeConstants.monitorControllerPowerWatts())));
            case RADAR_DISPLAY -> tooltip.add(property(
                    "power_radar.tooltip.nominal_power",
                    PowerRadarCeeFormatter.powerComponent(PowerRadarCeeConstants.radarDisplayPowerWatts())));
            case TARGET_CONTROLLER -> {
                tooltip.add(property(
                        "power_radar.tooltip.internal_resistance",
                        PowerRadarCeeFormatter.resistanceComponent(
                                PowerRadarCeeConstants.TARGET_CONTROLLER_POWER_RESISTANCE_OHMS)));
                tooltip.add(property(
                        "power_radar.tooltip.working_voltage",
                        Component.literal(PowerRadarCeeFormatter.voltageRange(
                                PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE,
                                PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE))));
                tooltip.add(property(
                        "power_radar.tooltip.autocannon_min_distance",
                        Component.translatable(
                                "power_radar.unit.blocks",
                                formatBlocks(PowerRadarServerConfig.autocannonMinFiringDistanceBlocks()))));
                tooltip.add(property(
                        "power_radar.tooltip.big_cannon_min_distance",
                        Component.translatable(
                                "power_radar.unit.blocks",
                                formatBlocks(PowerRadarServerConfig.bigCannonMinFiringDistanceBlocks()))));
            }
            case INTERCEPTION_CONTROLLER -> {
                tooltip.add(property(
                        "power_radar.tooltip.internal_resistance",
                        PowerRadarCeeFormatter.resistanceComponent(
                                PowerRadarCeeConstants.TARGET_CONTROLLER_POWER_RESISTANCE_OHMS)));
                tooltip.add(property(
                        "power_radar.tooltip.working_voltage",
                        Component.literal(PowerRadarCeeFormatter.voltageRange(
                                PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE,
                                PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE))));
            }
            case SHELL_ALARM -> {
                tooltip.add(property(
                        "power_radar.tooltip.nominal_power",
                        PowerRadarCeeFormatter.powerComponent(PowerRadarCeeConstants.SHELL_ALARM_POWER_WATTS)));
                tooltip.add(property(
                        "power_radar.tooltip.working_voltage",
                        Component.literal(PowerRadarCeeFormatter.voltageRange(
                                PowerRadarCeeConstants.SHELL_ALARM_MIN_VOLTAGE,
                                PowerRadarCeeConstants.SHELL_ALARM_MAX_VOLTAGE))));
                tooltip.add(property(
                        "power_radar.tooltip.shell_alarm_side",
                        Component.translatable("power_radar.unit.blocks",
                                PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_SIDE_BLOCKS)));
            }
        }
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

    private static boolean isShiftDown() {
        try {
            Class<?> screen = Class.forName("net.minecraft.client.gui.screens.Screen");
            Object result = screen.getMethod("hasShiftDown").invoke(null);
            return result instanceof Boolean value && value;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }
}
