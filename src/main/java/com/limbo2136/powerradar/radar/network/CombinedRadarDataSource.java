package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.api.radar.RadarCoverage;
import com.limbo2136.powerradar.api.radar.RadarDataSource;
import com.limbo2136.powerradar.api.radar.RadarTargetingDataSource;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.TargetTrajectoryMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class CombinedRadarDataSource implements RadarTargetingDataSource {
    private final List<? extends RadarTargetingDataSource> sources;

    public CombinedRadarDataSource(List<? extends RadarTargetingDataSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public boolean isEmpty() {
        return this.sources.isEmpty();
    }

    @Override
    public RadarId radarId() {
        return primary().radarId();
    }

    @Override
    public ResourceLocation dimensionId() {
        return primary().dimensionId();
    }

    @Override
    public boolean assembled() {
        return this.sources.stream().anyMatch(RadarDataSource::assembled);
    }

    @Override
    public boolean isElectricallyOperational() {
        return this.sources.stream().anyMatch(RadarDataSource::isElectricallyOperational);
    }

    @Override
    public int effectiveScanRangeBlocks() {
        return this.sources.stream()
                .mapToInt(RadarDataSource::effectiveScanRangeBlocks)
                .max()
                .orElse(0);
    }

    @Override
    public long lastScanGameTime() {
        return this.sources.stream()
                .mapToLong(RadarDataSource::lastScanGameTime)
                .max()
                .orElse(0L);
    }

    @Override
    public RadarCoverage coverage() {
        return primary().coverage();
    }

    @Override
    public void forEachTrackedTarget(Consumer<? super TrackedTargetView> consumer) {
        Set<UUID> seenUuids = new HashSet<>();
        for (RadarTargetingDataSource source : this.sources) {
            source.forEachTrackedTarget(track -> {
                UUID uuid = track.targetUuid();
                if (uuid != null && !seenUuids.add(uuid)) {
                    return;
                }
                consumer.accept(track);
            });
        }
    }

    @Override
    public void forEachTrackedTargetBySource(TargetSourceType sourceType, Consumer<? super TrackedTargetView> consumer) {
        Set<UUID> seenUuids = new HashSet<>();
        for (RadarTargetingDataSource source : this.sources) {
            source.forEachTrackedTargetBySource(sourceType, track -> {
                UUID uuid = track.targetUuid();
                if (uuid != null && !seenUuids.add(uuid)) {
                    return;
                }
                consumer.accept(track);
            });
        }
    }

    @Override
    @Nullable
    public TrackedTargetView findTrackedTarget(UUID targetUuid) {
        for (RadarTargetingDataSource source : this.sources) {
            TrackedTargetView track = source.findTrackedTarget(targetUuid);
            if (track != null) {
                return track;
            }
        }
        return null;
    }

    @Override
    public int trackedTargetCount() {
        final int[] count = {0};
        forEachTrackedTarget(ignored -> count[0]++);
        return count[0];
    }

    @Override
    public TargetTrajectoryMode targetTrajectoryMode() {
        return primary().targetTrajectoryMode();
    }

    private RadarTargetingDataSource primary() {
        if (this.sources.isEmpty()) {
            throw new IllegalStateException("Combined radar source has no sources");
        }
        return this.sources.get(0);
    }
}
