package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.network.RadarMonitorBlockSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorPosePayloadFactory;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayBuilder;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.OnlinePlayersSnapshotCache;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Общий серверный runtime устройств Power Radar, установленных в щиток CEE. */
public final class RadarPanelMonitorRuntime {
    private RadarPanelMonitorRuntime() {
    }

    public static RadarMonitorSnapshotPayload createSnapshot(
            ServerLevel level,
            BlockPos monitorPos,
            RadarDisplayPanelAttachment display
    ) {
        return createSnapshot(level, monitorPos, display, display.currentNetworkResolution(level));
    }

    public static RadarMonitorSnapshotPayload createSnapshot(
            ServerLevel level,
            BlockPos monitorPos,
            RadarDisplayPanelAttachment display,
            PanelNetworkResolution resolution
    ) {
        Direction facing = panelFacing(level, monitorPos);
        if (!display.isElectricallyOperational()) {
            return noLinkSnapshot(level, monitorPos, facing, display, RadarNetworkConnectionStatus.NO_LINK);
        }

        if (resolution.networkId() == null || resolution.consumerPos() == null) {
            return noLinkSnapshot(level, monitorPos, facing, display, resolution.status());
        }
        if (resolution.controllers().isEmpty()
                || resolution.status() != RadarNetworkConnectionStatus.CONNECTED) {
            return noLinkSnapshot(level, monitorPos, facing, display, resolution.status());
        }

        OnlinePlayersSnapshotCache.Snapshot onlinePlayers = OnlinePlayersSnapshotCache.snapshot(level);
        List<String> onlineNames = onlinePlayers.names();
        int onlineHash = onlinePlayers.hash();

        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        RadarMonitorDisplayData data = manager.displayDataForConsumer(
                resolution.networkId(),
                GlobalPos.of(level.dimension(), resolution.consumerPos()),
                monitorPos,
                facing,
                resolution.controllers(),
                level.getGameTime(),
                display.electricalState(),
                display.voltageVolts(),
                display.resistanceOhms(),
                1,
                1,
                true,
                onlineHash,
                onlineNames);
        return RadarMonitorSnapshotPayload.fromDisplayData(
                data,
                snapshotRevision(manager, resolution, display, onlineHash));
    }

