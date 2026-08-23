package com.limbo2136.powerradar.radar;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Накапливает Sable-структуры из всех пространственных срезов одного окна сканирования. */
public final class RadarSableCoverageAccumulator {
    private final Set<UUID> detectedStructures = new HashSet<>();

    public void reset() {
        this.detectedStructures.clear();
    }

    public void record(UUID structureUuid) {
        this.detectedStructures.add(structureUuid);
    }

    public Set<UUID> snapshot() {
        return Set.copyOf(this.detectedStructures);
    }
}
