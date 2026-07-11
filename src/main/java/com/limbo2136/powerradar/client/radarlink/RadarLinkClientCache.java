package com.limbo2136.powerradar.client.radarlink;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.client.multiplayer.ClientLevel;

public final class RadarLinkClientCache {
    private static final Map<ResourceKey<Level>, Map<UUID, Set<BlockPos>>> LINKS_BY_LEVEL = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, UUID>> NETWORK_BY_POS = new HashMap<>();

    private RadarLinkClientCache() {
    }

    public static void registerOrUpdate(ClientLevel level, BlockPos pos, @Nullable UUID networkId) {
        if (networkId == null) {
            unregister(level, pos);
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        BlockPos immutablePos = pos.immutable();
        UUID oldId = NETWORK_BY_POS
                .computeIfAbsent(dimension, ignored -> new HashMap<>())
                .put(immutablePos, networkId);
        if (oldId != null && !oldId.equals(networkId)) {
            removeFromNetwork(dimension, oldId, immutablePos);
        }
        LINKS_BY_LEVEL
                .computeIfAbsent(dimension, ignored -> new HashMap<>())
                .computeIfAbsent(networkId, ignored -> new HashSet<>())
                .add(immutablePos);
    }

    public static void unregister(ClientLevel level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        BlockPos immutablePos = pos.immutable();
        Map<BlockPos, UUID> positions = NETWORK_BY_POS.get(dimension);
        if (positions == null) {
            return;
        }
        UUID oldId = positions.remove(immutablePos);
        if (positions.isEmpty()) {
            NETWORK_BY_POS.remove(dimension);
        }
        if (oldId != null) {
            removeFromNetwork(dimension, oldId, immutablePos);
        }
    }

    public static Set<BlockPos> getLinks(ClientLevel level, UUID networkId) {
        Map<UUID, Set<BlockPos>> linksByNetwork = LINKS_BY_LEVEL.get(level.dimension());
        if (linksByNetwork == null) {
            return Collections.emptySet();
        }
        Set<BlockPos> links = linksByNetwork.get(networkId);
        if (links == null || links.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(links);
    }

    public static int size(ClientLevel level, UUID networkId) {
        Map<UUID, Set<BlockPos>> linksByNetwork = LINKS_BY_LEVEL.get(level.dimension());
        if (linksByNetwork == null) {
            return 0;
        }
        Set<BlockPos> links = linksByNetwork.get(networkId);
        return links == null ? 0 : links.size();
    }

    public static void clear() {
        LINKS_BY_LEVEL.clear();
        NETWORK_BY_POS.clear();
    }

    private static void removeFromNetwork(ResourceKey<Level> dimension, UUID networkId, BlockPos pos) {
        Map<UUID, Set<BlockPos>> linksByNetwork = LINKS_BY_LEVEL.get(dimension);
        if (linksByNetwork == null) {
            return;
        }
        Set<BlockPos> links = linksByNetwork.get(networkId);
        if (links == null) {
            return;
        }
        links.remove(pos);
        if (links.isEmpty()) {
            linksByNetwork.remove(networkId);
        }
        if (linksByNetwork.isEmpty()) {
            LINKS_BY_LEVEL.remove(dimension);
        }
    }
}
