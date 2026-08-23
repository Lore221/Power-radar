package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.block.entity.AbstractRadarMonitorBlockEntity;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.SelectedTargetRuntimeSnapshot;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Поля выбранной цели для Create Display Link. Источник читает общий runtime-снимок сети
 * корневого дисплея только во время пассивного обновления и не экстраполирует цель.
 */
public final class SelectedMonitorTargetDisplaySource extends SingleLineDisplaySource {
    private static final String LAST_VALUE_KEY = "PowerRadarLastValue";
    private static final String SPEED_SAMPLE_TARGET_KEY = "PowerRadarSpeedSampleTarget";
    private static final String SPEED_SAMPLE_TIME_KEY = "PowerRadarSpeedSampleTime";
    private static final String SPEED_SAMPLE_X_KEY = "PowerRadarSpeedSampleX";
    private static final String SPEED_SAMPLE_Y_KEY = "PowerRadarSpeedSampleY";
    private static final String SPEED_SAMPLE_Z_KEY = "PowerRadarSpeedSampleZ";
    private static final String SPEED_SAMPLE_VALUE_KEY = "PowerRadarSpeedSampleMetersPerSecond";
    private static final String SPEED_UNIT_KEY = "PowerRadarSpeedUnit";
    private static final double ZERO_SPEED_VALUE = 0.0D;

    private final Field field;

    public SelectedMonitorTargetDisplaySource(Field field) {
        this.field = field;
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        Output output = output(context);
        return output == null ? EMPTY_LINE : output.text();
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return this.field == Field.TYPE || this.field == Field.SPEED;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(
            DisplayLinkContext context,
            ModularGuiLineBuilder builder,
            boolean isFirstLine
    ) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (this.field != Field.SPEED || isFirstLine) {
            return;
        }
        builder.addSelectionScrollInput(0, 95, (selection, label) -> selection.forOptions(List.of(
                Component.translatable("power_radar.display_source.speed_unit.meters_per_second"),
                Component.translatable("power_radar.display_source.speed_unit.kilometers_per_hour")
        )), SPEED_UNIT_KEY);
    }

    @Override
    public void transferData(DisplayLinkContext context, DisplayTarget activeTarget, int line) {
        Output output = output(context);
        if (output == null) {
            // Исчезнувшая или снятая цель не должна стирать последнее значение на табло.
            return;
        }

        CompoundTag config = context.sourceConfig();
        String deliveryFingerprint = context.getTargetPos().asLong() + ":" + line + ":" + output.fingerprint();
        if (deliveryFingerprint.equals(config.getString(LAST_VALUE_KEY))) {
            return;
        }

        super.transferData(context, activeTarget, line);
        config.putString(LAST_VALUE_KEY, deliveryFingerprint);
    }

    @Override
    public int getPassiveRefreshTicks() {
        return RadarConstants.RADAR_DISPLAY_LINK_REFRESH_INTERVAL_TICKS;
    }

    private Output output(DisplayLinkContext context) {
        TrackedTargetView target = selectedTarget(context);
        if (target == null) {
            if (this.field == Field.SPEED && context.level() instanceof ServerLevel) {
                resetSpeedSample(context.sourceConfig());
            }
            return null;
        }
        return switch (this.field) {
            case COORDINATES -> coordinates(target.position());
            case TYPE -> type(target.classification());
            case SPEED -> speed(context, target);
        };
    }

