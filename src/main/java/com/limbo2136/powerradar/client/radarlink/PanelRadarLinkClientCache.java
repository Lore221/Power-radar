package com.limbo2136.powerradar.client.radarlink;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Неавторитетный клиентский индекс панельных Link для подсветки радарной сети. */
public final class PanelRadarLinkClientCache {
    private static final NetworkNodeClientIndex<LinkLocation> INDEX = new NetworkNodeClientIndex<>();

    private PanelRadarLinkClientCache() {
    }

    public static void registerOrUpdate(
            ClientLevel level,
            BlockPos panelPos,
            ElectricalPanelSlot slot,
            @Nullable UUID networkId
    ) {
        INDEX.registerOrUpdate(level.dimension(), new LinkLocation(panelPos.immutable(), slot), networkId);
    }

    public static void unregister(ClientLevel level, LinkLocation location) {
        INDEX.unregister(level.dimension(), location);
    }

    public static Set<LinkLocation> getLinks(ClientLevel level, UUID networkId) {
        return INDEX.locations(level.dimension(), networkId);
    }

    public static void clear() {
        INDEX.clear();
    }

    public record LinkLocation(BlockPos panelPos, ElectricalPanelSlot slot) {
    }
}
