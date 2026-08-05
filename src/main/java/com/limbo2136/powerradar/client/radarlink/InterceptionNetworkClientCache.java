package com.limbo2136.powerradar.client.radarlink;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class InterceptionNetworkClientCache {
    private static final NetworkNodeClientIndex<BlockPos> INDEX = new NetworkNodeClientIndex<>();

    private InterceptionNetworkClientCache() {
    }

    public static void registerOrUpdate(ClientLevel level, BlockPos pos, @Nullable UUID networkId) {
        INDEX.registerOrUpdate(level.dimension(), pos.immutable(), networkId);
    }

    public static void unregister(ClientLevel level, BlockPos pos) {
        INDEX.unregister(level.dimension(), pos);
    }

    public static Set<BlockPos> getNodes(ClientLevel level, UUID networkId) {
        return INDEX.locations(level.dimension(), networkId);
    }

    public static void clear() {
        INDEX.clear();
    }
}
