package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.radar.RadarTargetCategory;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PowerRadarClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue MONITOR_BLOCK_ALIGNED_VIEW;
    private static final ModConfigSpec.EnumValue<AttitudeIndicatorRenderMode> ATTITUDE_INDICATOR_RENDER_MODE;
    private static final ModConfigSpec.ConfigValue<String> RADAR_CONE_COLOR;
    private static final ModConfigSpec.ConfigValue<String> SHELL_ALARM_ZONE_COLOR;
    private static final ModConfigSpec.ConfigValue<String> SABLE_SILHOUETTE_COLOR;
    private static final ModConfigSpec.ConfigValue<String> PROJECTILE_BLIP_COLOR;
    private static final ModConfigSpec.ConfigValue<String> PLAYER_BLIP_COLOR;
    private static final ModConfigSpec.ConfigValue<String> PASSIVE_BLIP_COLOR;
    private static final ModConfigSpec.ConfigValue<String> HOSTILE_BLIP_COLOR;
    private static final ModConfigSpec.ConfigValue<String> STRUCTURE_BLIP_COLOR;
    private static final ModConfigSpec.ConfigValue<String> HOVERED_TARGET_FRAME_COLOR;
    private static final ModConfigSpec.ConfigValue<String> SELECTED_TARGET_FRAME_COLOR;

    private static String cachedConeColor;
    private static String cachedShellAlarmZoneColor;
    private static String cachedSableSilhouetteColor;
    private static String cachedProjectileColor;
    private static String cachedPlayerColor;
    private static String cachedPassiveColor;
    private static String cachedHostileColor;
    private static String cachedStructureColor;
    private static String cachedHoveredFrameColor;
    private static String cachedSelectedFrameColor;
    private static volatile RadarRenderPalette cachedPalette;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("monitor");
        MONITOR_BLOCK_ALIGNED_VIEW = builder
                .comment(
                        "Default false keeps monitor maps world-aligned with north at the top.",
                        "When true, monitor maps use the legacy block-facing-aligned view.")
                .define("block_aligned_view", false);
        RADAR_CONE_COLOR = defineColor(builder, "cone_color", "#B8FFD2",
                "Color applied to the white radar coverage cone texture.");
        SHELL_ALARM_ZONE_COLOR = defineColor(builder, "shell_alarm_zone_color", "#90EE90",
                "Color of active Shell Alarm protection zones.");
        SABLE_SILHOUETTE_COLOR = defineColor(builder, "sable_silhouette_color", "#FF0000",
                "Color of Sable structure silhouette outlines and fills.");
        builder.push("blips");
        PROJECTILE_BLIP_COLOR = defineColor(builder, "projectile_color", "#FFAA00",
                "Color of projectile blips.");
        PLAYER_BLIP_COLOR = defineColor(builder, "player_color", "#FFFF55",
                "Color of player blips.");
        PASSIVE_BLIP_COLOR = defineColor(builder, "passive_mob_color", "#55FF55",
                "Color of passive and neutral mob blips.");
        HOSTILE_BLIP_COLOR = defineColor(builder, "hostile_mob_color", "#FF5555",
                "Color of hostile mob blips.");
        STRUCTURE_BLIP_COLOR = defineColor(builder, "structure_color", "#8A739F",
                "Color of Create contraption, radar structure, and Sable structure blips.");
        HOVERED_TARGET_FRAME_COLOR = defineColor(builder, "hovered_target_frame_color", "#FFFFFF",
                "Color of the frame drawn around the target under the mouse cursor.");
        SELECTED_TARGET_FRAME_COLOR = defineColor(builder, "selected_target_frame_color", "#FF0000",
                "Color of the frame drawn around the selected target.");
        builder.pop();
        builder.pop();
        builder.push("onboard_computer");
        ATTITUDE_INDICATOR_RENDER_MODE = builder
                .comment(
                        "Selects the experimental attitude-indicator renderer.",
                        "TEXTURE_STRIP projects the cyclic texture under the window.",
                        "OBJ_SPHERE rotates the low-poly attitude sphere inside the housing.")
                .defineEnum("attitude_indicator_render_mode", AttitudeIndicatorRenderMode.TEXTURE_STRIP);
        builder.pop();
        SPEC = builder.build();
    }

    private PowerRadarClientConfig() {
    }

    public static boolean monitorBlockAlignedView() {
        return SPEC.isLoaded() ? MONITOR_BLOCK_ALIGNED_VIEW.get() : MONITOR_BLOCK_ALIGNED_VIEW.getDefault();
    }

    public static AttitudeIndicatorRenderMode attitudeIndicatorRenderMode() {
        return SPEC.isLoaded()
                ? ATTITUDE_INDICATOR_RENDER_MODE.get()
                : ATTITUDE_INDICATOR_RENDER_MODE.getDefault();
    }

    // Новые варианты можно добавлять сюда, не меняя сохранённый ключ конфигурации.
    public enum AttitudeIndicatorRenderMode {
        TEXTURE_STRIP,
        OBJ_SPHERE
    }

    public static RadarRenderPalette radarRenderPalette() {
        // Снимок палитры пересобирается только после изменения строк конфига и переиспользуется за кадр.
        String cone = colorValue(RADAR_CONE_COLOR);
        String shellAlarmZone = colorValue(SHELL_ALARM_ZONE_COLOR);
        String sableSilhouette = colorValue(SABLE_SILHOUETTE_COLOR);
        String projectile = colorValue(PROJECTILE_BLIP_COLOR);
        String player = colorValue(PLAYER_BLIP_COLOR);
        String passive = colorValue(PASSIVE_BLIP_COLOR);
        String hostile = colorValue(HOSTILE_BLIP_COLOR);
        String structure = colorValue(STRUCTURE_BLIP_COLOR);
        String hovered = colorValue(HOVERED_TARGET_FRAME_COLOR);
        String selected = colorValue(SELECTED_TARGET_FRAME_COLOR);
        RadarRenderPalette palette = cachedPalette;
        if (palette == null
                || !cone.equals(cachedConeColor)
                || !shellAlarmZone.equals(cachedShellAlarmZoneColor)
                || !sableSilhouette.equals(cachedSableSilhouetteColor)
                || !projectile.equals(cachedProjectileColor)
                || !player.equals(cachedPlayerColor)
                || !passive.equals(cachedPassiveColor)
                || !hostile.equals(cachedHostileColor)
                || !structure.equals(cachedStructureColor)
                || !hovered.equals(cachedHoveredFrameColor)
                || !selected.equals(cachedSelectedFrameColor)) {
            palette = new RadarRenderPalette(
                    parseRgb(cone),
                    parseRgb(shellAlarmZone),
                    parseRgb(sableSilhouette),
                    parseRgb(projectile),
                    parseRgb(player),
                    parseRgb(passive),
                    parseRgb(hostile),
                    parseRgb(structure),
                    parseRgb(hovered),
                    parseRgb(selected));
            cachedConeColor = cone;
            cachedShellAlarmZoneColor = shellAlarmZone;
            cachedSableSilhouetteColor = sableSilhouette;
            cachedProjectileColor = projectile;
            cachedPlayerColor = player;
            cachedPassiveColor = passive;
            cachedHostileColor = hostile;
            cachedStructureColor = structure;
            cachedHoveredFrameColor = hovered;
            cachedSelectedFrameColor = selected;
            cachedPalette = palette;
        }
        return palette;
    }

    private static ModConfigSpec.ConfigValue<String> defineColor(
            ModConfigSpec.Builder builder,
            String name,
            String defaultValue,
            String comment
    ) {
        return builder.comment(comment, "Format: #RRGGBB or RRGGBB.")
                .define(name, defaultValue, PowerRadarClientConfig::isValidRgb);
    }

    private static String colorValue(ModConfigSpec.ConfigValue<String> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static boolean isValidRgb(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String digits = text.startsWith("#") ? text.substring(1) : text;
        return digits.matches("[0-9a-fA-F]{6}");
    }

    private static int parseRgb(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        return Integer.parseInt(digits, 16);
    }

    public record RadarRenderPalette(
            int cone,
            int shellAlarmZone,
            int sableSilhouette,
            int projectile,
            int player,
            int passive,
            int hostile,
            int structure,
            int hoveredFrame,
            int selectedFrame
    ) {
        public int blip(RadarTargetCategory category) {
            return switch (category) {
                case PROJECTILE -> this.projectile;
                case PLAYER -> this.player;
                case PASSIVE_MOB -> this.passive;
                case HOSTILE_MOB -> this.hostile;
                case SABLE_STRUCTURE, UNKNOWN -> this.structure;
            };
        }

        public int red(int color) {
            return color >> 16 & 0xFF;
        }

        public int green(int color) {
            return color >> 8 & 0xFF;
        }

        public int blue(int color) {
            return color & 0xFF;
        }
    }
}
