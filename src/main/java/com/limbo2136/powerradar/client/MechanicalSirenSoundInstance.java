package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class MechanicalSirenSoundInstance extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 1.25F;

    private boolean active = true;
    private int keepAlive;
    private float targetVolume;

    public MechanicalSirenSoundInstance(BlockPos pos) {
        super(ModSounds.MECHANICAL_SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        Vec3 center = Vec3.atCenterOf(pos);
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.02F;
        this.pitch = 1.0F;
    }

    public void keepAlive(float rpm) {
        this.active = true;
        this.keepAlive = 2;
        this.targetVolume = Mth.clamp(Math.abs(rpm) / 256.0F, 0.0F, 1.0F) * MAX_VOLUME;
    }

    public void fadeOut() {
        this.active = false;
        this.targetVolume = 0.0F;
    }

    @Override
    public void tick() {
        if (this.active && --this.keepAlive <= 0) {
            fadeOut();
        }
        this.volume = Mth.lerp(0.12F, this.volume, this.targetVolume);
        if (!this.active && this.volume < 0.002F) {
            stop();
        }
    }
}
