package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarLinkConnectionResolver {
    private static final int DEFAULT_CACHE_TTL_TICKS = 5;
    private static final Map<ServerLevel, Map<BlockPos, CachedResolution>> CACHE = new WeakHashMap<>();

    private RadarLinkConnectionResolver() {
    }

    public static List<RadarLinkBlockEntity> findLinksFacingEndpoint(ServerLevel level, BlockPos endpointPos) {
        ArrayList<RadarLinkBlockEntity> links = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos linkPos = endpointPos.relative(direction);
            BlockState state = level.getBlockState(linkPos);
            if (!state.hasProperty(RadarLinkBlock.FACING) || !state.is(com.limbo2136.powerradar.registry.ModBlocks.RADAR_LINK.get())) {
                continue;
            }
            if (!linkPos.relative(state.getValue(RadarLinkBlock.FACING)).equals(endpointPos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(linkPos);
            if (blockEntity instanceof RadarLinkBlockEntity link && link.networkId() != null) {
                links.add(link);
            }
        }
        return List.copyOf(links);
    }

    public static Resolution findSingleLinkFacingEndpoint(ServerLevel level, BlockPos endpointPos) {
        List<RadarLinkBlockEntity> links = findLinksFacingEndpoint(level, endpointPos);
        if (links.isEmpty()) {
            return Resolution.none();
        }
        if (links.size() > 1) {
            return Resolution.ambiguous();
        }
        return Resolution.single(links.get(0));
    }

    public static Resolution findSingleLinkFacingEndpointCached(ServerLevel level, BlockPos endpointPos) {
        return findSingleLinkFacingEndpointCached(level, endpointPos, DEFAULT_CACHE_TTL_TICKS);
    }

    public static Resolution findSingleLinkFacingEndpointCached(ServerLevel level, BlockPos endpointPos, int ttlTicks) {
        long gameTime = level.getGameTime();
        BlockPos immutablePos = endpointPos.immutable();
        Map<BlockPos, CachedResolution> levelCache = CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
        CachedResolution cached = levelCache.get(immutablePos);
        if (cached != null && gameTime - cached.gameTime <= Math.max(0, ttlTicks)) {
            return cached.resolution;
        }
        Resolution resolution = findSingleLinkFacingEndpoint(level, endpointPos);
        levelCache.put(immutablePos, new CachedResolution(gameTime, resolution));
        return resolution;
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

    private record CachedResolution(long gameTime, Resolution resolution) {
    }
}