    public static void sendSnapshotToNearby(
            ServerLevel level,
            BlockPos monitorPos,
            RadarMonitorSnapshotPayload snapshot
    ) {
        Vec3 worldCenter = RadarWorldPoseResolver.worldPosition(level, monitorPos);
        double range = RadarConstants.RADAR_MONITOR_BLOCK_SYNC_RANGE_BLOCKS;
        double rangeSqr = range * range;
        RadarMonitorBlockSnapshotPayload payload = new RadarMonitorBlockSnapshotPayload(snapshot);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldCenter.x, worldCenter.y, worldCenter.z) <= rangeSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static boolean sendMovingPoseToNearby(
            ServerLevel level,
            BlockPos monitorPos,
            PanelNetworkResolution resolution,
            boolean movingPosePublished
    ) {
        RadarMonitorBlockPosePayload payload = RadarMonitorPosePayloadFactory.create(
                level, monitorPos, panelFacing(level, monitorPos), resolution.controllers());
        if (payload == null) {
            if (!movingPosePublished) {
                return false;
            }
            payload = new RadarMonitorBlockPosePayload(
                    monitorPos, level.getGameTime(), null, List.of());
        }
        Vec3 worldCenter = RadarWorldPoseResolver.worldPosition(level, monitorPos);
        double range = RadarConstants.RADAR_MONITOR_BLOCK_SYNC_RANGE_BLOCKS;
        double rangeSqr = range * range;
        boolean sent = false;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldCenter.x, worldCenter.y, worldCenter.z) <= rangeSqr) {
                PacketDistributor.sendToPlayer(player, payload);
                sent = true;
            }
        }
        return sent
                ? payload.monitorPose() != null || !payload.poses().isEmpty()
                : movingPosePublished;
    }

    @Nullable
    public static RadarDisplayPanelAttachment findDisplay(ServerLevel level, BlockPos panelPos) {
        if (!(level.getBlockEntity(panelPos) instanceof ElectricalPanelBlockEntity panel)) {
            return null;
        }
        for (PanelAttachment attachment : panel.getAttachments()) {
            if (attachment instanceof RadarDisplayPanelAttachment display) {
                return display;
            }
        }
        return null;
    }

    /** Разрешает сеть, сохранённую непосредственно в установленном предмете Display. */
    public static PanelNetworkResolution resolveBoundNetwork(
            ServerLevel level,
            BlockPos monitorPos,
            @Nullable UUID networkId
    ) {
        if (networkId == null) {
            return PanelNetworkResolution.empty(RadarNetworkConnectionStatus.NO_LINK);
        }
        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        if (!manager.networkExists(networkId)) {
            return PanelNetworkResolution.empty(RadarNetworkConnectionStatus.NO_LINK);
        }
        RadarNetworkManager.ControllersResolution controllers = manager.resolveControllersForConsumer(
                networkId,
                GlobalPos.of(level.dimension(), monitorPos));
        return new PanelNetworkResolution(
                networkId,
                monitorPos.immutable(),
                controllers.status(),
                controllers.controllers());
    }

    /** Обновляет доступные контроллеры без повторного чтения состояния attachment. */
    public static PanelNetworkResolution refreshCachedControllers(
            ServerLevel level,
            PanelNetworkResolution cached
    ) {
        if (cached.networkId() == null || cached.consumerPos() == null) {
            return cached;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        if (!manager.networkExists(cached.networkId())) {
            return PanelNetworkResolution.empty(RadarNetworkConnectionStatus.NO_LINK);
        }
        RadarNetworkManager.ControllersResolution controllers = manager.resolveControllersForConsumer(
                cached.networkId(),
                GlobalPos.of(level.dimension(), cached.consumerPos()));
        return new PanelNetworkResolution(
                cached.networkId(),
                cached.consumerPos(),
                controllers.status(),
                controllers.controllers());
    }

    private static RadarMonitorSnapshotPayload noLinkSnapshot(
            ServerLevel level,
            BlockPos monitorPos,
            Direction facing,
            RadarDisplayPanelAttachment display,
            RadarNetworkConnectionStatus status
    ) {
        RadarMonitorDisplayData data = RadarMonitorDisplayBuilder.noLink(
                monitorPos,
                facing,
                level.getGameTime(),
                status,
                display.electricalState(),
                display.voltageVolts(),
                display.resistanceOhms(),
                1,
                1,
                display.isElectricallyOperational());
        long revision = Objects.hash(
                status,
                display.electricalState(),
                Math.round(display.voltageVolts() * 10.0D));
        return RadarMonitorSnapshotPayload.fromDisplayData(data, revision);
    }

    private static long snapshotRevision(
            RadarNetworkManager manager,
            PanelNetworkResolution resolution,
            RadarDisplayPanelAttachment display,
            int onlinePlayersHash
    ) {
        long revision = 17L;
        revision = 31L * revision + Objects.hashCode(resolution.networkId());
        revision = 31L * revision + Objects.hashCode(resolution.consumerPos());
        revision = 31L * revision + Objects.hashCode(resolution.status());
        revision = 31L * revision + Objects.hashCode(display.electricalState());
        revision = 31L * revision + Math.round(display.voltageVolts() * 10.0D);
        revision = 31L * revision + onlinePlayersHash;
        if (resolution.networkId() != null) {
            revision = 31L * revision + manager.settingsRevision(resolution.networkId());
        }
        for (RadarControllerBlockEntity controller : resolution.controllers()) {
            revision = 31L * revision + controller.getBlockPos().asLong();
            revision = 31L * revision + controller.lastScanGameTime();
            revision = 31L * revision + controller.displayRevision();
        }
        return revision;
    }

    private static Direction panelFacing(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).hasProperty(ElectricalPanelBlock.FACING)
                ? level.getBlockState(pos).getValue(ElectricalPanelBlock.FACING)
                : Direction.NORTH;
    }

    public record PanelNetworkResolution(
            @Nullable UUID networkId,
            @Nullable BlockPos consumerPos,
            RadarNetworkConnectionStatus status,
            List<RadarControllerBlockEntity> controllers
    ) {
        private static PanelNetworkResolution empty(RadarNetworkConnectionStatus status) {
            return new PanelNetworkResolution(null, null, status, List.of());
        }
    }
}
