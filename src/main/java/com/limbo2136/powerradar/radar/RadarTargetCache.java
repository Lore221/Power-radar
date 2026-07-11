package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class RadarTargetCache {
    private final LinkedHashMap<TargetKey, RadarTargetTrack> tracks = new LinkedHashMap<>();
    private final EnumMap<TargetSourceType, LinkedHashMap<TargetKey, RadarTargetTrack>> tracksBySource =
            new EnumMap<>(TargetSourceType.class);

    public RadarTargetTrack get(TargetKey key) {
        return this.tracks.get(key);
    }

    public boolean contains(TargetKey key) {
        return this.tracks.containsKey(key);
    }

    public void put(TargetKey key, RadarTargetTrack track) {
        RadarTargetTrack previous = this.tracks.put(key, track);
        if (previous != null) {
            removeFromSourceIndex(key, previous);
        }
        this.tracksBySource
                .computeIfAbsent(track.sourceType(), ignored -> new LinkedHashMap<>())
                .put(key, track);
    }

    public void forEachTrackBySource(TargetSourceType sourceType, Consumer<RadarTargetTrack> consumer) {
        Map<TargetKey, RadarTargetTrack> typedTracks = this.tracksBySource.get(sourceType);
        if (typedTracks == null) {
            return;
        }
        typedTracks.values().forEach(consumer);
    }

    public int size() {
        return this.tracks.size();
    }

    public void forEachTrack(Consumer<RadarTargetTrack> consumer) {
        this.tracks.values().forEach(consumer);
    }

    @Nullable
    public RadarTargetTrack findByUuid(UUID targetUuid) {
        for (RadarTargetTrack track : this.tracks.values()) {
            if (targetUuid.equals(track.targetUuid())) {
                return track;
            }
        }
        return null;
    }

    public RadarStaleValidationResult validateStaleTracks(ServerLevel level, long gameTime) {
        int staleValidated = 0;
        int removedDeadOrMissing = 0;
        int removedExpired = 0;
        Iterator<RadarTargetTrack> iterator = this.tracks.values().iterator();
        while (iterator.hasNext()) {
            RadarTargetTrack track = iterator.next();
            if (track.lastSeenGameTime() >= gameTime) {
                continue;
            }

            if (gameTime - track.lastSeenGameTime() > RadarConstants.staleTrackExpirationTicks()) {
                iterator.remove();
                removeFromSourceIndex(track.key(), track);
                removedExpired++;
                continue;
            }

            staleValidated++;
            Entity entity = resolveKnownEntity(level, track);
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                removeFromSourceIndex(track.key(), track);
                removedDeadOrMissing++;
                continue;
            }

            track.confirmAlive(gameTime);
        }
        return new RadarStaleValidationResult(staleValidated, removedDeadOrMissing, removedExpired);
    }

    public void clear() {
        this.tracks.clear();
        this.tracksBySource.clear();
    }

    public RadarTargetCache copy() {
        RadarTargetCache copy = new RadarTargetCache();
        for (Map.Entry<TargetKey, RadarTargetTrack> entry : this.tracks.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public void replaceWith(RadarTargetCache other) {
        this.tracks.clear();
        this.tracksBySource.clear();
        for (Map.Entry<TargetKey, RadarTargetTrack> entry : other.tracks.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    private void removeFromSourceIndex(TargetKey key, RadarTargetTrack track) {
        Map<TargetKey, RadarTargetTrack> typedTracks = this.tracksBySource.get(track.sourceType());
        if (typedTracks == null) {
            return;
        }
        typedTracks.remove(key);
        if (typedTracks.isEmpty()) {
            this.tracksBySource.remove(track.sourceType());
        }
    }

    @Nullable
    private static Entity resolveKnownEntity(ServerLevel level, RadarTargetTrack track) {
        if (!level.dimension().location().equals(track.dimensionId())) {
            return null;
        }
        if (track.targetUuid() != null) {
            return level.getEntity(track.targetUuid());
        }
        return level.getEntity(track.targetId());
    }

    public int countByDimension(ResourceLocation dimensionId) {
        int count = 0;
        for (RadarTargetTrack track : this.tracks.values()) {
            if (track.dimensionId().equals(dimensionId)) {
                count++;
            }
        }
        return count;
    }
}