    private static TrackedTargetView selectedTarget(DisplayLinkContext context) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof AbstractRadarMonitorBlockEntity monitor)
                || !(context.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID networkId = monitor.displayLinkNetworkId(serverLevel);
        if (networkId == null) {
            return null;
        }
        SelectedTargetRuntimeSnapshot snapshot = RadarNetworkManager.get(serverLevel.getServer())
                .selectedTargetSnapshot(networkId);
        return snapshot.alive() ? snapshot.target() : null;
    }

    private static Output coordinates(Vec3 position) {
        int x = Mth.floor(position.x);
        int y = Mth.floor(position.y);
        int z = Mth.floor(position.z);
        return new Output(
                Component.translatable("display.power_radar.selected_target_coordinates", x, y, z),
                x + ":" + y + ":" + z);
    }

    private static Output type(TargetClassification classification) {
        MutableComponent category = Component.translatable(categoryTranslationKey(classification));
        return new Output(category, classification.name());
    }

    private static String categoryTranslationKey(TargetClassification classification) {
        return switch (classification) {
            case PLAYER -> "message.power_radar.monitor.category.player";
            case PASSIVE_MOB -> "message.power_radar.monitor.category.passive_mob";
            case HOSTILE_MOB -> "message.power_radar.monitor.category.hostile_mob";
            case PROJECTILE -> "message.power_radar.monitor.category.projectile";
            case STRUCTURE -> "message.power_radar.monitor.category.sable_structure";
            case UNKNOWN -> "message.power_radar.monitor.category.unknown";
        };
    }

    private static Output speed(DisplayLinkContext context, TrackedTargetView target) {
        CompoundTag config = context.sourceConfig();
        UUID targetUuid = target.targetUuid();
        if (targetUuid == null) {
            resetSpeedSample(config);
            return null;
        }

        long sampleTime = context.level().getGameTime();
        if (config.hasUUID(SPEED_SAMPLE_TARGET_KEY)
                && targetUuid.equals(config.getUUID(SPEED_SAMPLE_TARGET_KEY))
                && config.getLong(SPEED_SAMPLE_TIME_KEY) == sampleTime
                && config.contains(SPEED_SAMPLE_VALUE_KEY)) {
            return speedOutput(context, config.getDouble(SPEED_SAMPLE_VALUE_KEY));
        }

        Vec3 position = target.position();
        if (!config.hasUUID(SPEED_SAMPLE_TARGET_KEY)
                || !targetUuid.equals(config.getUUID(SPEED_SAMPLE_TARGET_KEY))
                || !config.contains(SPEED_SAMPLE_TIME_KEY)) {
            storeSpeedSample(config, targetUuid, position, sampleTime);
            config.putDouble(SPEED_SAMPLE_VALUE_KEY, ZERO_SPEED_VALUE);
            return speedOutput(context, ZERO_SPEED_VALUE);
        }

        long previousTime = config.getLong(SPEED_SAMPLE_TIME_KEY);
        long elapsedTicks = sampleTime - previousTime;
        if (elapsedTicks <= 0L) {
            storeSpeedSample(config, targetUuid, position, sampleTime);
            config.putDouble(SPEED_SAMPLE_VALUE_KEY, ZERO_SPEED_VALUE);
            return speedOutput(context, ZERO_SPEED_VALUE);
        }

        Vec3 previousPosition = new Vec3(
                config.getDouble(SPEED_SAMPLE_X_KEY),
                config.getDouble(SPEED_SAMPLE_Y_KEY),
                config.getDouble(SPEED_SAMPLE_Z_KEY));
        double metersPerSecond = position.distanceTo(previousPosition) * 20.0D / elapsedTicks;
        storeSpeedSample(config, targetUuid, position, sampleTime);
        config.putDouble(SPEED_SAMPLE_VALUE_KEY, metersPerSecond);
        return speedOutput(context, metersPerSecond);
    }

    private static Output speedOutput(DisplayLinkContext context, double metersPerSecond) {
        boolean kilometersPerHour = context.sourceConfig().getInt(SPEED_UNIT_KEY) == 1;
        double displayedSpeed = kilometersPerHour ? metersPerSecond * 3.6D : metersPerSecond;
        String formattedSpeed = String.format(Locale.ROOT, "%.1f", displayedSpeed);
        String translationKey = kilometersPerHour
                ? "display.power_radar.selected_target_speed.kilometers_per_hour"
                : "display.power_radar.selected_target_speed.meters_per_second";
        return new Output(
                Component.translatable(translationKey, formattedSpeed),
                (kilometersPerHour ? "kmh:" : "mps:") + formattedSpeed);
    }

    private static void storeSpeedSample(
            CompoundTag config,
            UUID targetUuid,
            Vec3 position,
            long sampleTime
    ) {
        config.putUUID(SPEED_SAMPLE_TARGET_KEY, targetUuid);
        config.putLong(SPEED_SAMPLE_TIME_KEY, sampleTime);
        config.putDouble(SPEED_SAMPLE_X_KEY, position.x);
        config.putDouble(SPEED_SAMPLE_Y_KEY, position.y);
        config.putDouble(SPEED_SAMPLE_Z_KEY, position.z);
        config.remove(SPEED_SAMPLE_VALUE_KEY);
    }

    private static void resetSpeedSample(CompoundTag config) {
        config.remove(SPEED_SAMPLE_TARGET_KEY);
        config.remove(SPEED_SAMPLE_TIME_KEY);
        config.remove(SPEED_SAMPLE_X_KEY);
        config.remove(SPEED_SAMPLE_Y_KEY);
        config.remove(SPEED_SAMPLE_Z_KEY);
        config.remove(SPEED_SAMPLE_VALUE_KEY);
    }

    public enum Field {
        COORDINATES,
        TYPE,
        SPEED
    }

    private record Output(MutableComponent text, String fingerprint) {
    }
}
