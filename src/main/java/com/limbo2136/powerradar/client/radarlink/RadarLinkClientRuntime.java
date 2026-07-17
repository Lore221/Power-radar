package com.limbo2136.powerradar.client.radarlink;

import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import net.minecraft.client.multiplayer.ClientLevel;
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
            public void onLoaded(Level level, net.minecraft.core.BlockPos pos, java.util.UUID networkId) {
                if (level instanceof ClientLevel clientLevel) {
                    RadarLinkClientCache.registerOrUpdate(clientLevel, pos, networkId);
                }
            }

            @Override
            public void onNetworkChanged(Level level, net.minecraft.core.BlockPos pos, java.util.UUID oldId, java.util.UUID newId) {
                if (level instanceof ClientLevel clientLevel) {
                    RadarLinkClientCache.registerOrUpdate(clientLevel, pos, newId);
                }
            }

            @Override
            public void onRemoved(Level level, net.minecraft.core.BlockPos pos) {
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
                            net.minecraft.core.BlockPos pos,
                            java.util.UUID networkId
                    ) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.registerOrUpdate(clientLevel, pos, networkId);
                        }
                    }

                    @Override
                    public void onNetworkChanged(
                            Level level,
                            net.minecraft.core.BlockPos pos,
                            java.util.UUID oldId,
                            java.util.UUID newId
                    ) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.registerOrUpdate(clientLevel, pos, newId);
                        }
                    }

                    @Override
                    public void onRemoved(Level level, net.minecraft.core.BlockPos pos) {
                        if (level instanceof ClientLevel clientLevel) {
                            InterceptionNetworkClientCache.unregister(clientLevel, pos);
                        }
                    }
                });
    }
}
