package com.limbo2136.powerradar.radar.network;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;

public class RadarNetworkChunkLoadState {
    private final Set<GlobalPos> activeConsumerLeaseLinks = new HashSet<>();
    private final Set<ChunkPos> appliedChunks = new HashSet<>();
    private GlobalPos radarLinkPos;

    public Set<GlobalPos> activeConsumerLeaseLinks() {
        return this.activeConsumerLeaseLinks;
    }

    public Optional<GlobalPos> radarLinkPos() {
        return Optional.ofNullable(this.radarLinkPos);
    }

    public void setRadarLinkPos(GlobalPos radarLinkPos) {
        this.radarLinkPos = radarLinkPos;
    }

    public Set<ChunkPos> appliedChunks() {
        return this.appliedChunks;
    }

    public boolean ticketsApplied() {
        return !this.appliedChunks.isEmpty();
    }

    public void clearAppliedTickets() {
        this.appliedChunks.clear();
        this.radarLinkPos = null;
    }
}
