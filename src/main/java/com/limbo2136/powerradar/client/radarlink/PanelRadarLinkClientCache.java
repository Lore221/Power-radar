package com.limbo2136.powerradar.client.radarlink;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
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

/** Неавторитетный клиентский индекс панельных Link для подсветки радарной сети. */
public final class PanelRadarLinkClientCache {
    private static final Map<ResourceKey<Level>, Map<UUID, Set<LinkLocation>>> LINKS_BY_LEVEL =
            new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<LinkLocation, UUID>> NETWORK_BY_LOCATION =
            new HashMap<>();

    private PanelRadarLinkClientCache() {
    }

    public static void registerOrUpdate(
            ClientLevel level,
            BlockPos panelPos,
            ElectricalPanelSlot slot,
            @Nullable UUID networkId
    ) {
        LinkLocation location = new LinkLocation(panelPos.immutable(), slot);
        if (networkId == null) {
            unregister(level, location);
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        UUID oldId = NETWORK_BY_LOCATION
                .computeIfAbsent(dimension, ignored -> new HashMap<>())
                .put(location, networkId);
        if (oldId != null && !oldId.equals(networkId)) {
            removeFromNetwork(dimension, oldId, location);
        }
        LINKS_BY_LEVEL
                .computeIfAbsent(dimension, ignored -> new HashMap<>())
                .computeIfAbsent(networkId, ignored -> new HashSet<>())
                .add(location);
    }

    public static void unregister(ClientLevel level, LinkLocation location) {
        ResourceKey<Level> dimension = level.dimension();
        Map<LinkLocation, UUID> locations = NETWORK_BY_LOCATION.get(dimension);
        if (locations == null) {
            return;
        }
        UUID oldId = locations.remove(location);
        if (locations.isEmpty()) {
            NETWORK_BY_LOCATION.remove(dimension);
        }
        if (oldId != null) {
            removeFromNetwork(dimension, oldId, location);
        }
    }

    public static Set<LinkLocation> getLinks(ClientLevel level, UUID networkId) {
        Map<UUID, Set<LinkLocation>> linksByNetwork = LINKS_BY_LEVEL.get(level.dimension());
        if (linksByNetwork == null) {
            return Collections.emptySet();
        }
        Set<LinkLocation> links = linksByNetwork.get(networkId);
        return links == null || links.isEmpty() ? Collections.emptySet() : Set.copyOf(links);
    }

    public static void clear() {
        LINKS_BY_LEVEL.clear();
        NETWORK_BY_LOCATION.clear();
    }

    private static void removeFromNetwork(
            ResourceKey<Level> dimension,
            UUID networkId,
            LinkLocation location
    ) {
        Map<UUID, Set<LinkLocation>> linksByNetwork = LINKS_BY_LEVEL.get(dimension);
        if (linksByNetwork == null) {
            return;
        }
        Set<LinkLocation> links = linksByNetwork.get(networkId);
        if (links == null) {
            return;
        }
        links.remove(location);
        if (links.isEmpty()) {
            linksByNetwork.remove(networkId);
        }
        if (linksByNetwork.isEmpty()) {
            LINKS_BY_LEVEL.remove(dimension);
        }
    }

    public record LinkLocation(BlockPos panelPos, ElectricalPanelSlot slot) {
    }
}
