package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class InterceptionCoordinatorDimensionIdentityTest {
    @Test
    void controllerKeysKeepSameCoordinatesInDifferentDimensionsSeparate() {
        BlockPos position = new BlockPos(24, 80, -48);
        GlobalPos overworld = InterceptionCoordinator.controllerKey(Level.OVERWORLD, position);
        GlobalPos nether = InterceptionCoordinator.controllerKey(Level.NETHER, position);

        Map<GlobalPos, String> controllerStates = new HashMap<>();
        controllerStates.put(overworld, "overworld");
        controllerStates.put(nether, "nether");

        assertNotEquals(overworld, nether);
        assertEquals(2, controllerStates.size());
        assertEquals("overworld", controllerStates.get(overworld));
        assertEquals("nether", controllerStates.get(nether));
    }
}
