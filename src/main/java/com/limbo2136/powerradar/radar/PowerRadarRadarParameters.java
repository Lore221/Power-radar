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
    private static final int DEFAULT_OVERVIEW_MODULE_RANGE_BLOCKS = 50;

    // Горизонтальная геометрия направленного радара.
    private static final double DEFAULT_AIR_RANGE_MULTIPLIER = 1.5D;
    private static final RadarFieldOfView DEFAULT_GROUND_FOV = RadarFieldOfView.DEGREES_90;
    private static final RadarFieldOfView DEFAULT_AIR_FOV = RadarFieldOfView.DEGREES_60;
    private static final RadarFieldOfView DEFAULT_SURFACE_FOV = RadarFieldOfView.DEGREES_120;

    // Вертикальные границы поиска задаются относительно контроллера в блоках.
    private static final int DEFAULT_GROUND_UP_BLOCKS = 128;
    private static final int DEFAULT_GROUND_DOWN_BLOCKS = 20;
    private static final int DEFAULT_SURFACE_DOWN_BLOCKS = 1_500;
    private static final int DEFAULT_SURFACE_MAX_Y_OFFSET = -20;
    private static final int DEFAULT_AIR_MIN_Y_OFFSET = 40;
    private static final int DEFAULT_AIR_MAX_Y_OFFSET = 1_500;

    // Бортовой радар имеет фиксированную геометрию: конус 90° с радиусом 200
    // блоков и симметричным вертикальным диапазоном.
    private static final int AIRCRAFT_RANGE_BLOCKS = 200;
    private static final int AIRCRAFT_FOV_DEGREES = 90;
    private static final int AIRCRAFT_MIN_Y_OFFSET = -200;
    private static final int AIRCRAFT_MAX_Y_OFFSET = 200;

    private static ModConfigSpec.IntValue maxPhasedArrayPanels;
    private static ModConfigSpec.IntValue maxOverviewModules;
    private static ModConfigSpec.IntValue baseRangeBlocks;
    private static ModConfigSpec.IntValue phasedArrayPanelRangeBlocks;
    private static ModConfigSpec.IntValue overviewModuleRangeBlocks;
    private static ModConfigSpec.DoubleValue airRangeMultiplier;
    private static ModConfigSpec.EnumValue<RadarFieldOfView> groundFov;
    private static ModConfigSpec.EnumValue<RadarFieldOfView> airFov;
    private static ModConfigSpec.EnumValue<RadarFieldOfView> surfaceFov;
    private static ModConfigSpec.IntValue groundUpBlocks;
    private static ModConfigSpec.IntValue groundDownBlocks;
    private static ModConfigSpec.IntValue surfaceDownBlocks;
    private static ModConfigSpec.IntValue surfaceMaxYOffset;
    private static ModConfigSpec.IntValue airMinYOffset;
    private static ModConfigSpec.IntValue airMaxYOffset;

    private PowerRadarRadarParameters() {
    }

    /** Подключает отдельные секции геометрии радаров и параметров модулей к серверному конфигу. */
    public static void defineConfig(ModConfigSpec.Builder builder) {
        builder.comment("Radar panel and overview-module limits and range bonuses.").push("panels");
        maxPhasedArrayPanels = builder.comment("Maximum phased-array panels on one radar.")
                .defineInRange("max_radar_panels", DEFAULT_MAX_PHASED_ARRAY_PANELS, 1, 512);
        maxOverviewModules = builder.comment("Maximum overview modules on one overview radar.")
                .defineInRange("max_overview_modules", DEFAULT_MAX_OVERVIEW_MODULES, 1, 512);
        phasedArrayPanelRangeBlocks = builder.comment("Range bonus from one phased-array panel, in blocks.")
                .defineInRange("basic_panel_range_bonus_blocks", DEFAULT_PHASED_ARRAY_PANEL_RANGE_BLOCKS,
                        0, 100_000);
        overviewModuleRangeBlocks = builder.comment("Range bonus from one overview module, in blocks.")
                .defineInRange("overview_module_range_bonus_blocks", DEFAULT_OVERVIEW_MODULE_RANGE_BLOCKS,
                        0, 100_000);
        builder.pop();

        builder.comment("Base radar range and scan geometry.").push("base_radar");
        baseRangeBlocks = builder.comment("Base range of an assembled radar before module bonuses, in blocks.")
                .defineInRange("base_range_blocks", DEFAULT_BASE_RANGE_BLOCKS, 0, 100_000);
        groundFov = builder.comment("Base radar field of view: DEGREES_60, DEGREES_90, or DEGREES_120.")
                .defineEnum("fov", DEFAULT_GROUND_FOV);
        groundUpBlocks = builder.comment("Base radar scan height above the controller, in blocks.")
                .defineInRange("up_blocks", DEFAULT_GROUND_UP_BLOCKS, 0, 4_096);
        groundDownBlocks = builder.comment("Base radar scan depth below the controller, in blocks.")
                .defineInRange("down_blocks", DEFAULT_GROUND_DOWN_BLOCKS, 0, 4_096);
        builder.pop();

        builder.comment("Air radar range and scan geometry.").push("air_radar");
        airRangeMultiplier = builder.comment("Air radar range multiplier.")
                .defineInRange("range_multiplier", DEFAULT_AIR_RANGE_MULTIPLIER, 0.0D, 100.0D);
        airFov = builder.comment("Air radar field of view: DEGREES_60, DEGREES_90, or DEGREES_120.")
                .defineEnum("fov", DEFAULT_AIR_FOV);
        airMinYOffset = builder.comment("Lower vertical offset of the air radar scan, in blocks.")
                .defineInRange("min_y_offset", DEFAULT_AIR_MIN_Y_OFFSET, -4_096, 4_096);
        airMaxYOffset = builder.comment("Upper vertical offset of the air radar scan, in blocks.")
                .defineInRange("max_y_offset", DEFAULT_AIR_MAX_Y_OFFSET, 0, 32_000);
        builder.pop();

        builder.comment("Onboard surface radar range and scan geometry.").push("surface_radar");
        surfaceFov = builder.comment("Onboard radar field of view: DEGREES_60, DEGREES_90, or DEGREES_120.")
                .defineEnum("fov", DEFAULT_SURFACE_FOV);
        surfaceDownBlocks = builder.comment("Surface radar scan depth below the controller, in blocks.")
                .defineInRange("down_blocks", DEFAULT_SURFACE_DOWN_BLOCKS, 0, 32_000);
        surfaceMaxYOffset = builder.comment("Upper vertical offset of the onboard radar scan, in blocks.")
                .defineInRange("max_y_offset", DEFAULT_SURFACE_MAX_Y_OFFSET, -4_096, 0);
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

    public static int groundFovDegrees() {
        ensureConfigDefined();
        return value(groundFov).degrees();
    }

    public static int airFovDegrees() {
        ensureConfigDefined();
        return value(airFov).degrees();
    }

    public static int surfaceFovDegrees() {
        ensureConfigDefined();
        return value(surfaceFov).degrees();
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

    public static int surfaceMinYOffset() {
        return -surfaceDownBlocks();
    }

    public static int surfaceMaxYOffset() {
        ensureConfigDefined();
        return Math.max(surfaceMinYOffset(), value(surfaceMaxYOffset));
    }

    public static int airMinYOffset() {
        ensureConfigDefined();
        return value(airMinYOffset);
    }

    public static int airMaxYOffset() {
        ensureConfigDefined();
        return Math.max(airMinYOffset(), value(airMaxYOffset));
    }

    // При раннем вызове из блока принудительно завершаем инициализацию общего серверного конфига.
    private static void ensureConfigDefined() {
        if (baseRangeBlocks == null) {
            PowerRadarServerConfig.ensureInitialized();
        }
    }

    public static int aircraftRangeBlocks() {
        return AIRCRAFT_RANGE_BLOCKS;
    }

    public static int aircraftFovDegrees() {
        return AIRCRAFT_FOV_DEGREES;
    }

    public static int aircraftMinYOffset() {
        return AIRCRAFT_MIN_Y_OFFSET;
    }

    public static int aircraftMaxYOffset() {
        return AIRCRAFT_MAX_Y_OFFSET;
    }

    private static int value(ModConfigSpec.IntValue configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }

    private static double value(ModConfigSpec.DoubleValue configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }

    public enum RadarFieldOfView {
        DEGREES_60(60),
        DEGREES_90(90),
        DEGREES_120(120);

        private final int degrees;

        RadarFieldOfView(int degrees) {
            this.degrees = degrees;
        }

        public int degrees() {
            return this.degrees;
        }
    }
}
