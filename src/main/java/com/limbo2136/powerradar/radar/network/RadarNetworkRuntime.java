package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;

/** Загружаемое состояние сети: ссылки, leases, ревизии и производные снимки без NBT-владения. */
public class RadarNetworkRuntime {
    private final Set<GlobalPos> loadedLinks = new HashSet<>();
    private final Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos = new HashMap<>();
    private final RadarNetworkChunkLoadState chunkLoadState = new RadarNetworkChunkLoadState();
    private UUID selectedTargetUuid;
    private DisplaySnapshotCacheEntry displaySnapshot;
    private long settingsRevision;
    private long selectedTargetRevision;
    private long selectedTargetScanFingerprint = Long.MIN_VALUE;
    private long selectedTargetLiveUpdateGameTime = Long.MIN_VALUE;
    private SelectedTargetTrackSelection selectedTargetTrack = SelectedTargetTrackSelection.EMPTY;
    private SelectedTargetRuntimeSnapshot selectedTargetSnapshot = SelectedTargetRuntimeSnapshot.EMPTY;

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
            resetSelectedTargetRuntime(selectedTargetUuid, 0L);
        }
    }

    public void loadPersistentSettings(UUID selectedTargetUuid) {
        this.selectedTargetUuid = selectedTargetUuid;
        this.settingsRevision++;
        invalidateDisplaySnapshots();
        resetSelectedTargetRuntime(selectedTargetUuid, 0L);
    }

    public long settingsRevision() {
        return this.settingsRevision;
    }

    public void markSettingsChanged() {
        // Одна ревизия инвалидирует и политику потребителей, и производный снимок монитора.
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

    public long selectedTargetScanFingerprint() {
        return this.selectedTargetScanFingerprint;
    }

    public SelectedTargetTrackSelection selectedTargetTrack() {
        return this.selectedTargetTrack;
    }

    public SelectedTargetRuntimeSnapshot selectedTargetSnapshot() {
        return this.selectedTargetSnapshot;
    }

    public boolean selectedTargetUpdatedAt(long gameTime) {
        return this.selectedTargetLiveUpdateGameTime == gameTime;
    }

    public void putSelectedTargetTrack(
            long scanFingerprint,
            UUID targetUuid,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView measuredTarget,
            Set<RadarId> confirmingRadars,
            long gameTime
    ) {
        this.selectedTargetScanFingerprint = scanFingerprint;
        if (measuredTarget == null || confirmingRadars.isEmpty()) {
            this.selectedTargetLiveUpdateGameTime = Long.MIN_VALUE;
            this.selectedTargetTrack = SelectedTargetTrackSelection.EMPTY;
            putSelectedTargetSnapshot(
                    SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                    targetUuid,
                    Set.of(),
                    gameTime,
                    null,
                    false);
            return;
        }
        this.selectedTargetTrack = new SelectedTargetTrackSelection(
                targetUuid, measuredTarget, Set.copyOf(confirmingRadars));

        // ServerTick.Post публикует radar track после тиков block entity. Если контроллер
        // уже прочитал Entity в этот тик, новый track не должен провоцировать второй запрос.
        if (this.selectedTargetLiveUpdateGameTime == gameTime
                && targetUuid.equals(this.selectedTargetSnapshot.selectedTargetUuid())
                && (this.selectedTargetSnapshot.status() == SelectedTargetRuntimeSnapshot.Status.LIVE
                        || this.selectedTargetSnapshot.status()
                                == SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE)) {
            SelectedTargetRuntimeSnapshot.TargetView liveTarget =
                    this.selectedTargetSnapshot.target() == null
                            ? null
                            : SelectedTargetRuntimeSnapshot.TargetView.rebaseLive(
                                    measuredTarget, this.selectedTargetSnapshot.target());
            putSelectedTargetSnapshot(
                    this.selectedTargetSnapshot.status(),
                    targetUuid,
                    confirmingRadars,
                    gameTime,
                    liveTarget,
                    false);
            return;
        }

        this.selectedTargetLiveUpdateGameTime = Long.MIN_VALUE;
        putSelectedTargetSnapshot(
                SelectedTargetRuntimeSnapshot.Status.TRACK_CONFIRMED,
                targetUuid,
                confirmingRadars,
                gameTime,
                null,
                false);
    }

    public void putLiveSelectedTarget(
            SelectedTargetRuntimeSnapshot.Status status,
            UUID targetUuid,
            Set<RadarId> confirmingRadars,
            long gameTime,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView target
    ) {
        if (status != SelectedTargetRuntimeSnapshot.Status.LIVE
                && this.selectedTargetSnapshot.status() == status
                && java.util.Objects.equals(this.selectedTargetSnapshot.selectedTargetUuid(), targetUuid)
                && this.selectedTargetSnapshot.confirmingRadars().equals(confirmingRadars)) {
            this.selectedTargetLiveUpdateGameTime = gameTime;
            return;
        }
        putSelectedTargetSnapshot(status, targetUuid, confirmingRadars, gameTime, target, true);
    }

    private void putSelectedTargetSnapshot(
            SelectedTargetRuntimeSnapshot.Status status,
            @Nullable UUID targetUuid,
            Set<RadarId> confirmingRadars,
            long gameTime,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView target,
            boolean liveUpdate
    ) {
        this.selectedTargetRevision++;
        if (liveUpdate) {
            this.selectedTargetLiveUpdateGameTime = gameTime;
        }
        this.selectedTargetSnapshot = new SelectedTargetRuntimeSnapshot(
                this.selectedTargetRevision,
                status,
                targetUuid,
                confirmingRadars,
                gameTime,
                target);
    }

    private void resetSelectedTargetRuntime(@Nullable UUID targetUuid, long gameTime) {
        this.selectedTargetScanFingerprint = Long.MIN_VALUE;
        this.selectedTargetLiveUpdateGameTime = Long.MIN_VALUE;
        this.selectedTargetTrack = SelectedTargetTrackSelection.EMPTY;
        putSelectedTargetSnapshot(
                targetUuid == null
                        ? SelectedTargetRuntimeSnapshot.Status.NO_SELECTION
                        : SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                targetUuid,
                Set.of(),
                gameTime,
                null,
                false);
    }

    public record DisplaySnapshotCacheEntry(long revision, RadarMonitorDisplayData data) {
    }

    public record SelectedTargetTrackSelection(
            @Nullable UUID targetUuid,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView measuredTarget,
            Set<RadarId> confirmingRadars
    ) {
        private static final SelectedTargetTrackSelection EMPTY =
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
