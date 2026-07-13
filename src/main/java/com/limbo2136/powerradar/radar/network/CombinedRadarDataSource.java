package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.api.radar.RadarCoverage;
import com.limbo2136.powerradar.api.radar.RadarDataSource;
import com.limbo2136.powerradar.api.radar.RadarTargetingDataSource;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.radar.RadarId;
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
        for (RadarDataSource source : this.sources) {
            if (source.assembled()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isElectricallyOperational() {
        for (RadarDataSource source : this.sources) {
            if (source.isElectricallyOperational()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int effectiveScanRangeBlocks() {
        int maximumRange = 0;
        boolean found = false;
        for (RadarDataSource source : this.sources) {
            int range = source.effectiveScanRangeBlocks();
            if (!found || range > maximumRange) {
                maximumRange = range;
                found = true;
            }
        }
        return maximumRange;
    }

    @Override
    public long lastScanGameTime() {
        long latestScanGameTime = 0L;
        boolean found = false;
        for (RadarDataSource source : this.sources) {
            long scanGameTime = source.lastScanGameTime();
            if (!found || scanGameTime > latestScanGameTime) {
                latestScanGameTime = scanGameTime;
                found = true;
            }
        }
        return latestScanGameTime;
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

    private RadarTargetingDataSource primary() {
        if (this.sources.isEmpty()) {
            throw new IllegalStateException("Combined radar source has no sources");
        }
        return this.sources.get(0);
    }
}
