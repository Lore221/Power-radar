package com.limbo2136.powerradar.client.radarlink;

import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class RadarLinkClientRuntime {
    private static boolean initialized;

    private RadarLinkClientRuntime() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RadarNetworkNodeClientCacheBridge.setHandler(new RadarNetworkNodeClientCacheBridge.Handler() {
            @Override
            public void onLoaded(Level level, BlockPos pos, UUID networkId) {
                if (level instanceof ClientLevel clientLevel) {
                    RadarLinkClientCache.registerOrUpdate(clientLevel, pos, networkId);
                }
            }

            @Override
            public void onNetworkChanged(Level level, BlockPos pos, UUID oldId, UUID newId) {
                if (level instanceof ClientLevel clientLevel) {
                    RadarLinkClientCache.registerOrUpdate(clientLevel, pos, newId);
                }
            }

            @Override
            public void onRemoved(Level level, BlockPos pos) {
                if (level instanceof ClientLevel clientLevel) {
                    RadarLinkClientCache.unregister(clientLevel, pos);
                }
            }
        });
        InterceptionNetworkNodeClientCacheBridge.setHandler(
                new InterceptionNetworkNodeClientCacheBridge.Handler() {
                    @Override
                    public void onLoaded(
                            Level level,
                            BlockPos pos,
                            UUID networkId
                    ) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.registerOrUpdate(clientLevel, pos, networkId);
                        }
                    }

                    @Override
                    public void onNetworkChanged(
                            Level level,
                            BlockPos pos,
                            UUID oldId,
                            UUID newId
                    ) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.registerOrUpdate(clientLevel, pos, newId);
                        }
                    }

                    @Override
                    public void onRemoved(Level level, BlockPos pos) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.unregister(clientLevel, pos);
                        }
                    }
                });
    }
}
