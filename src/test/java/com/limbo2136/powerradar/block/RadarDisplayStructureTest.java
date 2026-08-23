package com.limbo2136.powerradar.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class RadarDisplayStructureTest {
    private static final BlockPos ORIGIN = new BlockPos(10, 20, 30);

    @Test
    void oneBlockWideDisplayKeepsBothSideFrames() {
        RadarDisplayStructure structure = rectangle(1, 3);

        assertEquals(RadarDisplayFrameShape.VERTICAL_BOTTOM, structure.frameShape(ORIGIN));
        assertEquals(RadarDisplayFrameShape.VERTICAL, structure.frameShape(ORIGIN.above()));
        assertEquals(RadarDisplayFrameShape.VERTICAL_TOP, structure.frameShape(ORIGIN.above(2)));
    }

    @Test
    void oneBlockHighDisplayKeepsTopAndBottomFrames() {
        RadarDisplayStructure structure = rectangle(3, 1);
        Direction right = RadarDisplayStructureResolver.right(Direction.NORTH);

        assertEquals(RadarDisplayFrameShape.HORIZONTAL_LEFT, structure.frameShape(ORIGIN));
        assertEquals(RadarDisplayFrameShape.HORIZONTAL, structure.frameShape(ORIGIN.relative(right)));
        assertEquals(RadarDisplayFrameShape.HORIZONTAL_RIGHT, structure.frameShape(ORIGIN.relative(right, 2)));
    }

    private static RadarDisplayStructure rectangle(int width, int height) {
        return new RadarDisplayStructure(
                ORIGIN,
                width,
                height,
                Direction.NORTH,
                Set.copyOf(RadarDisplayStructure.rectanglePositions(
                        ORIGIN, Direction.NORTH, width, height)));
    }
}
