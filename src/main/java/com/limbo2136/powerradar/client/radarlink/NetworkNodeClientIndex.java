package com.limbo2136.powerradar.client.radarlink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Неавторитетный индекс клиентских узлов с переиспользуемыми immutable-снимками для рендера. */
final class NetworkNodeClientIndex<T> {
    private final Map<ResourceKey<Level>, DimensionIndex<T>> dimensions = new HashMap<>();

    void registerOrUpdate(ResourceKey<Level> dimension, T location, @Nullable UUID networkId) {
        if (networkId == null) {
            unregister(dimension, location);
            return;
        }
        DimensionIndex<T> index = this.dimensions.computeIfAbsent(dimension, ignored -> new DimensionIndex<>());
        UUID previousNetworkId = index.networkByLocation.put(location, networkId);
        if (networkId.equals(previousNetworkId)) {
            return;
        }
        if (previousNetworkId == null) {
            index.knownLocationsSnapshot = null;
        }
        if (previousNetworkId != null) {
            index.removeFromNetwork(previousNetworkId, location);
        }
        index.locationsByNetwork.computeIfAbsent(networkId, ignored -> new HashSet<>()).add(location);
        index.snapshotsByNetwork.remove(networkId);
    }

    void unregister(ResourceKey<Level> dimension, T location) {
        DimensionIndex<T> index = this.dimensions.get(dimension);
        if (index == null) {
            return;
        }
        UUID networkId = index.networkByLocation.remove(location);
        if (networkId == null) {
            return;
        }
        index.knownLocationsSnapshot = null;
        index.removeFromNetwork(networkId, location);
        if (index.networkByLocation.isEmpty()) {
            this.dimensions.remove(dimension);
        }
    }

    Set<T> locations(ResourceKey<Level> dimension, UUID networkId) {
        DimensionIndex<T> index = this.dimensions.get(dimension);
        return index == null ? Set.of() : index.locations(networkId);
    }

    int size(ResourceKey<Level> dimension, UUID networkId) {
        DimensionIndex<T> index = this.dimensions.get(dimension);
        if (index == null) {
            return 0;
        }
        Set<T> locations = index.locationsByNetwork.get(networkId);
        return locations == null ? 0 : locations.size();
    }

    Set<T> knownLocations(ResourceKey<Level> dimension) {
        DimensionIndex<T> index = this.dimensions.get(dimension);
        return index == null ? Set.of() : index.knownLocations();
    }

    void clear() {
        this.dimensions.clear();
    }

    private static final class DimensionIndex<T> {
        private final Map<UUID, Set<T>> locationsByNetwork = new HashMap<>();
        private final Map<T, UUID> networkByLocation = new HashMap<>();
        private final Map<UUID, Set<T>> snapshotsByNetwork = new HashMap<>();
        @Nullable
        private Set<T> knownLocationsSnapshot;

        private Set<T> locations(UUID networkId) {
            Set<T> locations = this.locationsByNetwork.get(networkId);
            if (locations == null || locations.isEmpty()) {
                return Set.of();
            }
            return this.snapshotsByNetwork.computeIfAbsent(networkId, ignored -> Set.copyOf(locations));
        }

        private Set<T> knownLocations() {
            if (this.networkByLocation.isEmpty()) {
                return Set.of();
            }
            if (this.knownLocationsSnapshot == null) {
                this.knownLocationsSnapshot = Set.copyOf(this.networkByLocation.keySet());
            }
            return this.knownLocationsSnapshot;
        }

        private void removeFromNetwork(UUID networkId, T location) {
            Set<T> locations = this.locationsByNetwork.get(networkId);
            if (locations == null) {
                return;
            }
            locations.remove(location);
            this.snapshotsByNetwork.remove(networkId);
            if (locations.isEmpty()) {
                this.locationsByNetwork.remove(networkId);
            }
        }
    }
}
