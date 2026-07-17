package com.limbo2136.powerradar.client.radarlink;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class InterceptionNetworkClientCache {
    private static final Map<ResourceKey<Level>, Map<UUID, Set<BlockPos>>> NODES_BY_LEVEL = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, UUID>> NETWORK_BY_POS = new HashMap<>();

    private InterceptionNetworkClientCache() {
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
        NODES_BY_LEVEL
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

    public static Set<BlockPos> getNodes(ClientLevel level, UUID networkId) {
        Map<UUID, Set<BlockPos>> nodesByNetwork = NODES_BY_LEVEL.get(level.dimension());
        if (nodesByNetwork == null) {
            return Collections.emptySet();
        }
        Set<BlockPos> nodes = nodesByNetwork.get(networkId);
        return nodes == null || nodes.isEmpty() ? Collections.emptySet() : Set.copyOf(nodes);
    }

    public static void clear() {
        NODES_BY_LEVEL.clear();
        NETWORK_BY_POS.clear();
    }

    private static void removeFromNetwork(ResourceKey<Level> dimension, UUID networkId, BlockPos pos) {
        Map<UUID, Set<BlockPos>> nodesByNetwork = NODES_BY_LEVEL.get(dimension);
        if (nodesByNetwork == null) {
            return;
        }
        Set<BlockPos> nodes = nodesByNetwork.get(networkId);
        if (nodes == null) {
            return;
        }
        nodes.remove(pos);
        if (nodes.isEmpty()) {
            nodesByNetwork.remove(networkId);
        }
        if (nodesByNetwork.isEmpty()) {
            NODES_BY_LEVEL.remove(dimension);
        }
    }
}
