package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarDisplayStructure;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.block.RadarMonitorControllerBlock;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.network.RadarMonitorBlockStaticPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPose;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayBuilder;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadarMonitorControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final java.util.Map<MinecraftServer, OnlinePlayersCache> ONLINE_PLAYERS_CACHE = new java.util.WeakHashMap<>();
    private static final int NEARBY_PLAYERS_CACHE_TICKS = 20;

    @Nullable
    private BlockPos activeOrigin;
    private int activeSize;
    private Direction activeFacing = Direction.NORTH;
    private RadarDisplayStructureResolver.StructureStatus structureStatus =
            RadarDisplayStructureResolver.StructureStatus.NO_DISPLAY;
    private int structureRevision;
    private int ticksSinceSync;
    private long lastSentBlockSnapshotRevision = Long.MIN_VALUE;
    private long lastSentBlockStaticRevision = Long.MIN_VALUE;
    private long lastSentBlockStaticGameTime = Long.MIN_VALUE;
    private long lastSentBlockSnapshotScanGameTime = Long.MIN_VALUE;
    private boolean needsStructureReconcile = true;
    private boolean needsLeaseReconcile = true;
    private int startupSafetyTicks = 2;
    private boolean removingOrUnloading;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
    private double cachedElectricalVoltageVolts;
    private double cachedElectricalCurrentAmps;
    private double cachedElectricalPowerWatts;
    private double cachedElectricalResistanceOhms = PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
    @Nullable
    private UUID cachedConsumerLeaseNetworkId;
    @Nullable
    private GlobalPos cachedConsumerLeaseLinkPos;
    @Nullable
    private RadarMonitorSnapshotPayload cachedSnapshot;
    @Nullable
    private SnapshotKey cachedSnapshotKey;
    private long localSnapshotRevision;
    @Nullable
    private UUID cachedSnapshotNetworkId;
    @Nullable
    private GlobalPos cachedSnapshotLinkPos;
    @Nullable
    private List<RadarControllerBlockEntity> cachedSnapshotControllers = List.of();
    private RadarNetworkConnectionStatus cachedSnapshotConnectionStatus = RadarNetworkConnectionStatus.NO_LINK;
    private long cachedSnapshotResolutionGameTime = Long.MIN_VALUE;
    private long nearbyPlayersCacheGameTime = Long.MIN_VALUE;
    private List<ServerPlayer> cachedNearbyPlayers = List.of();
    private boolean dynamicSnapshotResolution;

    public RadarMonitorControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.RADAR_MONITOR_CONTROLLER.get(), pos, blockState);
    }

    protected RadarMonitorControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.activeFacing = facingFromState(blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.needsStructureReconcile = usesDisplayStructureResolver() && !hasDisplayStructureCache();
        this.needsLeaseReconcile = true;
        this.startupSafetyTicks = 2;
        this.removingOrUnloading = false;
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.needsStructureReconcile = usesDisplayStructureResolver() && !hasDisplayStructureCache();
        this.needsLeaseReconcile = true;
        this.startupSafetyTicks = 2;
        this.removingOrUnloading = false;
    }

    public void prepareForBlockRemoval() {
        this.removingOrUnloading = true;
        invalidateSnapshotCache();
        reconcileConsumerLease("block-removal", true);
    }

    @Override
    public void onChunkUnloaded() {
        this.removingOrUnloading = true;
        releaseCachedConsumerLease("chunk-unload");
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        this.removingOrUnloading = true;
        super.remove();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadarMonitorControllerBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }
        if (blockEntity.removingOrUnloading) {
            return;
        }

        blockEntity.tick();
        blockEntity.runDeferredLifecycleWork(level, pos);

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        blockEntity.ticksSinceSync++;
        if (blockEntity.shouldPollSnapshot(serverLevel)) {
            RadarMonitorSnapshotPayload snapshot = getOrCreateSnapshotPayload(serverLevel, pos);
            if (blockEntity.shouldSendBlockSnapshot(snapshot)) {
                blockEntity.ticksSinceSync = 0;
                blockEntity.lastSentBlockSnapshotRevision = snapshot.revision();
                blockEntity.lastSentBlockSnapshotScanGameTime = snapshot.lastScanGameTime();
                blockEntity.sendBlockSnapshotToNearby(serverLevel, snapshot);
            }
        }
        // The pose delta is deliberately last. On scan ticks it must supersede the
        // coverage pose embedded in the lower-frequency static snapshot.
        blockEntity.sendMovingPoses(serverLevel, pos);
    }

    private void sendMovingPoses(ServerLevel serverLevel, BlockPos monitorPos) {
        refreshCachedSnapshotResolutionIfNeeded(serverLevel, monitorPos);
        List<RadarMonitorBlockPosePayload.RadarPose> poses = new ArrayList<>();
        long gameTime = serverLevel.getGameTime();
        RadarWorldPose monitorWorldPose = RadarWorldPoseResolver.resolve(
                serverLevel,
                monitorPos,
                net.minecraft.world.phys.Vec3.atCenterOf(monitorPos),
                RadarGeometry.yawDegrees(facingFromState(getBlockState()).getOpposite()));
        RadarMonitorBlockPosePayload.MonitorPose monitorPose = monitorWorldPose.onSableStructure()
                ? new RadarMonitorBlockPosePayload.MonitorPose(
                        monitorWorldPose.origin().x, monitorWorldPose.origin().y, monitorWorldPose.origin().z,
                        monitorWorldPose.yawDegrees())
                : null;
        for (RadarControllerBlockEntity controller : this.cachedSnapshotControllers) {
            RadarWorldPose pose = controller.worldPoseAt(gameTime);
            if (!pose.onSableStructure()) {
                continue;
            }
            poses.add(new RadarMonitorBlockPosePayload.RadarPose(
                    controller.radarId(),
                    pose.origin().x, pose.origin().y, pose.origin().z,
                    controller.orientationState().structureType() == RadarStructureType.OVERVIEW
                            ? 0.0F
                            : pose.yawDegrees()));
        }
        if (monitorPose == null && poses.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = nearbyPlayers(serverLevel);
        if (players.isEmpty()) {
            return;
        }
        RadarMonitorBlockPosePayload payload = new RadarMonitorBlockPosePayload(
                monitorPos, gameTime, monitorPose, List.copyOf(poses));
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private boolean shouldPollSnapshot(ServerLevel level) {
        if (this.cachedSnapshot == null || this.cachedSnapshotKey == null) {
            return true;
        }
        int interval = Math.max(1, this.cachedSnapshot.trackUpdateIntervalTicks());
        if (!this.cachedSnapshot.linked() || this.cachedSnapshot.lastScanGameTime() <= 0L) {
            interval = Math.max(1, RadarConstants.RADAR_MONITOR_BLOCK_UPDATE_INTERVAL_TICKS);
        }
        BlockPos phasePos = this.cachedSnapshotLinkPos == null ? this.worldPosition : this.cachedSnapshotLinkPos.pos();
        int phase = snapshotPhase(phasePos, interval);
        return Math.floorMod((int) level.getGameTime() - phase, interval) == 0;
    }

    private static int snapshotPhase(BlockPos pos, int interval) {
        int hash = pos.getX() * 73428767 ^ pos.getY() * 912931 ^ pos.getZ() * 42317861;
        return Math.floorMod(hash, Math.max(1, interval));
    }

    private void sendBlockSnapshotToNearby(ServerLevel serverLevel) {
        sendBlockSnapshotToNearby(serverLevel, getOrCreateSnapshotPayload(serverLevel, this.worldPosition));
    }

    private void sendBlockSnapshotToNearby(ServerLevel serverLevel, RadarMonitorSnapshotPayload snapshot) {
        List<ServerPlayer> nearbyPlayers = nearbyPlayers(serverLevel);
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        boolean refreshStatic = snapshot.revision() != this.lastSentBlockStaticRevision
                || this.lastSentBlockStaticGameTime == Long.MIN_VALUE
                || gameTime - this.lastSentBlockStaticGameTime >= NEARBY_PLAYERS_CACHE_TICKS;
        if (refreshStatic) {
            RadarMonitorSnapshotPayload staticSnapshot = snapshot.withTargets(List.of());
            RadarMonitorBlockStaticPayload staticPayload = new RadarMonitorBlockStaticPayload(staticSnapshot);
            for (ServerPlayer player : nearbyPlayers) {
                PacketDistributor.sendToPlayer(player, staticPayload);
            }
            this.lastSentBlockStaticRevision = snapshot.revision();
            this.lastSentBlockStaticGameTime = gameTime;
        }
        RadarMonitorBlockTargetsPayload payload = new RadarMonitorBlockTargetsPayload(
                snapshot.monitorPos(),
                snapshot.revision(),
                snapshot.lastScanGameTime(),
                snapshot.serverGameTime(),
                snapshot.targets());
        for (ServerPlayer player : nearbyPlayers) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        RadarWorldPose monitorPose = RadarWorldPoseResolver.resolve(
                serverLevel, this.worldPosition, net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition), 0.0F);
        if (!monitorPose.onSableStructure()
                && this.nearbyPlayersCacheGameTime != Long.MIN_VALUE
                && gameTime - this.nearbyPlayersCacheGameTime < NEARBY_PLAYERS_CACHE_TICKS) {
            return this.cachedNearbyPlayers;
        }
        double range = RadarConstants.RADAR_MONITOR_BLOCK_SYNC_RANGE_BLOCKS;
        double rangeSqr = range * range;
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(
                    monitorPose.origin().x,
                    monitorPose.origin().y,
                    monitorPose.origin().z) <= rangeSqr) {
                nearbyPlayers.add(player);
            }
        }
        this.cachedNearbyPlayers = List.copyOf(nearbyPlayers);
        this.nearbyPlayersCacheGameTime = gameTime;
        return this.cachedNearbyPlayers;
    }

    private boolean shouldSendBlockSnapshot(RadarMonitorSnapshotPayload snapshot) {
        int fallbackInterval = Math.max(1, snapshot.trackUpdateIntervalTicks());
        if (!snapshot.linked() || snapshot.lastScanGameTime() <= 0L) {
            return this.ticksSinceSync >= Math.max(1, RadarConstants.RADAR_MONITOR_BLOCK_UPDATE_INTERVAL_TICKS);
        }
        if (snapshot.lastScanGameTime() != this.lastSentBlockSnapshotScanGameTime) {
            return true;
        }
        return snapshot.revision() != this.lastSentBlockSnapshotRevision && this.ticksSinceSync >= fallbackInterval;
    }

    public static RadarMonitorSnapshotPayload createSnapshotPayload(ServerLevel level, BlockPos controllerPos) {
        return getOrCreateSnapshotPayload(level, controllerPos);
    }

    public static RadarMonitorSnapshotPayload getOrCreateSnapshotPayload(ServerLevel level, BlockPos controllerPos) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof RadarMonitorControllerBlockEntity monitorController) {
            SnapshotKey key = monitorController.snapshotKey(level, controllerPos);
            if (monitorController.cachedSnapshot != null && Objects.equals(monitorController.cachedSnapshotKey, key)) {
                return monitorController.cachedSnapshot;
            }
            RadarMonitorSnapshotPayload snapshot = buildSnapshotPayload(level, controllerPos, monitorController, key.revision());
            monitorController.cachedSnapshot = snapshot;
            monitorController.cachedSnapshotKey = key;
            return snapshot;
        }
        return buildSnapshotPayload(level, controllerPos, null, level.getGameTime());
    }

    private static RadarMonitorSnapshotPayload buildSnapshotPayload(
            ServerLevel level,
            BlockPos controllerPos,
            @Nullable RadarMonitorControllerBlockEntity monitorController,
            long revision
    ) {
        Direction monitorFacing = level.getBlockState(controllerPos).hasProperty(RadarMonitorControllerBlock.FACING)
                ? level.getBlockState(controllerPos).getValue(RadarMonitorControllerBlock.FACING)
                : Direction.NORTH;
        if (monitorController == null) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    RadarMonitorDisplayBuilder.noLink(controllerPos, monitorFacing, level.getGameTime()), revision);
        }
        if (monitorController.structureStatus != RadarDisplayStructureResolver.StructureStatus.ACTIVE
                || monitorController.activeSize <= 0) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitorController.noLinkDisplayData(controllerPos, monitorFacing, level.getGameTime(), RadarNetworkConnectionStatus.NO_LINK),
                    revision);
        }
        if (!monitorController.isRendererEnabled()) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitorController.noLinkDisplayData(controllerPos, monitorFacing, level.getGameTime(), RadarNetworkConnectionStatus.NO_LINK),
                    revision);
        }

        if (monitorController.cachedSnapshotNetworkId == null
                || monitorController.cachedSnapshotLinkPos == null) {
            RadarNetworkConnectionStatus status = monitorController.cachedSnapshotConnectionStatus;
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitorController.noLinkDisplayData(controllerPos, monitorFacing, level.getGameTime(), status),
                    revision);
        }

        RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
        UUID networkId = monitorController.cachedSnapshotNetworkId;
        List<RadarControllerBlockEntity> controllers = monitorController.cachedSnapshotControllers;
        if (monitorController.hasRemovedCachedSnapshotController()) {
            controllers = controllers.stream()
                    .filter(controller -> controller != null && !controller.isRemoved())
                    .toList();
        }
        if (controllers.isEmpty()
                || monitorController.cachedSnapshotConnectionStatus != RadarNetworkConnectionStatus.CONNECTED) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitorController.noLinkDisplayData(
                            controllerPos,
                            monitorFacing,
                            level.getGameTime(),
                            monitorController.cachedSnapshotConnectionStatus),
                    revision);
        }
        OnlinePlayersSnapshot onlinePlayers = onlinePlayersSnapshot(level);
        RadarMonitorDisplayData displayData = networkManager.displayDataForConsumer(
                        networkId,
                        monitorController.cachedSnapshotLinkPos,
                        controllerPos,
                        monitorFacing,
                        controllers,
                        level.getGameTime(),
                        monitorController.electricalState(),
                        monitorController.electricalVoltageVolts(),
                        monitorController.electricalResistanceOhms(),
                        monitorController.activeDisplayCount(),
                        monitorController.activeSize(),
                        monitorController.isRendererEnabled(),
                        onlinePlayers.hash(),
                        onlinePlayers.names());
        return RadarMonitorSnapshotPayload.fromDisplayData(displayData, revision);
    }

    public Direction facing() {
        return this.getBlockState().hasProperty(RadarMonitorControllerBlock.FACING)
                ? this.getBlockState().getValue(RadarMonitorControllerBlock.FACING)
                : this.activeFacing;
    }

    private RadarMonitorDisplayData noLinkDisplayData(
            BlockPos controllerPos,
            Direction monitorFacing,
            long serverGameTime,
            RadarNetworkConnectionStatus status
    ) {
        PowerRadarCeeState state = this.structureStatus == RadarDisplayStructureResolver.StructureStatus.INVALID_MULTIPLE_CONTROLLERS
                ? PowerRadarCeeState.INVALID_STRUCTURE
                : electricalState();
        return RadarMonitorDisplayBuilder.noLink(
                controllerPos,
                monitorFacing,
                serverGameTime,
                status,
                state,
                electricalVoltageVolts(),
                electricalResistanceOhms(),
                activeDisplayCount(),
                this.activeSize,
                isRendererEnabled());
    }

    private SnapshotKey snapshotKey(ServerLevel level, BlockPos controllerPos) {
        refreshCachedSnapshotResolutionIfNeeded(level, controllerPos);
        UUID networkId = this.cachedSnapshotNetworkId;
        RadarNetworkConnectionStatus connectionStatus = this.cachedSnapshotConnectionStatus;
        BlockPos controllerBlockPos = null;
        long controllerScanTime = Long.MIN_VALUE;
        long controllerDisplayRevision = 0L;
        long networkRevision = 0L;
        if (networkId != null) {
            networkRevision = RadarNetworkManager.get(level.getServer()).settingsRevision(networkId);
        }
        if (connectionStatus == RadarNetworkConnectionStatus.CONNECTED) {
            long revisionHash = 1L;
            for (RadarControllerBlockEntity controller : this.cachedSnapshotControllers) {
                if (controller == null || controller.isRemoved()) {
                    continue;
                }
                if (controllerBlockPos == null) {
                    controllerBlockPos = controller.getBlockPos();
                }
                controllerScanTime = Math.max(controllerScanTime, controller.lastScanGameTime());
                revisionHash = 31L * revisionHash + controller.displayRevision();
                revisionHash = 31L * revisionHash + controller.getBlockPos().asLong();
            }
            controllerDisplayRevision = revisionHash;
        }
        int onlinePlayersHash = onlinePlayersSnapshot(level).hash();
        return new SnapshotKey(
                snapshotRevision(
                        this.localSnapshotRevision,
                        this.structureRevision,
                        networkRevision,
                        controllerScanTime,
                        controllerDisplayRevision,
                        onlinePlayersHash,
                        networkId,
                        controllerBlockPos,
                        connectionStatus),
                this.localSnapshotRevision,
                this.structureRevision,
                this.electricalState,
                this.cachedElectricalVoltageVolts,
                this.cachedElectricalResistanceOhms,
                this.activeOrigin,
                this.activeSize,
                this.activeFacing,
                this.structureStatus,
                networkId,
                connectionStatus,
                controllerBlockPos,
                controllerScanTime,
                controllerDisplayRevision,
                networkRevision,
                onlinePlayersHash);
    }

    private void refreshCachedSnapshotResolutionIfNeeded(ServerLevel level, BlockPos controllerPos) {
        long gameTime = level.getGameTime();
        boolean monitorOnSable = RadarWorldPoseResolver.isOnSableStructure(level, controllerPos);
        if (!monitorOnSable
                && !this.dynamicSnapshotResolution
                && this.cachedSnapshotResolutionGameTime != Long.MIN_VALUE
                && gameTime - this.cachedSnapshotResolutionGameTime < 20L
                && !hasRemovedCachedSnapshotController()) {
            return;
        }
        UUID directNetworkId = directNetworkId();
        if (directNetworkId != null) {
            RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
            GlobalPos consumerPos = GlobalPos.of(level.dimension(), controllerPos);
            RadarNetworkManager.ControllersResolution resolution = networkManager
                    .resolveControllersForConsumer(directNetworkId, consumerPos);
            this.cachedSnapshotNetworkId = directNetworkId;
            this.cachedSnapshotLinkPos = consumerPos;
            this.cachedSnapshotControllers = resolution.controllers();
            this.cachedSnapshotConnectionStatus = resolution.status();
            this.cachedSnapshotResolutionGameTime = gameTime;
            this.dynamicSnapshotResolution = monitorOnSable || this.cachedSnapshotControllers.stream()
                    .anyMatch(controller -> controller.worldPoseAt(gameTime).onSableStructure());
            return;
        }
        RadarLinkConnectionResolver.Resolution linkResolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(level, controllerPos);
        UUID networkId = linkResolution.link() == null ? null : linkResolution.link().networkId();
        this.cachedSnapshotNetworkId = null;
        this.cachedSnapshotLinkPos = null;
        this.cachedSnapshotControllers = List.of();
        this.cachedSnapshotConnectionStatus = RadarNetworkConnectionStatus.NO_LINK;
        this.cachedSnapshotResolutionGameTime = gameTime;
        if (linkResolution.status() == RadarLinkConnectionResolver.Status.AMBIGUOUS) {
            this.cachedSnapshotConnectionStatus = RadarNetworkConnectionStatus.AMBIGUOUS_LINKS;
        } else if (linkResolution.status() == RadarLinkConnectionResolver.Status.SINGLE && networkId != null) {
            RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
            GlobalPos consumerLinkPos = GlobalPos.of(level.dimension(), linkResolution.link().getBlockPos());
            RadarNetworkManager.ControllersResolution controllerResolution = networkManager
                    .resolveControllersForConsumer(
                            networkId,
                            consumerLinkPos);
            this.cachedSnapshotNetworkId = networkId;
            this.cachedSnapshotLinkPos = consumerLinkPos;
            this.cachedSnapshotControllers = controllerResolution.controllers();
            this.cachedSnapshotConnectionStatus = controllerResolution.status();
            if (monitorOnSable || this.cachedSnapshotControllers.stream()
                    .anyMatch(controller -> controller.worldPoseAt(gameTime).onSableStructure())) {
                this.dynamicSnapshotResolution = true;
            }
        }
    }

    @Nullable
    protected UUID directNetworkId() { return null; }

    protected boolean usesDisplayStructureResolver() { return true; }

    private boolean hasRemovedCachedSnapshotController() {
        for (RadarControllerBlockEntity controller : this.cachedSnapshotControllers) {
            if (controller == null || controller.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    private static long snapshotRevision(
            long localRevision,
            int structureRevision,
            long networkRevision,
            long controllerScanTime,
            long controllerDisplayRevision,
            int onlinePlayersHash,
            @Nullable UUID networkId,
            @Nullable BlockPos controllerPos,
            RadarNetworkConnectionStatus connectionStatus
    ) {
        int revision = 1;
        revision = 31 * revision + Long.hashCode(localRevision);
        revision = 31 * revision + Integer.hashCode(structureRevision);
        revision = 31 * revision + Long.hashCode(networkRevision);
        revision = 31 * revision + Long.hashCode(controllerScanTime);
        revision = 31 * revision + Long.hashCode(controllerDisplayRevision);
        revision = 31 * revision + Integer.hashCode(onlinePlayersHash);
        revision = 31 * revision + Objects.hashCode(networkId);
        revision = 31 * revision + Objects.hashCode(controllerPos);
        revision = 31 * revision + Objects.hashCode(connectionStatus);
        return revision;
    }

    private static OnlinePlayersSnapshot onlinePlayersSnapshot(ServerLevel level) {
        return ONLINE_PLAYERS_CACHE
                .computeIfAbsent(level.getServer(), ignored -> new OnlinePlayersCache())
                .snapshot(level);
    }

    private void invalidateSnapshotCache() {
        this.localSnapshotRevision++;
        this.cachedSnapshot = null;
        this.cachedSnapshotKey = null;
        this.cachedSnapshotResolutionGameTime = Long.MIN_VALUE;
    }

    public BlockPos anchorPos() {
        return this.worldPosition.relative(this.facing());
    }

    public RadarDisplayStructureResolver.StructureStatus structureStatus() {
        return this.structureStatus;
    }

    @Nullable
    public BlockPos activeOrigin() {
        return this.activeOrigin;
    }

    public int activeSize() {
        return this.activeSize;
    }

    public Direction activeFacing() {
        return this.activeFacing;
    }

    public int structureRevision() {
        return this.structureRevision;
    }

    public List<BlockPos> activePanelPositions() {
        if (this.activeOrigin == null || this.activeSize <= 0) {
            return List.of();
        }
        return RadarDisplayStructureResolver.squarePositions(this.activeOrigin, this.activeFacing, this.activeSize);
    }

    @Override
    public AABB getRenderBoundingBox() {
        List<BlockPos> positions = activePanelPositions();
        if (positions.isEmpty()) {
            return super.getRenderBoundingBox();
        }
        int minX = this.worldPosition.getX();
        int minY = this.worldPosition.getY();
        int minZ = this.worldPosition.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D).inflate(1.0D);
    }

    public boolean activeContains(BlockPos pos) {
        return this.activeOrigin != null
                && RadarDisplayStructureResolver.squareContains(this.activeOrigin, this.activeFacing, this.activeSize, pos);
    }

    public boolean lastStructureContains(BlockPos pos) {
        return activeContains(pos);
    }

    public Optional<RadarDisplayStructure> activeStructure() {
        if (this.activeOrigin == null || this.activeSize <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RadarDisplayStructure(
                this.activeOrigin,
                this.activeSize,
                this.activeFacing,
                java.util.Set.copyOf(this.activePanelPositions())
        ));
    }

    public void updateDisplayStructure(
            @Nullable BlockPos origin,
            int size,
            Direction facing,
            RadarDisplayStructureResolver.StructureStatus status
    ) {
        if (this.removingOrUnloading) {
            return;
        }
        boolean changed = this.activeOrigin == null ? origin != null : !this.activeOrigin.equals(origin);
        changed = changed || this.activeSize != size || this.activeFacing != facing || this.structureStatus != status;
        this.activeOrigin = origin;
        this.activeSize = size;
        this.activeFacing = facing;
        this.structureStatus = status;
        if (changed) {
            this.structureRevision++;
            invalidateSnapshotCache();
            updateElectricalStateAndLoad();
            requestLeaseReconcile("structure");
            syncChanged();
            if (this.level instanceof ServerLevel serverLevel) {
                sendBlockSnapshotToNearby(serverLevel);
            }
        }
    }

    public double calculateElectricalResistanceOhms() {
        return electricalResistanceOhms();
    }

    public double electricalResistanceOhms() {
        return this.cachedElectricalResistanceOhms;
    }

    public double electricalVoltageVolts() {
        return this.cachedElectricalVoltageVolts;
    }

    public double electricalCurrentAmps() {
        return this.cachedElectricalCurrentAmps;
    }

    public double electricalPowerWatts() {
        return this.cachedElectricalPowerWatts;
    }

    public int activeDisplayCount() {
        return hasValidDisplayStructure() ? this.activeSize * this.activeSize : 0;
    }

    public PowerRadarCeeState electricalState() {
        return this.electricalState;
    }

    public boolean isElectricallyOperational() {
        return this.electricalState == PowerRadarCeeState.POWERED;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.radar_monitor_controller")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("power_radar.electrical.state",
                Component.translatable(this.electricalState.translationKey())));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
        tooltip.add(Component.translatable("power_radar.electrical.display_count", activeDisplayCount()));
        return true;
    }

    public boolean isRendererEnabled() {
        return hasValidDisplayStructure() && isElectricallyOperational();
    }

    public boolean canHoldConsumerLease() {
        return canHoldLeaseNow();
    }

    protected boolean hasValidDisplayStructure() {
        return this.structureStatus == RadarDisplayStructureResolver.StructureStatus.ACTIVE && this.activeSize > 0;
    }

    private boolean hasDisplayStructureCache() {
        return this.activeOrigin != null && this.activeSize > 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.activeOrigin != null) {
            tag.putInt("ActiveOriginX", this.activeOrigin.getX());
            tag.putInt("ActiveOriginY", this.activeOrigin.getY());
            tag.putInt("ActiveOriginZ", this.activeOrigin.getZ());
        }
        tag.putInt("ActiveSize", this.activeSize);
        tag.putString("ActiveFacing", this.activeFacing.getName());
        tag.putString("StructureStatus", this.structureStatus.name());
        tag.putInt("StructureRevision", this.structureRevision);
        tag.putString("ElectricalState", this.electricalState.name());
        tag.putDouble("ElectricalVoltageVolts", this.cachedElectricalVoltageVolts);
        tag.putDouble("ElectricalCurrentAmps", this.cachedElectricalCurrentAmps);
        tag.putDouble("ElectricalPowerWatts", this.cachedElectricalPowerWatts);
        tag.putDouble("ElectricalResistanceOhms", this.cachedElectricalResistanceOhms);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.activeOrigin = tag.contains("ActiveOriginX")
                ? new BlockPos(tag.getInt("ActiveOriginX"), tag.getInt("ActiveOriginY"), tag.getInt("ActiveOriginZ"))
                : null;
        this.activeSize = tag.getInt("ActiveSize");
        Direction facing = Direction.byName(tag.getString("ActiveFacing"));
        this.activeFacing = facing == null ? Direction.NORTH : facing;
        try {
            this.structureStatus = RadarDisplayStructureResolver.StructureStatus.valueOf(tag.getString("StructureStatus"));
        } catch (IllegalArgumentException exception) {
            this.structureStatus = RadarDisplayStructureResolver.StructureStatus.NO_DISPLAY;
        }
        this.structureRevision = tag.getInt("StructureRevision");
        try {
            this.electricalState = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            this.electricalState = hasValidDisplayStructure() ? PowerRadarCeeState.UNDERVOLTAGE : PowerRadarCeeState.INVALID_STRUCTURE;
        }
        this.cachedElectricalVoltageVolts = safeSignedElectrical(tag.getDouble("ElectricalVoltageVolts"));
        this.cachedElectricalCurrentAmps = safeElectrical(tag.getDouble("ElectricalCurrentAmps"));
        this.cachedElectricalPowerWatts = safeElectrical(tag.getDouble("ElectricalPowerWatts"));
        this.cachedElectricalResistanceOhms = tag.contains("ElectricalResistanceOhms")
                ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ElectricalResistanceOhms"))
                : PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
        if (this.level == null || !this.level.isClientSide()) {
            updateElectricalStateAndLoad();
            invalidateSnapshotCache();
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private static Direction facingFromState(BlockState state) {
        return state.hasProperty(RadarMonitorControllerBlock.FACING)
                ? state.getValue(RadarMonitorControllerBlock.FACING)
                : Direction.NORTH;
    }

    private void syncChanged() {
        if (this.removingOrUnloading) {
            return;
        }
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    private void updateElectricalStateAndLoad() {
        if (this.removingOrUnloading) {
            return;
        }
        if (this.level instanceof ServerLevel serverLevel) {
            PowerRadarCeeIntegration.configureMonitorLoad(serverLevel, this.worldPosition, hasValidDisplayStructure(), activeDisplayCount());
        }
    }

    private void runDeferredLifecycleWork(Level level, BlockPos pos) {
        if (this.removingOrUnloading) {
            this.needsStructureReconcile = false;
            this.needsLeaseReconcile = false;
            return;
        }
        if (this.startupSafetyTicks > 0) {
            this.startupSafetyTicks--;
            return;
        }
        if (this.needsStructureReconcile) {
            this.needsStructureReconcile = false;
            RadarDisplayStructureResolver.reconcileAround(level, pos, "deferred-startup");
        }
        if (this.needsLeaseReconcile && canHoldLeaseNow()) {
            this.needsLeaseReconcile = false;
            reconcileConsumerLease("deferred", false);
        }
    }

    private void requestLeaseReconcile(String reason) {
        if (this.removingOrUnloading) {
            this.needsLeaseReconcile = false;
            return;
        }
        this.needsLeaseReconcile = true;
        if (canHoldLeaseNow()) {
            reconcileConsumerLease(reason, false);
            this.needsLeaseReconcile = false;
        }
    }

    private boolean canHoldLeaseNow() {
        return !this.removingOrUnloading && isRendererEnabled();
    }

    private void reconcileConsumerLease(String reason, boolean releaseOnly) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        RadarLinkConnectionResolver.Resolution linkResolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(serverLevel, this.worldPosition);
        if (linkResolution.status() != RadarLinkConnectionResolver.Status.SINGLE
                || linkResolution.link().networkId() == null) {
            return;
        }
        UUID networkId = linkResolution.link().networkId();
        GlobalPos linkPos = GlobalPos.of(serverLevel.dimension(), linkResolution.link().getBlockPos());
        if (releaseOnly) {
            this.cachedConsumerLeaseNetworkId = null;
            this.cachedConsumerLeaseLinkPos = null;
            RadarNetworkManager.get(serverLevel.getServer()).releaseMonitorConsumerLease(
                    networkId,
                    linkPos
            );
        } else {
            this.cachedConsumerLeaseNetworkId = networkId;
            this.cachedConsumerLeaseLinkPos = linkPos;
            RadarNetworkManager.get(serverLevel.getServer()).reconcileMonitorConsumerLease(
                    networkId,
                    linkPos
            );
        }
    }

    private void releaseCachedConsumerLease(String reason) {
        if (!(this.level instanceof ServerLevel serverLevel)
                || this.cachedConsumerLeaseNetworkId == null
                || this.cachedConsumerLeaseLinkPos == null) {
            return;
        }
        RadarNetworkManager.get(serverLevel.getServer()).releaseMonitorConsumerLease(
                this.cachedConsumerLeaseNetworkId,
                this.cachedConsumerLeaseLinkPos
        );
        this.cachedConsumerLeaseNetworkId = null;
        this.cachedConsumerLeaseLinkPos = null;
    }

    public boolean applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        double previousVoltage = this.cachedElectricalVoltageVolts;
        double previousCurrent = this.cachedElectricalCurrentAmps;
        double previousPower = this.cachedElectricalPowerWatts;
        double previousResistance = this.cachedElectricalResistanceOhms;
        PowerRadarCeeState previousState = this.electricalState;
        double voltage = safeSignedElectrical(snapshot.voltageVolts());
        double current = safeElectrical(snapshot.currentAmps());
        double resistance = PowerRadarCeeConstants.sanitizeResistance(snapshot.resistanceOhms());
        double snapshotPower = safeElectrical(snapshot.powerWatts());
        double power = snapshotPower > 0.0
                ? snapshotPower
                : PowerRadarCeeConstants.powerWatts(voltage, resistance);
        this.cachedElectricalVoltageVolts = voltage;
        this.cachedElectricalCurrentAmps = current;
        this.cachedElectricalResistanceOhms = resistance;
        this.cachedElectricalPowerWatts = power;
        this.electricalState = snapshot.electricalState();
        boolean changed = Math.abs(previousVoltage - voltage) > 0.01
                || Math.abs(previousCurrent - current) > 0.001
                || Math.abs(previousPower - power) > 0.1
                || Math.abs(previousResistance - resistance) > 0.001
                || previousState != this.electricalState;
        if (changed) {
            invalidateSnapshotCache();
            if (previousState != this.electricalState) {
                if (this.electricalState != PowerRadarCeeState.POWERED) {
                    releaseCachedConsumerLease("power-loss");
                } else {
                    requestLeaseReconcile("power-restored");
                }
            }
            syncChanged();
        }
        return changed;
    }

    private static double safeElectrical(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double safeSignedElectrical(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private record SnapshotKey(
            long revision,
            long localRevision,
            int structureRevision,
            PowerRadarCeeState electricalState,
            double voltage,
            double resistance,
            @Nullable BlockPos activeOrigin,
            int activeSize,
            Direction activeFacing,
            RadarDisplayStructureResolver.StructureStatus structureStatus,
            @Nullable UUID networkId,
            RadarNetworkConnectionStatus connectionStatus,
            @Nullable BlockPos controllerPos,
            long controllerScanTime,
            long controllerDisplayRevision,
            long networkRevision,
            int onlinePlayersHash
    ) {
    }

    private static final class OnlinePlayersCache {
        private long lastRefreshGameTime = Long.MIN_VALUE;
        private OnlinePlayersSnapshot snapshot = new OnlinePlayersSnapshot(List.of(), 1);

        private OnlinePlayersSnapshot snapshot(ServerLevel level) {
            long gameTime = level.getGameTime();
            if (gameTime - this.lastRefreshGameTime < 20L) {
                return this.snapshot;
            }
            List<String> names = level.getServer().getPlayerList().getPlayers().stream()
                    .map(player -> player.getGameProfile().getName())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            int hash = 1;
            for (String name : names) {
                hash = 31 * hash + name.toLowerCase(java.util.Locale.ROOT).hashCode();
            }
            this.snapshot = new OnlinePlayersSnapshot(List.copyOf(names), hash);
            this.lastRefreshGameTime = gameTime;
            return this.snapshot;
        }
    }

    private record OnlinePlayersSnapshot(List<String> names, int hash) {
    }
}
