package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.block.MechanicalSirenBlock;
import com.limbo2136.powerradar.client.MechanicalSirenSoundInstance;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MechanicalSirenBlockEntity extends KineticBlockEntity {
    @OnlyIn(Dist.CLIENT)
    private MechanicalSirenSoundInstance soundInstance;

    public MechanicalSirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_SIREN.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        float speed = Math.abs(getSpeed());
        int redstoneSignal = getBlockState().getValue(MechanicalSirenBlock.POWER);
        if (speed < 1.0F || redstoneSignal <= 0) {
            stopAudio();
            return;
        }
        if (this.soundInstance == null || this.soundInstance.isStopped()) {
            this.soundInstance = new MechanicalSirenSoundInstance(this.worldPosition);
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(this.soundInstance);
        }
        this.soundInstance.keepAlive(speed);
    }

    @OnlyIn(Dist.CLIENT)
    private void stopAudio() {
        if (this.soundInstance == null) {
            return;
        }
        this.soundInstance.fadeOut();
        this.soundInstance = null;
    }

    @Override
    public void remove() {
        if (this.level != null && this.level.isClientSide) {
            stopAudio();
        }
        super.remove();
    }
}
