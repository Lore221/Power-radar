package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Управляет несериализуемыми chunk tickets, удерживаемыми потребителями радарной сети. */
final class RadarNetworkChunkTicketManager {
    private static final TicketType<UUID> RADAR_LINK_TICKET =
            TicketType.create("power_radar:radar_link", UUID::compareTo);
    private static final int TICKET_DISTANCE = 2;

    private final MinecraftServer server;

    RadarNetworkChunkTicketManager(MinecraftServer server) {
        this.server = server;
    }

    void apply(UUID networkId, RadarNetworkRuntime runtime, GlobalPos radarLinkPos) {
        RadarNetworkChunkLoadState state = runtime.chunkLoadState();
        ServerLevel level = this.server.getLevel(radarLinkPos.dimension());
        if (level != null && RadarWorldPoseResolver.isOnSableStructure(level, radarLinkPos.pos())) {
            remove(networkId, runtime);
            return;
        }
        if (state.radarLinkPos().filter(radarLinkPos::equals).isPresent() && state.ticketsApplied()) {
            return;
        }

        remove(networkId, runtime);
        if (level == null || state.activeConsumerLeaseLinks().isEmpty()) {
            return;
        }
        ChunkPos center = new ChunkPos(radarLinkPos.pos());
        int radius = RadarConstants.radarLinkForceLoadRadiusChunks();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                if (state.appliedChunks().add(chunkPos)) {
                    level.getChunkSource().addRegionTicket(
                            RADAR_LINK_TICKET, chunkPos, TICKET_DISTANCE, networkId, true);
                }
            }
        }
        state.setRadarLinkPos(radarLinkPos);
    }

    void remove(UUID networkId, RadarNetworkRuntime runtime) {
        RadarNetworkChunkLoadState state = runtime.chunkLoadState();
        if (state.radarLinkPos().isEmpty() || !state.ticketsApplied()) {
            return;
        }
        ServerLevel level = this.server.getLevel(state.radarLinkPos().orElseThrow().dimension());
        if (level != null) {
            for (ChunkPos chunkPos : state.appliedChunks()) {
                level.getChunkSource().removeRegionTicket(
                        RADAR_LINK_TICKET, chunkPos, TICKET_DISTANCE, networkId, true);
            }
        }
        state.clearAppliedTickets();
    }
}
