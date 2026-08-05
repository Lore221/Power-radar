package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.bridge.MechanicalSirenClientAudioBridge;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class MechanicalSirenClientAudioRuntime {
    private static final float MINIMUM_AUDIBLE_SPEED = 1.0F;
    private static boolean initialized;
    private static final Map<BlockPos, MechanicalSirenSoundInstance> sounds = new HashMap<>();
    @Nullable
    private static ClientLevel levelSession;

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
                    ensureLevelSession(clientLevel);
                    stopSound(pos);
                }
            }
        });
    }

    private static void tickSound(ClientLevel level, BlockPos pos, float speed, boolean redstonePowered) {
        // Звуки эфемерны: при смене измерения старые экземпляры плавно гасятся и удаляются.
        ensureLevelSession(level);
        BlockPos key = pos.immutable();
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

    private static void ensureLevelSession(ClientLevel level) {
        if (level == levelSession) {
            return;
        }
        for (MechanicalSirenSoundInstance sound : sounds.values()) {
            sound.fadeOut();
        }
        sounds.clear();
        levelSession = level;
    }

    private static void stopSound(BlockPos key) {
        MechanicalSirenSoundInstance sound = sounds.remove(key);
        if (sound != null) {
            sound.fadeOut();
        }
    }
}
