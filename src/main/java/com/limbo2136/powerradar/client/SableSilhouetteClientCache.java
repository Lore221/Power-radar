package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.compat.aeronautics.SableSilhouetteLimits;
import com.limbo2136.powerradar.network.RadarMonitorSilhouettePayload;
import com.limbo2136.powerradar.network.RadarMonitorSilhouetteRequestPayload;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class SableSilhouetteClientCache {
    private static final long REQUEST_RETRY_MILLIS = 1_000L;
    private static final long PRUNE_INTERVAL_MILLIS = 30_000L;
    private static final long SNAPSHOT_RETENTION_MILLIS = 300_000L;
    private static final long PENDING_RETENTION_MILLIS = 60_000L;
    private static final int MAX_CACHE_BYTES = 8 * 1024 * 1024;
    private static final Map<Key, CachedSnapshot> SNAPSHOTS = new HashMap<>();
    private static final Map<Key, PendingRequest> PENDING_REQUESTS = new HashMap<>();
    private static final Map<Key, SablePoseMotion> POSES = new HashMap<>();
    @Nullable
    private static ClientLevel levelSession;
    private static long updateVersion;
    private static long lastPruneMillis;
    private static int cachedBytes;

    private SableSilhouetteClientCache() {
    }

    public static void requestMissing(BlockPos monitorPos, List<RadarDisplayTarget> targets) {
        ensureLevelSession();
        long nowMillis = net.minecraft.Util.getMillis();
        pruneIfDue(nowMillis);
        for (RadarDisplayTarget target : targets) {
            updatePose(target, nowMillis);
            if (target.category() != RadarTargetCategory.SABLE_STRUCTURE
                    || target.targetUuid() == null
                    || target.silhouetteVersion() <= 0) {
                continue;
            }
            Key key = new Key(target.dimensionId(), target.targetUuid());
            CachedSnapshot cached = SNAPSHOTS.get(key);
            int cachedVersion = cached == null ? 0 : cached.payload.version();
            PendingRequest pending = PENDING_REQUESTS.get(key);
            if (cachedVersion >= target.silhouetteVersion()) {
                continue;
            }
            if (pending != null
                    && pending.version() >= target.silhouetteVersion()
                    && nowMillis - pending.sentAtMillis() < REQUEST_RETRY_MILLIS) {
                continue;
            }
            PENDING_REQUESTS.put(key, new PendingRequest(target.silhouetteVersion(), nowMillis));
            PacketDistributor.sendToServer(new RadarMonitorSilhouetteRequestPayload(
                    monitorPos, target.targetUuid(), cachedVersion));
        }
    }

    public static void apply(RadarMonitorSilhouettePayload payload) {
        ensureLevelSession();
        long nowMillis = net.minecraft.Util.getMillis();
        pruneIfDue(nowMillis);
        Key key = new Key(payload.dimensionId(), payload.structureUuid());
        CachedSnapshot current = SNAPSHOTS.get(key);
        if (current == null || payload.version() >= current.payload.version()) {
            if (current != null) {
                cachedBytes -= current.estimatedBytes;
            }
            CachedSnapshot replacement = new CachedSnapshot(payload, nowMillis);
            SNAPSHOTS.put(key, replacement);
            cachedBytes += replacement.estimatedBytes;
            enforceMemoryLimit();
            updateVersion++;
        }
        PendingRequest pending = PENDING_REQUESTS.get(key);
        if (pending != null && payload.version() >= pending.version()) {
            PENDING_REQUESTS.remove(key);
        }
    }

    @Nullable
    public static RadarMonitorSilhouettePayload get(RadarDisplayTarget target) {
        ensureLevelSession();
        if (target.targetUuid() == null || target.silhouetteVersion() <= 0) {
            return null;
        }
        CachedSnapshot snapshot = SNAPSHOTS.get(
                new Key(target.dimensionId(), target.targetUuid()));
        if (snapshot == null
                || snapshot.payload.version() < target.silhouetteVersion()
                || !snapshot.payload.status().drawable()) {
            return null;
        }
        snapshot.lastAccessMillis = net.minecraft.Util.getMillis();
        return snapshot.payload;
    }

    public static long updateVersion() {
        ensureLevelSession();
        return updateVersion;
    }

    public static float interpolatedHeading(RadarDisplayTarget target, float partialTick) {
        return interpolatedPose(target, partialTick).headingDegrees();
    }

    public static DisplayPose interpolatedPose(RadarDisplayTarget target, float partialTick) {
        ensureLevelSession();
        if (target.targetUuid() == null) {
            return new DisplayPose(target.x(), target.z(), target.structureHeadingDegrees());
        }
        SablePoseMotion motion = POSES.get(new Key(target.dimensionId(), target.targetUuid()));
        SablePoseInterpolator.Pose pose = motion == null
                ? poseOf(target)
                : motion.valueAt(net.minecraft.Util.getMillis());
        return new DisplayPose(pose.centerX(), pose.centerZ(), pose.headingDegrees());
    }

    private static void updatePose(RadarDisplayTarget target, long nowMillis) {
        if (target.category() != RadarTargetCategory.SABLE_STRUCTURE || target.targetUuid() == null) {
            return;
        }
        Key key = new Key(target.dimensionId(), target.targetUuid());
        SablePoseInterpolator.Pose targetPose = poseOf(target);
        SablePoseMotion previous = POSES.get(key);
        if (previous == null) {
            SablePoseMotion initial = SablePoseMotion.initial(targetPose, nowMillis);
            POSES.put(key, initial);
            logPoseSnapshot(target, targetPose, null, initial.valueAt(nowMillis));
            return;
        }
        SablePoseInterpolator.Pose previousRendered = previous.valueAt(nowMillis);
        SablePoseMotion updated = previous.update(targetPose, nowMillis);
        POSES.put(key, updated);
        logPoseSnapshot(target, targetPose, previousRendered, updated.valueAt(nowMillis));
    }

    private static void logPoseSnapshot(
            RadarDisplayTarget target,
            SablePoseInterpolator.Pose received,
            @Nullable SablePoseInterpolator.Pose previousRendered,
            SablePoseInterpolator.Pose nextRendered
    ) {
        if (!PowerRadarDebugOptions.sablePoseLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][SablePose][Client] structure={} received_center_x={} received_center_z={} received_heading={} received_offset_x={} received_offset_z={} received_pivot_x={} received_pivot_z={} previous_center_x={} previous_center_z={} previous_heading={} previous_pivot_x={} previous_pivot_z={} next_center_x={} next_center_z={} next_heading={} next_pivot_x={} next_pivot_z={}",
                target.targetUuid(), target.x(), target.z(), target.structureHeadingDegrees(),
                target.structureRotationPointOffsetX(), target.structureRotationPointOffsetZ(),
                received.worldRotationPointX(), received.worldRotationPointZ(),
                previousRendered == null ? Double.NaN : previousRendered.centerX(),
                previousRendered == null ? Double.NaN : previousRendered.centerZ(),
                previousRendered == null ? Float.NaN : previousRendered.headingDegrees(),
                previousRendered == null ? Double.NaN : previousRendered.worldRotationPointX(),
                previousRendered == null ? Double.NaN : previousRendered.worldRotationPointZ(),
                nextRendered.centerX(), nextRendered.centerZ(), nextRendered.headingDegrees(),
                nextRendered.worldRotationPointX(), nextRendered.worldRotationPointZ());
    }

    private static SablePoseInterpolator.Pose poseOf(RadarDisplayTarget target) {
        return SablePoseInterpolator.fromCenter(
                target.x(), target.z(), target.structureHeadingDegrees(),
                target.structureRotationPointOffsetX(), target.structureRotationPointOffsetZ());
    }

    private static void ensureLevelSession() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != levelSession) {
            // Сравнение объекта уровня по ссылке учитывает переподключение в то же измерение.
            SNAPSHOTS.clear();
            PENDING_REQUESTS.clear();
            POSES.clear();
            cachedBytes = 0;
            updateVersion = 0L;
            lastPruneMillis = net.minecraft.Util.getMillis();
            levelSession = currentLevel;
        }
    }

    private static void pruneIfDue(long nowMillis) {
        if (nowMillis - lastPruneMillis < PRUNE_INTERVAL_MILLIS) {
            return;
        }
        SNAPSHOTS.entrySet().removeIf(entry -> {
            boolean expired = nowMillis - entry.getValue().lastAccessMillis > SNAPSHOT_RETENTION_MILLIS;
            if (expired) {
                cachedBytes -= entry.getValue().estimatedBytes;
            }
            return expired;
        });
        PENDING_REQUESTS.entrySet().removeIf(entry ->
                nowMillis - entry.getValue().sentAtMillis() > PENDING_RETENTION_MILLIS);
        POSES.keySet().removeIf(key -> !SNAPSHOTS.containsKey(key) && !PENDING_REQUESTS.containsKey(key));
        lastPruneMillis = nowMillis;
    }

    private static void enforceMemoryLimit() {
        while (cachedBytes > MAX_CACHE_BYTES && !SNAPSHOTS.isEmpty()) {
            Map.Entry<Key, CachedSnapshot> oldest = SNAPSHOTS.entrySet().stream()
                    .min((left, right) -> Long.compare(
                            left.getValue().lastAccessMillis, right.getValue().lastAccessMillis))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            cachedBytes -= oldest.getValue().estimatedBytes;
            SNAPSHOTS.remove(oldest.getKey());
        }
    }

    private record Key(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private record PendingRequest(int version, long sentAtMillis) {
    }

    private static final class CachedSnapshot {
        private final RadarMonitorSilhouettePayload payload;
        private final int estimatedBytes;
        private long lastAccessMillis;

        private CachedSnapshot(RadarMonitorSilhouettePayload payload, long lastAccessMillis) {
            this.payload = payload;
            this.estimatedBytes = 64 + SableSilhouetteLimits.estimatedGeometryBytes(
                    payload.lines().size(), payload.fills().size());
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    public record DisplayPose(double centerX, double centerZ, float headingDegrees) {
    }

}
