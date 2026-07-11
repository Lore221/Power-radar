package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PowerRadarCeeBlockLifecycle {
    private PowerRadarCeeBlockLifecycle() {
    }

    public static void removeCreativeConnections(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || player == null || !player.isCreative()) {
            return;
        }
        InfrastructureSavedData infrastructure = InfrastructureSavedData.load(serverLevel);
        for (InWorldNodeData node : List.copyOf(infrastructure.getNodesAt(pos))) {
            for (InWorldNodeConnection connection : List.copyOf(infrastructure.getConnections(node))) {
                infrastructure.removeConnection(connection);
            }
        }
    }
}
