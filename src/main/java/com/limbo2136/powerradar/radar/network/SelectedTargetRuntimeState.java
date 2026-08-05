package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.radar.RadarId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** Хранит переходы и измерения выбранной цели, не относящиеся к топологии сети. */
final class SelectedTargetRuntimeState {
    @Nullable
    private UUID selectedTargetUuid;
    private long revision;
    private long scanFingerprint = Long.MIN_VALUE;
    private long liveUpdateGameTime = Long.MIN_VALUE;
    @Nullable
    private UUID motionTargetUuid;
    private Vec3 motionPosition = Vec3.ZERO;
    private long motionGameTime = Long.MIN_VALUE;
    private RadarNetworkRuntime.SelectedTargetTrackSelection track =
            RadarNetworkRuntime.SelectedTargetTrackSelection.EMPTY;
    private SelectedTargetRuntimeSnapshot snapshot = SelectedTargetRuntimeSnapshot.EMPTY;

    Optional<UUID> selectedTargetUuid() {
        return Optional.ofNullable(this.selectedTargetUuid);
    }

    boolean select(@Nullable UUID targetUuid) {
        if (Objects.equals(this.selectedTargetUuid, targetUuid)) {
            return false;
        }
        this.selectedTargetUuid = targetUuid;
        reset(targetUuid, 0L);
        return true;
    }

    void load(@Nullable UUID targetUuid) {
        this.selectedTargetUuid = targetUuid;
        reset(targetUuid, 0L);
    }

    long scanFingerprint() {
        return this.scanFingerprint;
    }

    RadarNetworkRuntime.SelectedTargetTrackSelection track() {
        return this.track;
    }

    SelectedTargetRuntimeSnapshot snapshot() {
        return this.snapshot;
    }

    boolean updatedAt(long gameTime) {
        return this.liveUpdateGameTime == gameTime;
    }

    void putTrack(
            long scanFingerprint,
            UUID targetUuid,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView measuredTarget,
            Set<RadarId> confirmingRadars,
            long gameTime
    ) {
        this.scanFingerprint = scanFingerprint;
        if (measuredTarget == null || confirmingRadars.isEmpty()) {
            this.liveUpdateGameTime = Long.MIN_VALUE;
            this.track = RadarNetworkRuntime.SelectedTargetTrackSelection.EMPTY;
            putSnapshot(
                    SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                    targetUuid,
                    Set.of(),
                    gameTime,
                    null,
                    false);
            return;
        }
        this.track = new RadarNetworkRuntime.SelectedTargetTrackSelection(
                targetUuid, measuredTarget, Set.copyOf(confirmingRadars));

        // ServerTick.Post публикует radar track после тиков block entity. Если контроллер
        // уже прочитал Entity в этот тик, новый track не должен провоцировать второй запрос.
        if (this.liveUpdateGameTime == gameTime
                && targetUuid.equals(this.snapshot.selectedTargetUuid())
                && (this.snapshot.status() == SelectedTargetRuntimeSnapshot.Status.LIVE
                        || this.snapshot.status() == SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE)) {
            SelectedTargetRuntimeSnapshot.TargetView liveTarget = this.snapshot.target() == null
                    ? null
                    : SelectedTargetRuntimeSnapshot.TargetView.rebaseLive(measuredTarget, this.snapshot.target());
            putSnapshot(
                    this.snapshot.status(),
                    targetUuid,
                    confirmingRadars,
                    gameTime,
                    liveTarget,
                    false);
            return;
        }

        this.liveUpdateGameTime = Long.MIN_VALUE;
        putSnapshot(
                SelectedTargetRuntimeSnapshot.Status.TRACK_CONFIRMED,
                targetUuid,
                confirmingRadars,
                gameTime,
                null,
                false);
    }

    void putLive(
            SelectedTargetRuntimeSnapshot.Status status,
            UUID targetUuid,
            Set<RadarId> confirmingRadars,
            long gameTime,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView target
    ) {
        if (status == SelectedTargetRuntimeSnapshot.Status.LIVE && target != null) {
            target = target.withVelocity(measuredVelocity(targetUuid, gameTime, target));
        } else {
            resetMotion();
        }
        if (status != SelectedTargetRuntimeSnapshot.Status.LIVE
                && this.snapshot.status() == status
                && Objects.equals(this.snapshot.selectedTargetUuid(), targetUuid)
                && this.snapshot.confirmingRadars().equals(confirmingRadars)) {
            this.liveUpdateGameTime = gameTime;
            return;
        }
        putSnapshot(status, targetUuid, confirmingRadars, gameTime, target, true);
    }

    private Vec3 measuredVelocity(
            UUID targetUuid,
            long gameTime,
            SelectedTargetRuntimeSnapshot.TargetView target
    ) {
        Vec3 velocity = target.velocity();
        if (targetUuid.equals(this.motionTargetUuid)
                && this.motionGameTime != Long.MIN_VALUE
                && gameTime > this.motionGameTime) {
            double elapsedTicks = gameTime - this.motionGameTime;
            velocity = target.position().subtract(this.motionPosition).scale(1.0D / elapsedTicks);
        }
        this.motionTargetUuid = targetUuid;
        this.motionPosition = target.position();
        this.motionGameTime = gameTime;
        return velocity;
    }

    private void resetMotion() {
        this.motionTargetUuid = null;
        this.motionPosition = Vec3.ZERO;
        this.motionGameTime = Long.MIN_VALUE;
    }

    private void putSnapshot(
            SelectedTargetRuntimeSnapshot.Status status,
            @Nullable UUID targetUuid,
            Set<RadarId> confirmingRadars,
            long gameTime,
            @Nullable SelectedTargetRuntimeSnapshot.TargetView target,
            boolean liveUpdate
    ) {
        this.revision++;
        if (liveUpdate) {
            this.liveUpdateGameTime = gameTime;
        }
        this.snapshot = new SelectedTargetRuntimeSnapshot(
                this.revision, status, targetUuid, confirmingRadars, gameTime, target);
    }

    private void reset(@Nullable UUID targetUuid, long gameTime) {
        this.scanFingerprint = Long.MIN_VALUE;
        this.liveUpdateGameTime = Long.MIN_VALUE;
        this.track = RadarNetworkRuntime.SelectedTargetTrackSelection.EMPTY;
        resetMotion();
        putSnapshot(
                targetUuid == null
                        ? SelectedTargetRuntimeSnapshot.Status.NO_SELECTION
                        : SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                targetUuid,
                Set.of(),
                gameTime,
                null,
                false);
    }
}
