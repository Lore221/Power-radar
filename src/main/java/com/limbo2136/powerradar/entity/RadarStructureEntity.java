package com.limbo2136.powerradar.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class RadarStructureEntity extends Entity {
    private BlockPos controllerPos = BlockPos.ZERO;

    public RadarStructureEntity(EntityType<? extends RadarStructureEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvisible(true);
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    public void setControllerPos(BlockPos controllerPos) {
        this.controllerPos = controllerPos.immutable();
        moveTo(controllerPos.getX() + 0.5D, controllerPos.getY() + 0.5D, controllerPos.getZ() + 0.5D);
    }

    public boolean belongsTo(BlockPos pos) {
        return this.controllerPos.equals(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("ControllerPos", this.controllerPos.asLong());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
