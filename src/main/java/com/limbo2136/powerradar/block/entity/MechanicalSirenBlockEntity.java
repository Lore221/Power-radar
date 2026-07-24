package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.block.MechanicalSirenBlock;
import com.limbo2136.powerradar.bridge.MechanicalSirenClientAudioBridge;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalSirenBlockEntity extends KineticBlockEntity {
    public MechanicalSirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_SIREN.get(), pos, state);
    }

    @Override
    public void tickAudio() {
        super.tickAudio();
        if (this.level == null || !this.level.isClientSide) {
            return;
        }
        float speed = Math.abs(getSpeed());
        int redstoneSignal = getBlockState().getValue(MechanicalSirenBlock.POWER);
        MechanicalSirenClientAudioBridge.tick(this.level, this.worldPosition, speed, redstoneSignal > 0);
    }

    @Override
    public void remove() {
        MechanicalSirenClientAudioBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }
}
