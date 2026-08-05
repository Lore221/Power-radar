package com.limbo2136.powerradar.radar;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Ленивый общий кэш высоты поверхности для всех air/surface-проверок одного серверного тика. */
final class RadarSurfaceHeightCache {
    private final ServerLevel level;
    private Long2IntOpenHashMap heights;

    RadarSurfaceHeightCache(ServerLevel level) {
        this.level = level;
    }

    int height(int blockX, int blockZ) {
        if (this.heights == null) {
            this.heights = new Long2IntOpenHashMap();
            this.heights.defaultReturnValue(Integer.MIN_VALUE);
        }
        long key = ((long) blockX << 32) ^ (blockZ & 0xFFFF_FFFFL);
        int cached = this.heights.get(key);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        int height = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        this.heights.put(key, height);
        return height;
    }
}
