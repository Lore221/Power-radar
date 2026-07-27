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
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

/**
 * Связывает панельные Link и Display без отдельного block entity.
 * Область Link — квадрат 3x3 в плоскости одинаково ориентированных щитков.
 */
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

        if (resolution.networkId() == null || resolution.linkPos() == null) {
            return noLinkSnapshot(level, monitorPos, facing, display, resolution.status());
        }
        if (resolution.controllers().isEmpty()
                || resolution.status() != RadarNetworkConnectionStatus.CONNECTED) {
            return noLinkSnapshot(level, monitorPos, facing, display, resolution.status());
        }

        List<String> onlineNames = level.getServer().getPlayerList().getPlayers().stream()
                .map(player -> player.getGameProfile().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int onlineHash = 1;
        for (String name : onlineNames) {
            onlineHash = 31 * onlineHash + name.toLowerCase(Locale.ROOT).hashCode();
        }

        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        RadarMonitorDisplayData data = manager.displayDataForConsumer(
                resolution.networkId(),
                GlobalPos.of(level.dimension(), resolution.linkPos()),
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

    public static void sendMovingPoseToNearby(
            ServerLevel level,
            BlockPos monitorPos,
            PanelNetworkResolution resolution
    ) {
        if (resolution.controllers().isEmpty()) {
            return;
        }
        RadarMonitorBlockPosePayload payload = RadarMonitorPosePayloadFactory.create(
                level, monitorPos, panelFacing(level, monitorPos), resolution.controllers());
        if (payload == null) {
            return;
        }
        Vec3 worldCenter = RadarWorldPoseResolver.worldPosition(level, monitorPos);
        double range = RadarConstants.RADAR_MONITOR_BLOCK_SYNC_RANGE_BLOCKS;
        double rangeSqr = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldCenter.x, worldCenter.y, worldCenter.z) <= rangeSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
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

    public static PanelNetworkResolution resolvePanelNetwork(ServerLevel level, BlockPos monitorPos) {
        Direction facing = panelFacing(level, monitorPos);
        Direction horizontal = facing.getClockWise();
        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        ArrayList<LinkCandidate> candidates = new ArrayList<>();

        for (int horizontalOffset = -1; horizontalOffset <= 1; horizontalOffset++) {
            for (int verticalOffset = -1; verticalOffset <= 1; verticalOffset++) {
                BlockPos candidatePos = monitorPos.relative(horizontal, horizontalOffset)
                        .offset(0, verticalOffset, 0);
                if (!level.isLoaded(candidatePos)
                        || panelFacing(level, candidatePos) != facing
                        || !(level.getBlockEntity(candidatePos) instanceof ElectricalPanelBlockEntity panel)) {
                    continue;
                }
                for (PanelAttachment attachment : panel.getAttachments()) {
                    if (attachment instanceof RadarLinkPanelAttachment link
                            && link.isElectricallyOperational()
                            && link.networkId() != null
                            && manager.networkExists(link.networkId())) {
                        candidates.add(new LinkCandidate(
                                link.networkId(),
                                candidatePos.immutable(),
                                link.installationOrder(),
                                link.slot.ordinal()));
                    }
                }
            }
        }

        LinkCandidate selected = candidates.stream()
                .min(Comparator.comparingLong(LinkCandidate::installationOrder)
                        .thenComparing(candidate -> candidate.networkId().toString())
                        .thenComparingLong(candidate -> candidate.panelPos().asLong())
                        .thenComparingInt(LinkCandidate::slotIndex))
                .orElse(null);
        if (selected == null) {
            return PanelNetworkResolution.empty(RadarNetworkConnectionStatus.NO_LINK);
        }
        RadarNetworkManager.ControllersResolution controllers = manager.resolveControllersForConsumer(
                selected.networkId(),
                GlobalPos.of(level.dimension(), selected.panelPos()));
        return new PanelNetworkResolution(
                selected.networkId(),
                selected.panelPos(),
                controllers.status(),
                controllers.controllers());
    }

    /**
     * Обновляет контроллеры сети без повторного обхода щитков.
     * Сам Link и его питание проверяются отдельным редким обновлением кэша монитора.
     */
    public static PanelNetworkResolution refreshCachedControllers(
            ServerLevel level,
            PanelNetworkResolution cached
    ) {
        if (cached.networkId() == null || cached.linkPos() == null) {
            return cached;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        if (!manager.networkExists(cached.networkId())) {
            return PanelNetworkResolution.empty(RadarNetworkConnectionStatus.NO_LINK);
        }
        RadarNetworkManager.ControllersResolution controllers = manager.resolveControllersForConsumer(
                cached.networkId(),
                GlobalPos.of(level.dimension(), cached.linkPos()));
        return new PanelNetworkResolution(
                cached.networkId(),
                cached.linkPos(),
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
        revision = 31L * revision + Objects.hashCode(resolution.linkPos());
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

    private record LinkCandidate(
            UUID networkId,
            BlockPos panelPos,
            long installationOrder,
            int slotIndex
    ) {
    }

    public record PanelNetworkResolution(
            @Nullable UUID networkId,
            @Nullable BlockPos linkPos,
            RadarNetworkConnectionStatus status,
            List<RadarControllerBlockEntity> controllers
    ) {
        private static PanelNetworkResolution empty(RadarNetworkConnectionStatus status) {
            return new PanelNetworkResolution(null, null, status, List.of());
        }
    }
}
