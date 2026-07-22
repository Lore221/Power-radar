package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Единый каталог геометрии и дальности радаров Power Radar.
 * Верхний блок содержит игровые значения по умолчанию, а серверный TOML может переопределить их.
 */
public final class PowerRadarRadarParameters {
    // Состав радара и вклад каждого установленного модуля в итоговую дальность.
    private static final int DEFAULT_MAX_PHASED_ARRAY_PANELS = 20;
    private static final int DEFAULT_MAX_OVERVIEW_MODULES = 5;
    private static final int DEFAULT_BASE_RANGE_BLOCKS = 80;
    private static final int DEFAULT_PHASED_ARRAY_PANEL_RANGE_BLOCKS = 20;
    private static final int DEFAULT_OVERVIEW_MODULE_RANGE_BLOCKS = 60;

    // Горизонтальная геометрия направленного радара.
    private static final double DEFAULT_AIR_RANGE_MULTIPLIER = 1.5D;
    private static final double DEFAULT_AIR_FOV_DEGREES = 90.0D;

    // Вертикальные границы поиска задаются относительно контроллера в блоках.
    private static final int DEFAULT_GROUND_UP_BLOCKS = 128;
    private static final int DEFAULT_GROUND_DOWN_BLOCKS = 20;
    private static final int DEFAULT_SURFACE_DOWN_BLOCKS = 1_500;
    private static final int DEFAULT_AIR_MIN_Y_OFFSET = 40;
    private static final int DEFAULT_AIR_MAX_Y_OFFSET = 1_500;

    private static ModConfigSpec.IntValue maxPhasedArrayPanels;
    private static ModConfigSpec.IntValue maxOverviewModules;
    private static ModConfigSpec.IntValue baseRangeBlocks;
    private static ModConfigSpec.IntValue phasedArrayPanelRangeBlocks;
    private static ModConfigSpec.IntValue overviewModuleRangeBlocks;
    private static ModConfigSpec.DoubleValue airRangeMultiplier;
    private static ModConfigSpec.DoubleValue airFovDegrees;
    private static ModConfigSpec.IntValue groundUpBlocks;
    private static ModConfigSpec.IntValue groundDownBlocks;
    private static ModConfigSpec.IntValue surfaceDownBlocks;
    private static ModConfigSpec.IntValue airMinYOffset;
    private static ModConfigSpec.IntValue airMaxYOffset;

    private PowerRadarRadarParameters() {
    }

    /** Подключает каталог к прежней секции radar_range общего серверного конфига. */
    public static void defineConfig(ModConfigSpec.Builder builder) {
        builder.comment("Power Radar range, module limits, and field of view.").push("radar_range");
        maxPhasedArrayPanels = builder.comment("Maximum phased-array panels on one radar.")
                .defineInRange("max_radar_panels", DEFAULT_MAX_PHASED_ARRAY_PANELS, 1, 512);
        maxOverviewModules = builder.comment("Maximum overview modules on one overview radar.")
                .defineInRange("max_overview_modules", DEFAULT_MAX_OVERVIEW_MODULES, 1, 512);
        baseRangeBlocks = builder.comment("Base range of an assembled radar before module bonuses, in blocks.")
                .defineInRange("base_range_blocks", DEFAULT_BASE_RANGE_BLOCKS, 0, 100_000);
        phasedArrayPanelRangeBlocks = builder.comment("Range bonus from one phased-array panel, in blocks.")
                .defineInRange("basic_panel_range_bonus_blocks", DEFAULT_PHASED_ARRAY_PANEL_RANGE_BLOCKS,
                        0, 100_000);
        overviewModuleRangeBlocks = builder.comment("Range bonus from one overview module, in blocks.")
                .defineInRange("overview_module_range_bonus_blocks", DEFAULT_OVERVIEW_MODULE_RANGE_BLOCKS,
                        0, 100_000);
        airRangeMultiplier = builder.comment("Air radar range multiplier.")
                .defineInRange("air_range_multiplier", DEFAULT_AIR_RANGE_MULTIPLIER, 0.0D, 100.0D);
        airFovDegrees = builder.comment("Full horizontal air radar field of view, in degrees.")
                .defineInRange("air_fov_degrees", DEFAULT_AIR_FOV_DEGREES, 1.0D, 360.0D);
        groundUpBlocks = builder.comment("Ground radar scan height above the controller, in blocks.")
                .defineInRange("ground_up_blocks", DEFAULT_GROUND_UP_BLOCKS, 0, 4_096);
        groundDownBlocks = builder.comment("Ground radar scan depth below the controller, in blocks.")
                .defineInRange("ground_down_blocks", DEFAULT_GROUND_DOWN_BLOCKS, 0, 4_096);
        surfaceDownBlocks = builder.comment("Surface radar scan depth below the controller, in blocks.")
                .defineInRange("surface_down_blocks", DEFAULT_SURFACE_DOWN_BLOCKS, 0, 32_000);
        airMinYOffset = builder.comment("Lower vertical offset of the air radar scan, in blocks.")
                .defineInRange("air_min_y_offset", DEFAULT_AIR_MIN_Y_OFFSET, -4_096, 4_096);
        airMaxYOffset = builder.comment("Upper vertical offset of the air radar scan, in blocks.")
                .defineInRange("air_max_y_offset", DEFAULT_AIR_MAX_Y_OFFSET, 0, 32_000);
        builder.pop();
    }

    public static int maxPhasedArrayPanels() {
        ensureConfigDefined();
        return value(maxPhasedArrayPanels);
    }

    public static int maxOverviewModules() {
        ensureConfigDefined();
        return value(maxOverviewModules);
    }

    public static int baseRangeBlocks() {
        ensureConfigDefined();
        return value(baseRangeBlocks);
    }

    public static int phasedArrayPanelRangeBlocks() {
        ensureConfigDefined();
        return value(phasedArrayPanelRangeBlocks);
    }

    public static int overviewModuleRangeBlocks() {
        ensureConfigDefined();
        return value(overviewModuleRangeBlocks);
    }

    public static double airRangeMultiplier() {
        ensureConfigDefined();
        return value(airRangeMultiplier);
    }

    public static double airFovDegrees() {
        ensureConfigDefined();
        return value(airFovDegrees);
    }

    public static int groundUpBlocks() {
        ensureConfigDefined();
        return value(groundUpBlocks);
    }

    public static int groundDownBlocks() {
        ensureConfigDefined();
        return value(groundDownBlocks);
    }

    public static int surfaceDownBlocks() {
        ensureConfigDefined();
        return value(surfaceDownBlocks);
    }

    public static int airMinYOffset() {
        ensureConfigDefined();
        return value(airMinYOffset);
    }

    public static int airMaxYOffset() {
        ensureConfigDefined();
        return Math.max(airMinYOffset(), value(airMaxYOffset));
    }

    // Ранний вызов из блока сначала заставляет общий серверный SPEC зарегистрировать этот каталог.
    private static void ensureConfigDefined() {
        if (baseRangeBlocks == null) {
            ModConfigSpec ignored = PowerRadarServerConfig.SPEC;
        }
    }

    private static int value(ModConfigSpec.IntValue configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }

    private static double value(ModConfigSpec.DoubleValue configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }
}
