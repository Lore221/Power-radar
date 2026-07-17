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
    private static final Map<Key, RadarMonitorSilhouettePayload> SNAPSHOTS = new HashMap<>();
    private static final Map<Key, PendingRequest> PENDING_REQUESTS = new HashMap<>();
    @Nullable
    private static ClientLevel levelSession;
    private static long updateVersion;

    private SableSilhouetteClientCache() {
    }

    public static void requestMissing(BlockPos monitorPos, List<RadarDisplayTarget> targets) {
        ensureLevelSession();
        long nowMillis = net.minecraft.Util.getMillis();
        for (RadarDisplayTarget target : targets) {
            if (target.category() != RadarTargetCategory.SABLE_STRUCTURE
                    || target.targetUuid() == null
                    || target.silhouetteVersion() <= 0) {
                continue;
            }
            Key key = new Key(target.dimensionId(), target.targetUuid());
            RadarMonitorSilhouettePayload cached = SNAPSHOTS.get(key);
            int cachedVersion = cached == null ? 0 : cached.version();
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
        Key key = new Key(payload.dimensionId(), payload.structureUuid());
        RadarMonitorSilhouettePayload current = SNAPSHOTS.get(key);
        if (current == null || payload.version() >= current.version()) {
            SNAPSHOTS.put(key, payload);
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
        RadarMonitorSilhouettePayload snapshot = SNAPSHOTS.get(
                new Key(target.dimensionId(), target.targetUuid()));
        return snapshot != null && snapshot.version() >= target.silhouetteVersion() ? snapshot : null;
    }

    public static long updateVersion() {
        ensureLevelSession();
        return updateVersion;
    }

    private static void ensureLevelSession() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != levelSession) {
            SNAPSHOTS.clear();
            PENDING_REQUESTS.clear();
            updateVersion = 0L;
            levelSession = currentLevel;
        }
    }

    private record Key(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private record PendingRequest(int version, long sentAtMillis) {
    }
}
