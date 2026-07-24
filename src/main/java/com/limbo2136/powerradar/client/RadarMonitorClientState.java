package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayTargetCache;
import com.limbo2136.powerradar.radar.RadarOrientationState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarMonitorClientState {
    // BlockPos достаточно только внутри одной ClientLevel-сессии; смена объекта уровня очищает всю карту.
    private static final Map<BlockPos, Entry> STATES = new HashMap<>();
    @Nullable
    private static ClientLevel levelSession;

    private RadarMonitorClientState() {
    }

    public static Entry applySnapshot(RadarMonitorSnapshotPayload snapshot) {
        ensureLevelSession();
        Entry entry = entry(snapshot.monitorPos());
        entry.apply(snapshot.displayData(), snapshot.revision());
        return entry;
    }

    public static Entry applyStatic(RadarMonitorSnapshotPayload snapshot) {
        ensureLevelSession();
        Entry entry = entry(snapshot.monitorPos());
        entry.applyStatic(snapshot.displayData(), snapshot.revision());
        return entry;
    }

    public static Entry applyTargets(RadarMonitorBlockTargetsPayload payload) {
        ensureLevelSession();
        Entry entry = entry(payload.monitorPos());
        entry.applyTargets(payload);
        return entry;
    }

    public static Entry applyPose(RadarMonitorBlockPosePayload payload) {
        ensureLevelSession();
        Entry entry = entry(payload.monitorPos());
        entry.applyPose(payload);
        return entry;
    }

    @Nullable
    public static Entry get(BlockPos monitorPos) {
        ensureLevelSession();
        return STATES.get(monitorPos);
    }

    @Nullable
    public static RadarMonitorDisplayData displayData(BlockPos monitorPos) {
        Entry entry = get(monitorPos);
        return entry == null ? null : entry.displayData();
    }

    private static Entry entry(BlockPos monitorPos) {
        return STATES.computeIfAbsent(monitorPos.immutable(), ignored -> new Entry());
    }

    private static void ensureLevelSession() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != levelSession) {
            // Сравнение объекта по ссылке защищает переподключение в то же измерение и координаты.
            STATES.clear();
            levelSession = currentLevel;
        }
    }

    private static long clientGameTime(long fallbackGameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? fallbackGameTime : minecraft.level.getGameTime();
    }

    private static double clientRenderTime(float partialTick, long fallbackGameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null
                ? fallbackGameTime + partialTick
                : minecraft.level.getGameTime() + partialTick;
    }

    public static final class Entry {
        private final RadarMonitorDisplayTargetCache targetCache = new RadarMonitorDisplayTargetCache();
        @Nullable
        private RadarMonitorDisplayData displayData;
        @Nullable
        private RadarMonitorDisplayData sourceDisplayData;
        private long revision = Long.MIN_VALUE;
        private long updateVersion;
        private long lastClientUpdateGameTime;
        private final Map<RadarId, PoseTransition> poseTransitions = new HashMap<>();
        @Nullable
        private MonitorPoseTransition monitorPoseTransition;
        @Nullable
        private RadarMonitorBlockPosePayload.MonitorPose lastMonitorPoseSample;
        private long lastMonitorPoseServerGameTime = Long.MIN_VALUE;
        @Nullable
        private VectorTransition monitorVelocityTransition;
        @Nullable
        private Vec3 lastMonitorVelocitySample;
        @Nullable
        private VectorTransition monitorAccelerationTransition;

        private Entry() {
        }

        private void apply(RadarMonitorDisplayData nextDisplayData, long nextRevision) {
            long gameTime = clientGameTime(nextDisplayData.serverGameTime());
            this.sourceDisplayData = nextDisplayData;
            this.displayData = this.targetCache.update(
                    withCurrentPoses(nextDisplayData), gameTime, this.poseTransitions.keySet());
            this.revision = nextRevision;
            this.lastClientUpdateGameTime = gameTime;
            this.updateVersion++;
        }

        private void applyTargets(RadarMonitorBlockTargetsPayload payload) {
            if (this.sourceDisplayData == null) {
                return;
            }
            RadarMonitorDisplayData dynamicData = this.sourceDisplayData.withTargets(
                    payload.targets(),
                    payload.lastScanGameTime(),
                    payload.serverGameTime());
            apply(dynamicData, payload.revision());
        }

        private void applyStatic(RadarMonitorDisplayData staticData, long nextRevision) {
            RadarMonitorDisplayData mergedData = staticData;
            if (this.sourceDisplayData != null && staticData.targets().isEmpty()) {
                // Пустой список static-пакета не удаляет цели до прихода парного targets-пакета.
                mergedData = staticData.withTargets(
                        this.sourceDisplayData.targets(),
                        this.sourceDisplayData.lastScanGameTime(),
                        this.sourceDisplayData.serverGameTime());
            }
            apply(mergedData, nextRevision);
        }

        private void applyPose(RadarMonitorBlockPosePayload payload) {
            Minecraft minecraft = Minecraft.getInstance();
            // Переход начинается с реально отрисованной позы в момент приёма, а не с границы tick.
            float receivePartialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            double receiveTime = clientRenderTime(receivePartialTick, payload.serverGameTime());
            if (payload.monitorPose() != null) {
                Vec3 nextVelocity = Vec3.ZERO;
                Vec3 nextAcceleration = Vec3.ZERO;
                boolean hasVelocitySample = false;
                long elapsedTicks = payload.serverGameTime() - this.lastMonitorPoseServerGameTime;
                if (this.lastMonitorPoseSample != null && elapsedTicks > 0L) {
                    RadarMonitorBlockPosePayload.MonitorPose last = this.lastMonitorPoseSample;
                    RadarMonitorBlockPosePayload.MonitorPose next = payload.monitorPose();
                    // Координатная разность за серверные тики переводится в блоки/с.
                    nextVelocity = new Vec3(
                            next.originX() - last.originX(),
                            next.originY() - last.originY(),
                            next.originZ() - last.originZ())
                            .scale(20.0D / elapsedTicks);
                    hasVelocitySample = true;
                    if (this.lastMonitorVelocitySample != null) {
                        nextAcceleration = nextVelocity.subtract(this.lastMonitorVelocitySample)
                                .scale(20.0D / elapsedTicks);
                    }
                }
                Vec3 previousVelocity = this.monitorVelocityTransition == null
                        ? nextVelocity
                        : sample(this.monitorVelocityTransition, receiveTime);
                this.monitorVelocityTransition = new VectorTransition(
                        previousVelocity, nextVelocity, receiveTime);
                if (hasVelocitySample) {
                    Vec3 previousAcceleration = this.monitorAccelerationTransition == null
                            ? nextAcceleration
                            : sample(this.monitorAccelerationTransition, receiveTime);
                    this.monitorAccelerationTransition = new VectorTransition(
                            previousAcceleration, nextAcceleration, receiveTime);
                    this.lastMonitorVelocitySample = nextVelocity;
                }
                this.lastMonitorPoseSample = payload.monitorPose();
                this.lastMonitorPoseServerGameTime = payload.serverGameTime();

                RadarMonitorBlockPosePayload.MonitorPose previous = this.monitorPoseTransition == null
                        ? payload.monitorPose()
                        : sample(this.monitorPoseTransition, receiveTime);
                this.monitorPoseTransition = new MonitorPoseTransition(
                        previous, payload.monitorPose(), receiveTime);
            }
            for (RadarMonitorBlockPosePayload.RadarPose next : payload.poses()) {
                PoseTransition old = this.poseTransitions.get(next.radarId());
                RadarMonitorBlockPosePayload.RadarPose previous = old == null
                        ? next
                        : sample(old, receiveTime);
                this.poseTransitions.put(next.radarId(), new PoseTransition(previous, next, receiveTime));
            }
            if (this.sourceDisplayData != null) {
                long gameTime = clientGameTime(payload.serverGameTime());
                this.displayData = this.targetCache.refilter(
                        withCurrentPoses(this.sourceDisplayData), gameTime);
                this.lastClientUpdateGameTime = gameTime;
                this.updateVersion++;
            }
        }

        private RadarMonitorDisplayData withCurrentPoses(RadarMonitorDisplayData data) {
            if (data.coverages().isEmpty() || this.poseTransitions.isEmpty()) {
                return data;
            }
            java.util.ArrayList<RadarDisplayCoverage> coverages = new java.util.ArrayList<>(data.coverages().size());
            for (RadarDisplayCoverage coverage : data.coverages()) {
                PoseTransition transition = this.poseTransitions.get(coverage.radarId());
                if (transition == null) {
                    coverages.add(coverage);
                    continue;
                }
                RadarMonitorBlockPosePayload.RadarPose pose = transition.current();
                coverages.add(new RadarDisplayCoverage(
                        coverage.radarId(), coverage.controllerPos(), coverage.dimensionId(),
                        pose.originX(), pose.originY(), pose.originZ(),
                        RadarOrientationState.fixed(
                                coverage.orientationState().structureType(), pose.yawDegrees(), 0L),
                        coverage.currentRange(), coverage.sectorAngle()));
            }
            return new RadarMonitorDisplayData(
                    data.monitorPos(), data.connectionStatus(), data.linked(), data.radarId(), data.controllerPos(),
                    data.radarDimensionId(), data.radarOriginX(), data.radarOriginY(), data.radarOriginZ(),
                    data.radarFacing(), data.monitorViewYawDegrees(), data.orientationState(), data.structureValid(),
                    data.active(), data.monitorElectricalState(), data.monitorVoltageVolts(), data.monitorResistanceOhms(),
                    data.monitorDisplayCount(), data.monitorScreenSize(), data.monitorRendererEnabled(), data.mode(),
                    data.detectionFilterMask(), data.autotargetFilterMask(), data.manualTargetUuid(),
                    data.onlinePlayerNames(), data.whitelistedPlayerNames(), data.whitelistedSableNames(),
                    data.validPanelCount(), data.currentRange(), data.maxRange(), data.sectorAngle(),
                    data.verticalScanHeight(), data.displayedTargetCount(), data.trackUpdateIntervalTicks(),
                    data.lastScanGameTime(), data.serverGameTime(), List.copyOf(coverages),
                    data.shellAlarmZones(), data.targets());
        }

        public RadarDisplayCoverage interpolatedCoverage(RadarDisplayCoverage coverage, float partialTick) {
            PoseTransition transition = this.poseTransitions.get(coverage.radarId());
            if (transition == null) {
                return coverage;
            }
            double renderTime = clientRenderTime(partialTick, 0L);
            double amount = interpolationAmount(transition, renderTime);
            RadarMonitorBlockPosePayload.RadarPose previous = transition.previous();
            RadarMonitorBlockPosePayload.RadarPose current = transition.current();
            float yawDelta = net.minecraft.util.Mth.wrapDegrees(current.yawDegrees() - previous.yawDegrees());
            float yaw = RadarGeometry.normalizeDegrees(previous.yawDegrees() + yawDelta * (float) amount);
            return new RadarDisplayCoverage(
                    coverage.radarId(), coverage.controllerPos(), coverage.dimensionId(),
                    lerp(previous.originX(), current.originX(), amount),
                    lerp(previous.originY(), current.originY(), amount),
                    lerp(previous.originZ(), current.originZ(), amount),
                    RadarOrientationState.fixed(coverage.orientationState().structureType(), yaw, 0L),
                    coverage.currentRange(), coverage.sectorAngle());
        }

        @Nullable
        public RadarMonitorBlockPosePayload.MonitorPose interpolatedMonitorPose(float partialTick) {
            if (this.monitorPoseTransition == null) {
                return null;
            }
            return sample(this.monitorPoseTransition, clientRenderTime(partialTick, 0L));
        }

        public Vec3 interpolatedMonitorVelocity(float partialTick) {
            if (this.monitorVelocityTransition == null) {
                return Vec3.ZERO;
            }
            return sample(this.monitorVelocityTransition, clientRenderTime(partialTick, 0L));
        }

        public Vec3 interpolatedMonitorAcceleration(float partialTick) {
            if (this.monitorAccelerationTransition == null) {
                return Vec3.ZERO;
            }
            return sample(this.monitorAccelerationTransition, clientRenderTime(partialTick, 0L));
        }

        public boolean hasMovingMonitorPose() {
            return this.monitorPoseTransition != null;
        }

        private static double lerp(double start, double end, double amount) {
            return start + (end - start) * amount;
        }

        private static double interpolationAmount(PoseTransition transition, double renderTime) {
            return Math.max(0.0D, Math.min(1.0D, renderTime - transition.receivedClientTime()));
        }

        private static RadarMonitorBlockPosePayload.RadarPose sample(
                PoseTransition transition,
                double renderTime
        ) {
            double amount = interpolationAmount(transition, renderTime);
            RadarMonitorBlockPosePayload.RadarPose previous = transition.previous();
            RadarMonitorBlockPosePayload.RadarPose current = transition.current();
            float yawDelta = net.minecraft.util.Mth.wrapDegrees(current.yawDegrees() - previous.yawDegrees());
            return new RadarMonitorBlockPosePayload.RadarPose(
                    current.radarId(),
                    lerp(previous.originX(), current.originX(), amount),
                    lerp(previous.originY(), current.originY(), amount),
                    lerp(previous.originZ(), current.originZ(), amount),
                    RadarGeometry.normalizeDegrees(previous.yawDegrees() + yawDelta * (float) amount));
        }

        private static RadarMonitorBlockPosePayload.MonitorPose sample(
                MonitorPoseTransition transition,
                double renderTime
        ) {
            double amount = Math.max(0.0D, Math.min(1.0D,
                    renderTime - transition.receivedClientTime()));
            RadarMonitorBlockPosePayload.MonitorPose previous = transition.previous();
            RadarMonitorBlockPosePayload.MonitorPose current = transition.current();
            float yawDelta = net.minecraft.util.Mth.wrapDegrees(current.yawDegrees() - previous.yawDegrees());
            return new RadarMonitorBlockPosePayload.MonitorPose(
                    lerp(previous.originX(), current.originX(), amount),
                    lerp(previous.originY(), current.originY(), amount),
                    lerp(previous.originZ(), current.originZ(), amount),
                    RadarGeometry.normalizeDegrees(previous.yawDegrees() + yawDelta * (float) amount));
        }

        private static Vec3 sample(VectorTransition transition, double renderTime) {
            double amount = Math.max(0.0D, Math.min(1.0D,
                    renderTime - transition.receivedClientTime()));
            return transition.previous().lerp(transition.current(), amount);
        }

        @Nullable
        public RadarMonitorDisplayData displayData() {
            return this.displayData;
        }

        public long revision() {
            return this.revision;
        }

        public long updateVersion() {
            return this.updateVersion;
        }

        public long lastClientUpdateGameTime() {
            return this.lastClientUpdateGameTime;
        }
    }

    private record PoseTransition(
            RadarMonitorBlockPosePayload.RadarPose previous,
            RadarMonitorBlockPosePayload.RadarPose current,
            double receivedClientTime
    ) {
    }

    private record MonitorPoseTransition(
            RadarMonitorBlockPosePayload.MonitorPose previous,
            RadarMonitorBlockPosePayload.MonitorPose current,
            double receivedClientTime
    ) {
    }

    private record VectorTransition(
            Vec3 previous,
            Vec3 current,
            double receivedClientTime
    ) {
    }
}
