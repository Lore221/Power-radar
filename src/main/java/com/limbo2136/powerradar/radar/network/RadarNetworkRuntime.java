package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;

/** Загружаемое состояние сети: ссылки, ревизии и производные снимки без NBT-владения. */
public class RadarNetworkRuntime {
    private final Set<GlobalPos> loadedLinks = new HashSet<>();
    private final Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos = new HashMap<>();
    private final SelectedTargetRuntimeState selectedTarget = new SelectedTargetRuntimeState();
    private final Map<Long, DisplaySnapshotCacheEntry> displaySnapshots = new HashMap<>();
    private long settingsRevision;

    public Set<GlobalPos> loadedLinks() {
        return this.loadedLinks;
    }

    public Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos() {
        return this.monitorLinkToMonitorPos;
    }

    public Optional<UUID> selectedTargetUuid() {
        return this.selectedTarget.selectedTargetUuid();
    }

    public void setSelectedTargetUuid(@Nullable UUID selectedTargetUuid) {
        if (this.selectedTarget.select(selectedTargetUuid)) {
            this.settingsRevision++;
            invalidateDisplaySnapshots();
        }
    }

    public void loadPersistentSettings(@Nullable UUID selectedTargetUuid) {
        this.selectedTarget.load(selectedTargetUuid);
        this.settingsRevision++;
        invalidateDisplaySnapshots();
    }

    public long settingsRevision() {
        return this.settingsRevision;
    }

    public void markSettingsChanged() {
        // Одна ревизия инвалидирует и политику потребителей, и производный снимок монитора.
        this.settingsRevision++;
        invalidateDisplaySnapshots();
    }

    @Nullable
    public DisplaySnapshotCacheEntry displaySnapshot(long revision) {
        return this.displaySnapshots.get(revision);
    }

    public void putDisplaySnapshot(long revision, RadarMonitorDisplayData data) {
        if (this.displaySnapshots.size() >= 32) {
            this.displaySnapshots.clear();
        }
        this.displaySnapshots.put(revision, new DisplaySnapshotCacheEntry(revision, data));
    }

    public void invalidateDisplaySnapshots() {
        this.displaySnapshots.clear();
    }

    public long selectedTargetScanFingerprint() {
        return this.selectedTarget.scanFingerprint();
    }

    public SelectedTargetTrackSelection selectedTargetTrack() {
        return this.selectedTarget.track();
    }

    public SelectedTargetRuntimeSnapshot selectedTargetSnapshot() {
        return this.selectedTarget.snapshot();
    }

    public boolean selectedTargetUpdatedAt(long gameTime) {
        return this.selectedTarget.updatedAt(gameTime);
    }

    public void putSelectedTargetTrack(
            long scanFingerprint,
            UUID targetUuid,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView measuredTarget,
            Set<RadarId> confirmingRadars,
            long gameTime
    ) {
        this.selectedTarget.putTrack(
                scanFingerprint, targetUuid, measuredTarget, confirmingRadars, gameTime);
    }

    public void putLiveSelectedTarget(
            SelectedTargetRuntimeSnapshot.Status status,
            UUID targetUuid,
            Set<RadarId> confirmingRadars,
            long gameTime,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView target
    ) {
        this.selectedTarget.putLive(status, targetUuid, confirmingRadars, gameTime, target);
    }

    public record DisplaySnapshotCacheEntry(long revision, RadarMonitorDisplayData data) {
    }

    public record SelectedTargetTrackSelection(
            @Nullable UUID targetUuid,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView measuredTarget,
            Set<RadarId> confirmingRadars
    ) {
        static final SelectedTargetTrackSelection EMPTY =
                new SelectedTargetTrackSelection(null, null, Set.of());

        public SelectedTargetTrackSelection {
            confirmingRadars = Set.copyOf(confirmingRadars);
        }

        public boolean confirmed() {
            return this.targetUuid != null
                    && this.measuredTarget != null
                    && !this.confirmingRadars.isEmpty();
        }
    }
}
