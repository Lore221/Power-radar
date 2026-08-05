package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.radar.RadarId;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Авторитетный runtime-снимок одной вручную выбранной цели в конкретной радарной сети.
 * Радар подтверждает доступность цели, а живая сущность обновляет её кинематику раз в тик.
 */
public record SelectedTargetRuntimeSnapshot(
        long revision,
        Status status,
        @Nullable UUID selectedTargetUuid,
        Set<RadarId> confirmingRadars,
        long serverGameTime,
        @Nullable TrackedTargetView target
) {
    public static final SelectedTargetRuntimeSnapshot EMPTY = new SelectedTargetRuntimeSnapshot(
            0L, Status.NO_SELECTION, null, Set.of(), 0L, null);

    public SelectedTargetRuntimeSnapshot {
        confirmingRadars = Set.copyOf(confirmingRadars);
    }

    public boolean confirmedByLatestScan() {
        return this.status == Status.TRACK_CONFIRMED
                || this.status == Status.LIVE
                || this.status == Status.ENTITY_UNAVAILABLE;
    }

    public boolean alive() {
        return this.status == Status.LIVE && this.target != null;
    }

    public enum Status {
        NO_SELECTION,
        WAITING_FOR_TRACK,
        TRACK_CONFIRMED,
        LIVE,
        ENTITY_UNAVAILABLE
    }

    /**
     * Неизменяемая копия измеренного или живого target view. Она не удерживает Entity
     * и поэтому безопасно переиспользуется несколькими потребителями в пределах тика.
     */
    public record TargetView(
            UUID targetUuid,
            int targetId,
            ResourceLocation entityTypeId,
            TargetSourceType sourceType,
            @Nullable String displayName,
            TargetClassification classification,
            ResourceLocation dimensionId,
            Vec3 position,
            Vec3 velocity,
            boolean hasVelocity,
            Vec3 acceleration,
            boolean hasAcceleration,
            long firstSeenGameTime,
            long lastSeenGameTime,
            long lastConfirmedAliveGameTime,
            double boundingHeight,
            double approximateSize
    ) implements TrackedTargetView {
        public static TargetView measured(TrackedTargetView source) {
            return copyWithLiveState(
                    source,
                    source.targetId(),
                    source.dimensionId(),
                    source.position(),
                    source.velocity(),
                    source.hasVelocity(),
                    source.lastSeenGameTime(),
                    source.lastConfirmedAliveGameTime(),
                    source.boundingHeight(),
                    source.approximateSize());
        }

        public static TargetView liveEntity(
                TrackedTargetView measured,
                int entityId,
                ResourceLocation dimensionId,
                Vec3 position,
                Vec3 velocity,
                long gameTime,
                double boundingHeight,
                double approximateSize
        ) {
            return copyWithLiveState(
                    measured,
                    entityId,
                    dimensionId,
                    position,
                    velocity,
                    true,
                    gameTime,
                    gameTime,
                    boundingHeight,
                    approximateSize);
        }

        public static TargetView liveStructure(
                TrackedTargetView measured,
                Vec3 position,
                Vec3 velocity,
                long gameTime,
                double boundingHeight
        ) {
            return copyWithLiveState(
                    measured,
                    measured.targetId(),
                    measured.dimensionId(),
                    position,
                    velocity,
                    true,
                    gameTime,
                    gameTime,
                    boundingHeight,
                    measured.approximateSize());
        }

        /**
         * Новый radar track обновляет классификацию и ускорение, но сохраняет уже
         * полученную в этом серверном тике живую кинематику.
         */
        public static TargetView rebaseLive(TrackedTargetView measured, TrackedTargetView live) {
            return copyWithLiveState(
                    measured,
                    live.targetId(),
                    live.dimensionId(),
                    live.position(),
                    live.velocity(),
                    live.hasVelocity(),
                    live.lastSeenGameTime(),
                    live.lastConfirmedAliveGameTime(),
                    live.boundingHeight(),
                    live.approximateSize());
        }

        public TargetView withVelocity(Vec3 velocity) {
            return copyWithLiveState(
                    this,
                    this.targetId,
                    this.dimensionId,
                    this.position,
                    velocity,
                    true,
                    this.lastSeenGameTime,
                    this.lastConfirmedAliveGameTime,
                    this.boundingHeight,
                    this.approximateSize);
        }

        private static TargetView copyWithLiveState(
                TrackedTargetView source,
                int targetId,
                ResourceLocation dimensionId,
                Vec3 position,
                Vec3 velocity,
                boolean hasVelocity,
                long lastSeenGameTime,
                long lastConfirmedAliveGameTime,
                double boundingHeight,
                double approximateSize
        ) {
            UUID targetUuid = source.targetUuid();
            if (targetUuid == null) {
                throw new IllegalArgumentException("Selected target view requires a UUID");
            }
            return new TargetView(
                    targetUuid,
                    targetId,
                    source.entityTypeId(),
                    source.sourceType(),
                    source.displayName(),
                    source.classification(),
                    dimensionId,
                    position,
                    velocity,
                    hasVelocity,
                    source.acceleration(),
                    source.hasAcceleration(),
                    source.firstSeenGameTime(),
                    lastSeenGameTime,
                    lastConfirmedAliveGameTime,
                    Math.max(0.1D, boundingHeight),
                    Math.max(0.1D, approximateSize));
        }
    }
}
