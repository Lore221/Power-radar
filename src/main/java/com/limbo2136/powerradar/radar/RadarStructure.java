package com.limbo2136.powerradar.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record RadarStructure(
        boolean assembled,
        BlockPos controllerPos,
        BlockPos processorPos,
        BlockPos corePos,
        Direction facing,
        int phasedArrayPanelCount,
        int overviewModuleCount,
        RadarStructureType structureType,
        RadarOrientationState orientationState
) {
    public RadarStructure(
            boolean assembled,
            BlockPos controllerPos,
            BlockPos processorPos,
            BlockPos corePos,
            Direction facing,
            int phasedArrayPanelCount,
            int overviewModuleCount
    ) {
        this(assembled, controllerPos, processorPos, corePos, facing, phasedArrayPanelCount, overviewModuleCount,
                RadarStructureType.PHASED_ARRAY, RadarOrientationState.fixed(com.limbo2136.powerradar.radar.RadarGeometry.yawDegrees(facing), 0L));
    }

    public int validPanelCount() {
        return this.phasedArrayPanelCount + this.overviewModuleCount;
    }

    public static RadarStructure aircraft(BlockPos controllerPos, Direction facing) {
        // The controller is the rear block and the sensing aperture is at the
        // front of the three-block body. Keep scanning anchored to that front
        // block; the rear block remains only the electrical/controller core.
        BlockPos scanOrigin = controllerPos.relative(facing, 2);
        return new RadarStructure(
                true,
                controllerPos,
                controllerPos,
                scanOrigin,
                facing,
                1,
                0,
                RadarStructureType.AIRCRAFT,
                RadarOrientationState.fixed(
                        RadarStructureType.AIRCRAFT,
                        RadarGeometry.yawDegrees(facing),
                        0L));
    }

    public static RadarStructure invalid(BlockPos controllerPos) {
        return new RadarStructure(false, controllerPos, controllerPos.above(), controllerPos.above(2), Direction.NORTH, 0, 0);
    }
}
