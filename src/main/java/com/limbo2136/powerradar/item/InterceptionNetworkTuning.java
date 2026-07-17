package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class InterceptionNetworkTuning {
    private InterceptionNetworkTuning() {
    }

    static boolean isSourceAt(Level level, BlockPos pos) {
        Object blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ShellAlarmBlockEntity
                || blockEntity instanceof OnboardComputerBlockEntity;
    }

    @Nullable
    static UUID ensureNetworkAt(Level level, BlockPos pos) {
        Object blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            return alarm.ensureInterceptionNetworkId();
        }
        if (blockEntity instanceof OnboardComputerBlockEntity computer) {
            return computer.ensureInterceptionNetworkId();
        }
        return null;
    }
}
