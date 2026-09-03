package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.AircraftRadarBlock;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class RadarNetworkTuning {
    private RadarNetworkTuning() {
    }

    static boolean isRadarSourceAt(Level level, BlockPos pos) {
        return resolveBlockEntityAt(level, pos) instanceof RadarControllerBlockEntity;
    }

    @Nullable
    static UUID ensureRadarSourceNetworkAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = resolveBlockEntityAt(level, pos);
        return blockEntity instanceof RadarControllerBlockEntity controller
                ? controller.ensureRadarNetworkId()
                : null;
    }

    @Nullable
    static BlockEntity resolveBlockEntityAt(Level level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct != null) {
            return direct;
        }
        return AircraftRadarBlock.findController(level, pos);
    }
}
