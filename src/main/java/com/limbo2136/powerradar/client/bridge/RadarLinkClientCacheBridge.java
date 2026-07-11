package com.limbo2136.powerradar.client.bridge;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class RadarLinkClientCacheBridge {
    private static Handler handler = Handler.NO_OP;

    private RadarLinkClientCacheBridge() {
    }

    public static void setHandler(Handler handler) {
        RadarLinkClientCacheBridge.handler = handler;
    }

    public static void onLoaded(@Nullable Level level, BlockPos pos, @Nullable UUID networkId) {
        handler.onLoaded(level, pos, networkId);
    }

    public static void onNetworkChanged(@Nullable Level level, BlockPos pos, @Nullable UUID oldId, @Nullable UUID newId) {
        handler.onNetworkChanged(level, pos, oldId, newId);
    }

    public static void onRemoved(@Nullable Level level, BlockPos pos) {
        handler.onRemoved(level, pos);
    }

    public interface Handler {
        Handler NO_OP = new Handler() {
        };

        default void onLoaded(@Nullable Level level, BlockPos pos, @Nullable UUID networkId) {
        }

        default void onNetworkChanged(@Nullable Level level, BlockPos pos, @Nullable UUID oldId, @Nullable UUID newId) {
        }

        default void onRemoved(@Nullable Level level, BlockPos pos) {
        }
    }
}
