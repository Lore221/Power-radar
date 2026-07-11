package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OverviewModuleBlockEntity extends BlockEntity {
    public OverviewModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVERVIEW_MODULE.get(), pos, state);
    }
}
