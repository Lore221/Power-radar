package com.limbo2136.powerradar.tooltip;

import java.util.List;
import java.util.Map;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Единое место, где задаются состав и порядок строк подсказок Power Radar.
 * field(...) постоянно показывает параметр, а text("lang.key") добавляет описание только при зажатом Shift.
 */
public final class PowerRadarTooltipSettings {
    public enum Target {
        RADAR_CONTROLLER,
        PHASED_ARRAY_PANEL,
        OVERVIEW_MODULE,
        MONITOR_CONTROLLER,
        RADAR_DISPLAY,
        LOGIC_DOCK,
        ONBOARD_COMPUTER,
        TARGET_CONTROLLER,
        INTERCEPTION_CONTROLLER,
        SHELL_ALARM,
        RADAR_LINK,
        MECHANICAL_SIREN,
        TARGETING_CARD,
        ALLOWLIST_CARD,
        DISPLAY_CARD,
        INTERCEPTION_FUZE,
        LINKER
    }

    public sealed interface Field permits InventoryField, GoggleField {
    }

    public enum InventoryField implements Field {
        NOMINAL_POWER,
        NOMINAL_VOLTAGE,
        RANGE_BONUS,
        INTERNAL_RESISTANCE,
        PROTECTION_ZONE
    }

    public enum GoggleField implements Field {
        TITLE,
        STATUS,
        SCAN_MODE,
        ELECTRICAL_STATE,
        VOLTAGE,
        CURRENT,
        POWER,
        EFFECTIVE_RANGE,
        CARD_SLOTS,
        NETWORK_STATUS,
        PROTECTION_ZONE,
        ALARM_STATE
    }

    /**
     * Одна строка раскладки: вычисляемое поле или произвольный ключ из lang.
     * style задаёт основной цвет, highlightStyle — цвет текста между символами "_".
     */
    public record Line(
            Field field,
            String translationKey,
            ChatFormatting style,
            ChatFormatting highlightStyle
    ) {
        // Старый формат строки без отдельного цвета выделения сохраняет один цвет для всего текста.
        public Line(Field field, String translationKey, ChatFormatting style) {
            this(field, translationKey, style, style);
        }

        public boolean isText() {
            return this.translationKey != null;
        }
    }

