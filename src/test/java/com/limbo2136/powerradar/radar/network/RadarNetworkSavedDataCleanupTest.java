package com.limbo2136.powerradar.radar.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RadarNetworkSavedDataCleanupTest {
    @Test
    void removesNetworkWithoutPersistentLinks() {
        RadarNetworkSavedData data = new RadarNetworkSavedData();
        UUID networkId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        data.ensure(networkId);

        assertTrue(data.removeIfNoLinks(networkId));
        assertTrue(data.get(networkId).isEmpty());
    }

    @Test
    void preservesNetworkThatStillHasAPersistentLink() {
        RadarNetworkSavedData data = new RadarNetworkSavedData();
        UUID networkId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        data.ensure(networkId).linkNodes().add(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));

        assertFalse(data.removeIfNoLinks(networkId));
        assertTrue(data.get(networkId).isPresent());
    }
}
