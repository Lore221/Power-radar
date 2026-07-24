package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class RadarNetworkTuning {
    private RadarNetworkTuning() {
    }

    static boolean isSourceAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof RadarLinkBlockEntity
                || blockEntity instanceof ShellAlarmBlockEntity
                || blockEntity instanceof OnboardComputerBlockEntity;
    }

    @Nullable
    static UUID ensureNetworkAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RadarLinkBlockEntity link) {
            return link.ensureNetworkId();
        }
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            return alarm.ensureNetworkId();
        }
        if (blockEntity instanceof OnboardComputerBlockEntity computer) {
            return computer.ensureNetworkId();
        }
        return null;
    }
}
