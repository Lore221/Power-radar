package com.limbo2136.powerradar.api.radar;

import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.radar.RadarId;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public interface RadarDataSource {
    RadarId radarId();

    ResourceLocation dimensionId();

    boolean assembled();

    boolean isElectricallyOperational();

    int effectiveScanRangeBlocks();

    long lastScanGameTime();

    RadarCoverage coverage();

    void forEachTrackedTarget(Consumer<? super TrackedTargetView> consumer);

    default void forEachTrackedTargetBySource(TargetSourceType sourceType, Consumer<? super TrackedTargetView> consumer) {
        forEachTrackedTarget(track -> {
            if (track.sourceType() == sourceType) {
                consumer.accept(track);
            }
        });
    }

    @Nullable
    TrackedTargetView findTrackedTarget(UUID targetUuid);

    int trackedTargetCount();
}
