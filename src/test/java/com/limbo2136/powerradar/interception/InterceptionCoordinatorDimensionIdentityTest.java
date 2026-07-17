package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class InterceptionCoordinatorDimensionIdentityTest {
    @Test
    void controllerKeysKeepSameCoordinatesInDifferentDimensionsSeparate() {
        BlockPos position = new BlockPos(24, 80, -48);
        InterceptionControllerKey overworld = InterceptionCoordinator.controllerKey(
                Level.OVERWORLD, null, position);
        InterceptionControllerKey nether = InterceptionCoordinator.controllerKey(
                Level.NETHER, null, position);

        Map<InterceptionControllerKey, String> controllerStates = new HashMap<>();
        controllerStates.put(overworld, "overworld");
        controllerStates.put(nether, "nether");

        assertNotEquals(overworld, nether);
        assertEquals(2, controllerStates.size());
        assertEquals("overworld", controllerStates.get(overworld));
        assertEquals("nether", controllerStates.get(nether));
    }

    @Test
    void controllerKeysKeepSameLocalCoordinatesOnDifferentStructuresSeparate() {
        BlockPos position = new BlockPos(8, 4, 12);
        InterceptionControllerKey first = InterceptionCoordinator.controllerKey(
                Level.OVERWORLD, UUID.fromString("00000000-0000-0000-0000-000000000001"), position);
        InterceptionControllerKey second = InterceptionCoordinator.controllerKey(
                Level.OVERWORLD, UUID.fromString("00000000-0000-0000-0000-000000000002"), position);

        Map<InterceptionControllerKey, String> controllerStates = new HashMap<>();
        controllerStates.put(first, "first");
        controllerStates.put(second, "second");

        assertNotEquals(first, second);
        assertEquals(2, controllerStates.size());
    }
}
