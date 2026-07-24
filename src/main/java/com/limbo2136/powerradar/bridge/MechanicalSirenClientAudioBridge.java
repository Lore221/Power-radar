package com.limbo2136.powerradar.bridge;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Нейтральный по стороне шлюз к зацикленному клиентскому звуку механической сирены. */
public final class MechanicalSirenClientAudioBridge {
    // До клиентской инициализации вызовы намеренно ничего не делают.
    private static Handler handler = Handler.NO_OP;

    private MechanicalSirenClientAudioBridge() {
    }

    public static void setHandler(Handler handler) {
        MechanicalSirenClientAudioBridge.handler = handler;
    }

    public static void tick(@Nullable Level level, BlockPos pos, float speed, boolean redstonePowered) {
        handler.tick(level, pos, speed, redstonePowered);
    }

    public static void onRemoved(@Nullable Level level, BlockPos pos) {
        handler.onRemoved(level, pos);
    }

    public interface Handler {
        Handler NO_OP = new Handler() {
        };

        default void tick(@Nullable Level level, BlockPos pos, float speed, boolean redstonePowered) {
        }

        default void onRemoved(@Nullable Level level, BlockPos pos) {
        }
    }
}
