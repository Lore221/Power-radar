package com.limbo2136.powerradar.tooltip;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Единое место, где задаются состав и порядок строк подсказок Power Radar.
 * Удаление строки скрывает её, перестановка меняет порядок, а text("lang.key") вставляет авторский текст.
 */
public final class PowerRadarTooltipSettings {
    public enum Target {
        RADAR_CONTROLLER,
        PHASED_ARRAY_PANEL,
        OVERVIEW_MODULE,
        MONITOR_CONTROLLER,
        RADAR_DISPLAY,
        COMPUTING_BLOCK,
        ONBOARD_COMPUTER,
        TARGET_CONTROLLER,
        INTERCEPTION_CONTROLLER,
        SHELL_ALARM
    }

    public sealed interface Field permits InventoryField, GoggleField {
    }

    public enum InventoryField implements Field {
        NOMINAL_POWER,
        RANGE_BONUS,
        INTERNAL_RESISTANCE,
        WORKING_VOLTAGE,
        AUTOCANNON_MIN_DISTANCE,
        BIG_CANNON_MIN_DISTANCE,
        PROTECTION_ZONE
    }

    public enum GoggleField implements Field {
        TITLE,
        STATUS,
        SCAN_MODE,
        CURRENT_RANGE,
        ELECTRICAL_STATE,
        VOLTAGE,
        CURRENT,
        POWER,
        PANEL_COUNT,
        BASIC_PANEL_COUNT,
        EFFECTIVE_RANGE,
        DISPLAY_COUNT,
        CARD_SLOTS,
        NETWORK_STATUS,
        PROTECTION_ZONE,
        SHELL_COUNT,
        ALARM_STATE,
        INTERCEPT_TIME
    }

    /** Одна строка раскладки: вычисляемое поле или произвольный ключ из lang. */
    public record Line(Field field, String translationKey, ChatFormatting style) {
        public boolean isText() {
            return this.translationKey != null;
        }
    }

    // Shift в инвентаре без надетых инженерных очков Create.
    private static final Map<Target, List<Line>> INVENTORY_WITHOUT_GOGGLES = Map.ofEntries(
            Map.entry(Target.RADAR_CONTROLLER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.PHASED_ARRAY_PANEL, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.OVERVIEW_MODULE, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.MONITOR_CONTROLLER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.RADAR_DISPLAY, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.COMPUTING_BLOCK, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.ONBOARD_COMPUTER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.TARGET_CONTROLLER, List.of(
                    field(InventoryField.INTERNAL_RESISTANCE),
                    field(InventoryField.WORKING_VOLTAGE),
                    field(InventoryField.AUTOCANNON_MIN_DISTANCE),
                    field(InventoryField.BIG_CANNON_MIN_DISTANCE))),
            Map.entry(Target.INTERCEPTION_CONTROLLER, List.of(
                    field(InventoryField.INTERNAL_RESISTANCE),
                    field(InventoryField.WORKING_VOLTAGE))),
            Map.entry(Target.SHELL_ALARM, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.WORKING_VOLTAGE),
                    field(InventoryField.PROTECTION_ZONE)))
    );

    // Shift в инвентаре с надетыми инженерными очками Create. Список настраивается независимо.
    private static final Map<Target, List<Line>> INVENTORY_WITH_GOGGLES = Map.ofEntries(
            Map.entry(Target.RADAR_CONTROLLER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.PHASED_ARRAY_PANEL, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.OVERVIEW_MODULE, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.MONITOR_CONTROLLER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.RADAR_DISPLAY, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.COMPUTING_BLOCK, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.ONBOARD_COMPUTER, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.TARGET_CONTROLLER, List.of(
                    field(InventoryField.INTERNAL_RESISTANCE),
                    field(InventoryField.WORKING_VOLTAGE),
                    field(InventoryField.AUTOCANNON_MIN_DISTANCE),
                    field(InventoryField.BIG_CANNON_MIN_DISTANCE))),
            Map.entry(Target.INTERCEPTION_CONTROLLER, List.of(
                    field(InventoryField.INTERNAL_RESISTANCE),
                    field(InventoryField.WORKING_VOLTAGE))),
            Map.entry(Target.SHELL_ALARM, List.of(
                    field(InventoryField.NOMINAL_POWER),
                    field(InventoryField.WORKING_VOLTAGE),
                    field(InventoryField.PROTECTION_ZONE)))
    );

    // Подсказка в мире при взгляде на установленный блок через инженерные очки Create.
    private static final Map<Target, List<Line>> GOGGLES = Map.ofEntries(
            Map.entry(Target.RADAR_CONTROLLER, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.STATUS),
                    field(GoggleField.SCAN_MODE),
                    field(GoggleField.CURRENT_RANGE),
                    field(GoggleField.ELECTRICAL_STATE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.POWER),
                    field(GoggleField.PANEL_COUNT),
                    field(GoggleField.BASIC_PANEL_COUNT),
                    field(GoggleField.EFFECTIVE_RANGE))),
            Map.entry(Target.MONITOR_CONTROLLER, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.ELECTRICAL_STATE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.POWER),
                    field(GoggleField.DISPLAY_COUNT))),
            Map.entry(Target.COMPUTING_BLOCK, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.CARD_SLOTS),
                    field(GoggleField.NETWORK_STATUS))),
            Map.entry(Target.ONBOARD_COMPUTER, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.ELECTRICAL_STATE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.POWER))),
            Map.entry(Target.TARGET_CONTROLLER, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.CURRENT),
                    field(GoggleField.POWER),
                    field(GoggleField.STATUS))),
            Map.entry(Target.INTERCEPTION_CONTROLLER, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.CURRENT),
                    field(GoggleField.POWER),
                    field(GoggleField.STATUS),
                    field(GoggleField.INTERCEPT_TIME))),
            Map.entry(Target.SHELL_ALARM, List.of(
                    field(GoggleField.TITLE),
                    field(GoggleField.ELECTRICAL_STATE),
                    field(GoggleField.VOLTAGE),
                    field(GoggleField.POWER),
                    field(GoggleField.PROTECTION_ZONE),
                    field(GoggleField.SHELL_COUNT),
                    field(GoggleField.ALARM_STATE)))
    );

    private PowerRadarTooltipSettings() {
    }

    // В списках выше используй text("ключ.из.lang") или text("ключ.из.lang", ChatFormatting.COLOR).
    private static Line text(String translationKey) {
        return text(translationKey, ChatFormatting.GRAY);
    }

    private static Line text(String translationKey, ChatFormatting style) {
        return new Line(null, translationKey, style);
    }

    private static Line field(Field field) {
        return new Line(field, null, null);
    }

    public static List<Line> inventory(Target target, boolean wearingGoggles) {
        Map<Target, List<Line>> source = wearingGoggles
                ? INVENTORY_WITH_GOGGLES
                : INVENTORY_WITHOUT_GOGGLES;
        return source.getOrDefault(target, List.of());
    }

    public static List<Line> goggles(Target target) {
        return GOGGLES.getOrDefault(target, List.of());
    }

    /** Добавляет произвольную lang-строку; false означает, что вызывающий блок должен вычислить системное поле. */
    public static boolean appendText(List<Component> tooltip, Line line) {
        if (!line.isText()) {
            return false;
        }
        tooltip.add(Component.translatable(line.translationKey()).withStyle(line.style()));
        return true;
    }
}
