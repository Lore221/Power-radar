package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarLinkConnectionResolver {
    // Кэш сглаживает частые опросы соседей, но не заменяет серверную проверку блока и направления.
    private static final int DEFAULT_CACHE_TTL_TICKS = 5;
    private static final int CACHE_PRUNE_INTERVAL_TICKS = 200;
    private static final int CACHE_RETENTION_TICKS = 200;
    private static final int CACHE_SIZE_PRUNE_THRESHOLD = 1_024;
    private static final Map<ServerLevel, LevelCache> CACHE = new WeakHashMap<>();

    private RadarLinkConnectionResolver() {
    }

    public static Resolution findSingleLinkFacingEndpoint(ServerLevel level, BlockPos endpointPos) {
        RadarLinkBlockEntity found = null;
        for (Direction direction : Direction.values()) {
            RadarLinkBlockEntity link = linkFacingEndpoint(level, endpointPos, direction);
            if (link == null) {
                continue;
            }
            if (found != null) {
                return Resolution.ambiguous();
            }
            found = link;
        }
        return found == null ? Resolution.none() : Resolution.single(found);
    }

    public static Resolution findSingleLinkFacingEndpointCached(ServerLevel level, BlockPos endpointPos) {
        return findSingleLinkFacingEndpointCached(level, endpointPos, DEFAULT_CACHE_TTL_TICKS);
    }

    public static Resolution findSingleLinkFacingEndpointCached(ServerLevel level, BlockPos endpointPos, int ttlTicks) {
        long gameTime = level.getGameTime();
        BlockPos immutablePos = endpointPos.immutable();
        LevelCache levelCache = CACHE.computeIfAbsent(level, ignored -> new LevelCache());
        levelCache.pruneIfDue(gameTime);
        CachedResolution cached = levelCache.entries.get(immutablePos);
        long age = cached == null ? Long.MAX_VALUE : gameTime - cached.gameTime;
        if (cached != null && age >= 0L && age <= Math.max(0, ttlTicks)) {
            Resolution live = cached.resolve(level, endpointPos);
            if (live != null) {
                return live;
            }
        }
        Resolution resolution = findSingleLinkFacingEndpoint(level, endpointPos);
        levelCache.entries.put(immutablePos, CachedResolution.from(gameTime, resolution));
        return resolution;
    }

    public static void stopServer(MinecraftServer server) {
        CACHE.keySet().removeIf(level -> level.getServer() == server);
    }

    private static RadarLinkBlockEntity linkFacingEndpoint(
            ServerLevel level,
            BlockPos endpointPos,
            Direction direction
    ) {
        BlockPos linkPos = endpointPos.relative(direction);
        return linkFacingEndpointAt(level, endpointPos, linkPos);
    }

    private static RadarLinkBlockEntity linkFacingEndpointAt(
            ServerLevel level,
            BlockPos endpointPos,
            BlockPos linkPos
    ) {
        BlockState state = level.getBlockState(linkPos);
        if (!state.hasProperty(RadarLinkBlock.FACING)
                || !state.is(com.limbo2136.powerradar.registry.ModBlocks.RADAR_LINK.get())
                || !linkPos.relative(state.getValue(RadarLinkBlock.FACING)).equals(endpointPos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(linkPos);
        return blockEntity instanceof RadarLinkBlockEntity link && link.networkId() != null ? link : null;
    }

    public record Resolution(Status status, RadarLinkBlockEntity link) {
        public static Resolution none() {
            return new Resolution(Status.NONE, null);
        }

        public static Resolution ambiguous() {
            return new Resolution(Status.AMBIGUOUS, null);
        }

        public static Resolution single(RadarLinkBlockEntity link) {
            return new Resolution(Status.SINGLE, link);
        }
    }

    public enum Status {
        NONE,
        SINGLE,
        AMBIGUOUS
    }

    private record CachedResolution(long gameTime, Status status, @Nullable BlockPos linkPos) {
        private static CachedResolution from(long gameTime, Resolution resolution) {
            BlockPos linkPos = resolution.link() == null ? null : resolution.link().getBlockPos().immutable();
            return new CachedResolution(gameTime, resolution.status(), linkPos);
        }

        @Nullable
        private Resolution resolve(ServerLevel level, BlockPos endpointPos) {
            if (this.status != Status.SINGLE) {
                return new Resolution(this.status, null);
            }
            RadarLinkBlockEntity link = this.linkPos == null
                    ? null
                    : linkFacingEndpointAt(level, endpointPos, this.linkPos);
            return link == null ? null : Resolution.single(link);
        }
    }

    private static final class LevelCache {
        private final Map<BlockPos, CachedResolution> entries = new HashMap<>();
        private long lastPruneGameTime = Long.MIN_VALUE;

        private void pruneIfDue(long gameTime) {
            if (this.lastPruneGameTime != Long.MIN_VALUE && gameTime < this.lastPruneGameTime) {
                this.entries.clear();
                this.lastPruneGameTime = gameTime;
                return;
            }
            boolean intervalElapsed = this.lastPruneGameTime == Long.MIN_VALUE
                    || gameTime - this.lastPruneGameTime >= CACHE_PRUNE_INTERVAL_TICKS;
            if (!intervalElapsed && this.entries.size() < CACHE_SIZE_PRUNE_THRESHOLD) {
                return;
            }
            this.entries.entrySet().removeIf(entry ->
                    gameTime - entry.getValue().gameTime > CACHE_RETENTION_TICKS);
            this.lastPruneGameTime = gameTime;
        }
    }
}
