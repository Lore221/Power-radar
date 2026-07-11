package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayTargetCache;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarMonitorClientState {
    private static final Map<BlockPos, Entry> STATES = new HashMap<>();

    private RadarMonitorClientState() {
    }

    public static Entry applySnapshot(RadarMonitorSnapshotPayload snapshot) {
        Entry entry = entry(snapshot.monitorPos());
        entry.apply(snapshot.displayData(), snapshot.revision());
        return entry;
    }

    public static Entry applyTargets(RadarMonitorBlockTargetsPayload payload) {
        Entry entry = entry(payload.monitorPos());
        entry.applyTargets(payload);
        return entry;
    }

    @Nullable
    public static Entry get(BlockPos monitorPos) {
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

    private static long clientGameTime(long fallbackGameTime) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? fallbackGameTime : minecraft.level.getGameTime();
    }

    public static final class Entry {
        private final RadarMonitorDisplayTargetCache targetCache = new RadarMonitorDisplayTargetCache();
        @Nullable
        private RadarMonitorDisplayData displayData;
        private long revision = Long.MIN_VALUE;
        private long updateVersion;
        private long lastClientUpdateGameTime;

        private Entry() {
        }

        private void apply(RadarMonitorDisplayData nextDisplayData, long nextRevision) {
            long gameTime = clientGameTime(nextDisplayData.serverGameTime());
            this.displayData = this.targetCache.update(nextDisplayData, gameTime);
            this.revision = nextRevision;
            this.lastClientUpdateGameTime = gameTime;
            this.updateVersion++;
        }

        private void applyTargets(RadarMonitorBlockTargetsPayload payload) {
            if (this.displayData == null) {
                return;
            }
            RadarMonitorDisplayData dynamicData = this.displayData.withTargets(
                    payload.targets(),
                    payload.lastScanGameTime(),
                    payload.serverGameTime());
            apply(dynamicData, payload.revision());
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
}
