package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.bridge.MechanicalSirenClientAudioBridge;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class MechanicalSirenClientAudioRuntime {
    private static final float MINIMUM_AUDIBLE_SPEED = 1.0F;
    private static boolean initialized;
    private static final Map<GlobalPos, MechanicalSirenSoundInstance> sounds = new HashMap<>();

    private MechanicalSirenClientAudioRuntime() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MechanicalSirenClientAudioBridge.setHandler(new MechanicalSirenClientAudioBridge.Handler() {
            @Override
            public void tick(Level level, BlockPos pos, float speed, boolean redstonePowered) {
                if (level instanceof ClientLevel clientLevel) {
                    tickSound(clientLevel, pos, speed, redstonePowered);
                }
            }

            @Override
            public void onRemoved(Level level, BlockPos pos) {
                if (level instanceof ClientLevel clientLevel) {
                    stopSound(GlobalPos.of(clientLevel.dimension(), pos));
                }
            }
        });
    }

    private static void tickSound(ClientLevel level, BlockPos pos, float speed, boolean redstonePowered) {
        discardSoundsFromOtherDimensions(level.dimension());
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        if (!redstonePowered || speed < MINIMUM_AUDIBLE_SPEED) {
            stopSound(key);
            return;
        }
        MechanicalSirenSoundInstance sound = sounds.get(key);
        if (sound == null || sound.isStopped()) {
            sound = new MechanicalSirenSoundInstance(pos);
            sounds.put(key, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.keepAlive(speed);
    }

    private static void discardSoundsFromOtherDimensions(ResourceKey<Level> currentDimension) {
        Iterator<Map.Entry<GlobalPos, MechanicalSirenSoundInstance>> iterator = sounds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GlobalPos, MechanicalSirenSoundInstance> entry = iterator.next();
            if (entry.getKey().dimension() != currentDimension) {
                entry.getValue().fadeOut();
                iterator.remove();
            }
        }
    }

    private static void stopSound(GlobalPos key) {
        MechanicalSirenSoundInstance sound = sounds.remove(key);
        if (sound != null) {
            sound.fadeOut();
        }
    }
}
