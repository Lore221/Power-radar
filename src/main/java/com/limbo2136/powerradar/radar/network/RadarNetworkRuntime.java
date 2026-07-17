package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

public class RadarNetworkRuntime {
    private final Set<GlobalPos> loadedLinks = new HashSet<>();
    private final Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos = new HashMap<>();
    private final RadarNetworkChunkLoadState chunkLoadState = new RadarNetworkChunkLoadState();
    private UUID selectedTargetUuid;
    private DisplaySnapshotCacheEntry displaySnapshot;
    private long settingsRevision;

    public Set<GlobalPos> loadedLinks() {
        return this.loadedLinks;
    }

    public Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos() {
        return this.monitorLinkToMonitorPos;
    }

    public RadarNetworkChunkLoadState chunkLoadState() {
        return this.chunkLoadState;
    }

    public Optional<UUID> selectedTargetUuid() {
        return Optional.ofNullable(this.selectedTargetUuid);
    }

    public void setSelectedTargetUuid(UUID selectedTargetUuid) {
        if (!java.util.Objects.equals(this.selectedTargetUuid, selectedTargetUuid)) {
            this.selectedTargetUuid = selectedTargetUuid;
            this.settingsRevision++;
            invalidateDisplaySnapshots();
        }
    }

    public void loadPersistentSettings(UUID selectedTargetUuid) {
        this.selectedTargetUuid = selectedTargetUuid;
        this.settingsRevision++;
        invalidateDisplaySnapshots();
    }

    public long settingsRevision() {
        return this.settingsRevision;
    }

    public void markSettingsChanged() {
        this.settingsRevision++;
        invalidateDisplaySnapshots();
    }

    public DisplaySnapshotCacheEntry displaySnapshot() {
        return this.displaySnapshot;
    }

    public void putDisplaySnapshot(long revision, RadarMonitorDisplayData data) {
        this.displaySnapshot = new DisplaySnapshotCacheEntry(revision, data);
    }

    public void invalidateDisplaySnapshots() {
        this.displaySnapshot = null;
    }

    public record DisplaySnapshotCacheEntry(long revision, RadarMonitorDisplayData data) {
    }
}
