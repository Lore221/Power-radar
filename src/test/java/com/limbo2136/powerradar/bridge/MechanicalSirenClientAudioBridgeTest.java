package com.limbo2136.powerradar.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MechanicalSirenClientAudioBridgeTest {
    @AfterEach
    void resetHandler() {
        MechanicalSirenClientAudioBridge.setHandler(MechanicalSirenClientAudioBridge.Handler.NO_OP);
    }

    @Test
    void forwardsSoundStateAndRemovalWithoutClientClasses() {
        BlockPos position = new BlockPos(4, 80, -12);
        int[] calls = {0};
        MechanicalSirenClientAudioBridge.setHandler(new MechanicalSirenClientAudioBridge.Handler() {
            @Override
            public void tick(net.minecraft.world.level.Level level, BlockPos pos, float speed, boolean redstonePowered) {
                assertEquals(position, pos);
                assertEquals(96.0F, speed);
                assertEquals(true, redstonePowered);
                calls[0]++;
            }

            @Override
            public void onRemoved(net.minecraft.world.level.Level level, BlockPos pos) {
                assertEquals(position, pos);
                calls[0]++;
            }
        });

        MechanicalSirenClientAudioBridge.tick(null, position, 96.0F, true);
        MechanicalSirenClientAudioBridge.onRemoved(null, position);

        assertEquals(2, calls[0]);
    }
}
