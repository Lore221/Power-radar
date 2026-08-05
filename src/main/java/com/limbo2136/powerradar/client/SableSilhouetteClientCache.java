package com.limbo2136.powerradar.client;

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
    private static final Map<Key, CachedSnapshot> SNAPSHOTS = new HashMap<>();
    private static final Map<Key, PendingRequest> PENDING_REQUESTS = new HashMap<>();
    @Nullable
    private static ClientLevel levelSession;
    private static long updateVersion;
    private static long lastPruneMillis;

    private SableSilhouetteClientCache() {
    }

    public static void requestMissing(BlockPos monitorPos, List<RadarDisplayTarget> targets) {
        ensureLevelSession();
        long nowMillis = net.minecraft.Util.getMillis();
        pruneIfDue(nowMillis);
        for (RadarDisplayTarget target : targets) {
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
            SNAPSHOTS.put(key, new CachedSnapshot(payload, nowMillis));
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
        if (snapshot == null || snapshot.payload.version() < target.silhouetteVersion()) {
            return null;
        }
        snapshot.lastAccessMillis = net.minecraft.Util.getMillis();
        return snapshot.payload;
    }

    public static long updateVersion() {
        ensureLevelSession();
        return updateVersion;
    }

    private static void ensureLevelSession() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != levelSession) {
            // Сравнение объекта уровня по ссылке учитывает переподключение в то же измерение.
            SNAPSHOTS.clear();
            PENDING_REQUESTS.clear();
            updateVersion = 0L;
            lastPruneMillis = net.minecraft.Util.getMillis();
            levelSession = currentLevel;
        }
    }

    private static void pruneIfDue(long nowMillis) {
        if (nowMillis - lastPruneMillis < PRUNE_INTERVAL_MILLIS) {
            return;
        }
        SNAPSHOTS.entrySet().removeIf(entry ->
                nowMillis - entry.getValue().lastAccessMillis > SNAPSHOT_RETENTION_MILLIS);
        PENDING_REQUESTS.entrySet().removeIf(entry ->
                nowMillis - entry.getValue().sentAtMillis() > PENDING_RETENTION_MILLIS);
        lastPruneMillis = nowMillis;
    }

    private record Key(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private record PendingRequest(int version, long sentAtMillis) {
    }

    private static final class CachedSnapshot {
        private final RadarMonitorSilhouettePayload payload;
        private long lastAccessMillis;

        private CachedSnapshot(RadarMonitorSilhouettePayload payload, long lastAccessMillis) {
            this.payload = payload;
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