    // Постоянные параметры предметов. Очки меняют только отображение: оценка или точное значение.
    private static final Map<Target, List<Line>> INVENTORY_PARAMETERS = Map.ofEntries(
            Map.entry(Target.RADAR_CONTROLLER, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.PHASED_ARRAY_PANEL, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.OVERVIEW_MODULE, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.RANGE_BONUS))),
            Map.entry(Target.MONITOR_CONTROLLER, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.RADAR_DISPLAY, List.of(field(InventoryField.NOMINAL_POWER))),
            Map.entry(Target.LOGIC_DOCK, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.ONBOARD_COMPUTER, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.TARGET_CONTROLLER, List.of(
                field(InventoryField.INTERNAL_RESISTANCE),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.INTERCEPTION_CONTROLLER, List.of(
                field(InventoryField.INTERNAL_RESISTANCE),
                field(InventoryField.NOMINAL_VOLTAGE))),
            Map.entry(Target.SHELL_ALARM, List.of(
                field(InventoryField.NOMINAL_POWER),
                field(InventoryField.NOMINAL_VOLTAGE),
                field(InventoryField.PROTECTION_ZONE)))
    );

    // Описания общие для режима с очками и без них и раскрываются только по Shift.
    private static final Map<Target, List<Line>> INVENTORY_SHIFT_TEXT = Map.ofEntries(
            Map.entry(Target.MECHANICAL_SIREN, List.of(text("mechanical_siren_text"))),
            Map.entry(Target.TARGETING_CARD, List.of(text("targeting_card_text"))),
            Map.entry(Target.ALLOWLIST_CARD, List.of(text("allowlist_card_text"))),
            Map.entry(Target.DISPLAY_CARD, List.of(text("display_card_text"))),
            Map.entry(Target.INTERCEPTION_FUZE, List.of(text("intercept_fuze_text"))),
            Map.entry(Target.LINKER, List.of(text("linker_text")))
    );

    // Подсказка в мире при взгляде на установленный блок через инженерные очки Create.
    private static final Map<Target, List<Line>> GOGGLES = Map.ofEntries(
            Map.entry(Target.RADAR_CONTROLLER, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.STATUS),
                field(GoggleField.POWER),
                field(GoggleField.EFFECTIVE_RANGE))),
            Map.entry(Target.MONITOR_CONTROLLER, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER))),
            Map.entry(Target.LOGIC_DOCK, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER),
                field(GoggleField.CARD_SLOTS))),
            Map.entry(Target.ONBOARD_COMPUTER, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER))),
            Map.entry(Target.TARGET_CONTROLLER, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER),
                field(GoggleField.STATUS))),
            Map.entry(Target.INTERCEPTION_CONTROLLER, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER),
                field(GoggleField.STATUS))),
            Map.entry(Target.SHELL_ALARM, List.of(
                field(GoggleField.TITLE),
                field(GoggleField.POWER),
                field(GoggleField.PROTECTION_ZONE),
                field(GoggleField.ALARM_STATE)))
    );

    private PowerRadarTooltipSettings() {
    }

    // В lang обрамляй выделяемые слова символами "_": "Обычный текст _выделенный текст_".
    // Здесь задаются основной цвет и цвет выделения для всех простых вызовов text("ключ.из.lang").
    private static Line text(String translationKey) {
        return text(translationKey, ChatFormatting.GOLD, ChatFormatting.YELLOW);
    }

    // У отдельной строки цвета можно переопределить вторым и третьим аргументами.
    private static Line text(
            String translationKey,
            ChatFormatting style,
            ChatFormatting highlightStyle
    ) {
        return new Line(null, translationKey, style, highlightStyle);
    }

    private static Line field(Field field) {
        return new Line(field, null, null, null);
    }

    public static List<Line> inventoryParameters(Target target) {
        return INVENTORY_PARAMETERS.getOrDefault(target, List.of());
    }

    public static List<Line> inventoryShiftText(Target target) {
        return INVENTORY_SHIFT_TEXT.getOrDefault(target, List.of());
    }

    public static List<Line> goggles(Target target) {
        return GOGGLES.getOrDefault(target, List.of());
    }

    // Заголовок добавляется без отдельного отступа: вся готовая подсказка выравнивается одним проходом.
    public static void appendElectricalStatisticsTitle(List<Component> tooltip) {
        tooltip.add(Component.translatable("goggles.power_radar.electric_stats"));
    }

    /**
     * Добавляет ко всем новым строкам стандартный отступ Create шириной 32 пикселя.
     * Индекс нужен, чтобы не менять строки, которые другой источник мог добавить в список раньше.
     */
    public static boolean finishGoggleTooltip(List<Component> tooltip, int firstNewLine) {
        for (int index = Math.max(0, firstNewLine); index < tooltip.size(); index++) {
            List<Component> indented = new java.util.ArrayList<>(1);
            CreateLang.builder()
                    .add(tooltip.get(index))
                    .forGoggles(indented);
            tooltip.set(index, indented.getFirst());
        }
        return true;
    }

    /**
     * Добавляет произвольную lang-строку и выделяет другим цветом фрагменты между символами "_".
     * false означает, что вызывающий блок должен вычислить системное поле.
     */
    public static boolean appendText(List<Component> tooltip, Line line) {
        if (!line.isText()) {
            return false;
        }

        String translatedText = Component.translatable(line.translationKey()).getString();
        String[] parts = translatedText.split("_", -1);
        MutableComponent formattedLine = Component.empty();
        boolean highlighted = false;
        for (String part : parts) {
            ChatFormatting color = highlighted ? line.highlightStyle() : line.style();
            formattedLine.append(Component.literal(part).withStyle(color));
            highlighted = !highlighted;
        }
        tooltip.add(formattedLine);
        return true;
    }
}
