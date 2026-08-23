package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarDisplayStructure;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarDisplayPanelAttachment;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarPanelMonitorRuntime;
import com.limbo2136.powerradar.network.RadarMonitorBlockStaticPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorPosePayloadFactory;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPose;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayBuilder;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.OnlinePlayersSnapshotCache;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.RadarNetworkMember;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class AbstractRadarMonitorBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, RadarNetworkMember {
    private static final int NEARBY_PLAYERS_CACHE_TICKS = 20;

    @Nullable
    private BlockPos activeOrigin;
    private int activeSize;
    private int activeWidth;
    private int activeHeight;
    private Direction activeFacing = Direction.NORTH;
    private RadarDisplayStructureResolver.StructureStatus structureStatus =
            RadarDisplayStructureResolver.StructureStatus.NO_DISPLAY;
    private int structureRevision;
    private int ticksSinceSync;
    private long lastSentBlockSnapshotRevision = Long.MIN_VALUE;
    private long lastSentBlockStaticRevision = Long.MIN_VALUE;
    private long lastSentBlockStaticGameTime = Long.MIN_VALUE;
    private long lastSentBlockSnapshotScanGameTime = Long.MIN_VALUE;
    private boolean removingOrUnloading;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
    private double cachedElectricalVoltageVolts;
    private double cachedElectricalCurrentAmps;
    private double cachedElectricalPowerWatts;
    private double cachedElectricalResistanceOhms = PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
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
    private boolean movingPosePublished;
    protected AbstractRadarMonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.activeFacing = facingFromState(blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.removingOrUnloading = false;
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
        RadarNetworkNodeClientCacheBridge.onLoaded(
                this.level, this.worldPosition, radarNetworkId());
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.removingOrUnloading = false;
    }

    public void prepareForBlockRemoval() {
        this.removingOrUnloading = true;
        invalidateSnapshotCache();
    }

    @Override
    public void onChunkUnloaded() {
        this.removingOrUnloading = true;
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        this.removingOrUnloading = true;
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AbstractRadarMonitorBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }
        if (blockEntity.removingOrUnloading) {
            return;
        }

        blockEntity.tick();
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
        // Дельта позы намеренно отправляется последней: в тик сканирования она должна
        // перекрыть позу покрытия из более редкого статического снимка.
        blockEntity.sendMovingPoses(serverLevel, pos);
    }

    private void sendMovingPoses(ServerLevel serverLevel, BlockPos monitorPos) {
        List<ServerPlayer> players = nearbyPlayers(serverLevel);
        if (players.isEmpty()) {
            return;
        }
        refreshCachedSnapshotResolutionIfNeeded(serverLevel, monitorPos);
        RadarMonitorBlockPosePayload payload = RadarMonitorPosePayloadFactory.create(
                serverLevel,
                monitorPos,
                facingFromState(getBlockState()),
                this.cachedSnapshotControllers);
        if (payload == null) {
            if (!this.movingPosePublished) {
                return;
            }
            payload = new RadarMonitorBlockPosePayload(
                    monitorPos, serverLevel.getGameTime(), null, List.of());
        }
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        this.movingPosePublished = payload.monitorPose() != null || !payload.poses().isEmpty();
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
            // Статический пакет намеренно не содержит целей: клиент сохраняет прежний список
            // до следующего target-пакета и не создаёт однокадровое мерцание.
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

    public static RadarMonitorSnapshotPayload createSnapshotPayload(ServerLevel level, BlockPos monitorPos) {
        return getOrCreateSnapshotPayload(level, monitorPos);
    }

    public static RadarMonitorSnapshotPayload getOrCreateSnapshotPayload(ServerLevel level, BlockPos monitorPos) {
        BlockEntity blockEntity = level.getBlockEntity(monitorPos);
        if (blockEntity instanceof RadarDisplayBlockEntity display && !display.isRoot()) {
            RadarDisplayBlockEntity root = display.loadedRoot();
            if (root != null) {
                blockEntity = root;
                monitorPos = root.getBlockPos();
            }
        }
        if (blockEntity instanceof AbstractRadarMonitorBlockEntity monitor) {
            SnapshotKey key = monitor.snapshotKey(level, monitorPos);
            if (monitor.cachedSnapshot != null && Objects.equals(monitor.cachedSnapshotKey, key)) {
                return monitor.cachedSnapshot;
            }
            RadarMonitorSnapshotPayload snapshot = buildSnapshotPayload(level, monitorPos, monitor, key.revision());
            monitor.cachedSnapshot = snapshot;
            monitor.cachedSnapshotKey = key;
            return snapshot;
        }
        RadarDisplayPanelAttachment panelDisplay = RadarPanelMonitorRuntime.findDisplay(level, monitorPos);
        if (panelDisplay != null) {
            return RadarPanelMonitorRuntime.createSnapshot(level, monitorPos, panelDisplay);
        }
        return buildSnapshotPayload(level, monitorPos, null, level.getGameTime());
    }

    public int displayLinkTargetCount(ServerLevel level) {
        return getOrCreateSnapshotPayload(level, this.worldPosition).displayedTargetCount();
    }

    @Nullable
    public UUID displayLinkNetworkId(ServerLevel level) {
        AbstractRadarMonitorBlockEntity owner = this;
        if (this instanceof RadarDisplayBlockEntity display && !display.isRoot()) {
            RadarDisplayBlockEntity root = display.loadedRoot();
            if (root != null) {
                owner = root;
            }
        }
        RadarMonitorSnapshotPayload snapshot = getOrCreateSnapshotPayload(level, owner.worldPosition);
        return snapshot.linked() ? owner.cachedSnapshotNetworkId : null;
    }

    private static RadarMonitorSnapshotPayload buildSnapshotPayload(
            ServerLevel level,
            BlockPos monitorPos,
            @Nullable AbstractRadarMonitorBlockEntity monitor,
            long revision
    ) {
        Direction monitorFacing = level.getBlockState(monitorPos).hasProperty(HorizontalDirectionalBlock.FACING)
                ? level.getBlockState(monitorPos).getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        if (monitor == null) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    RadarMonitorDisplayBuilder.noLink(monitorPos, monitorFacing, level.getGameTime()), revision);
        }
        if (monitor.structureStatus != RadarDisplayStructureResolver.StructureStatus.ACTIVE
                || monitor.activeSize <= 0) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitor.noLinkDisplayData(monitorPos, monitorFacing, level.getGameTime(), RadarNetworkConnectionStatus.NO_LINK),
                    revision);
        }
        if (!monitor.isRendererEnabled()) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitor.noLinkDisplayData(monitorPos, monitorFacing, level.getGameTime(), RadarNetworkConnectionStatus.NO_LINK),
                    revision);
        }

        if (monitor.cachedSnapshotNetworkId == null
                || monitor.cachedSnapshotLinkPos == null) {
            RadarNetworkConnectionStatus status = monitor.cachedSnapshotConnectionStatus;
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitor.noLinkDisplayData(monitorPos, monitorFacing, level.getGameTime(), status),
                    revision);
        }

        RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
        UUID networkId = monitor.cachedSnapshotNetworkId;
        List<RadarControllerBlockEntity> controllers = monitor.cachedSnapshotControllers;
        if (monitor.hasRemovedCachedSnapshotController()) {
            controllers = activeControllers(controllers);
        }
        if (controllers.isEmpty()
                || monitor.cachedSnapshotConnectionStatus != RadarNetworkConnectionStatus.CONNECTED) {
            return RadarMonitorSnapshotPayload.fromDisplayData(
                    monitor.noLinkDisplayData(
                            monitorPos,
                            monitorFacing,
                            level.getGameTime(),
                            monitor.cachedSnapshotConnectionStatus),
                    revision);
        }
        OnlinePlayersSnapshotCache.Snapshot onlinePlayers = OnlinePlayersSnapshotCache.snapshot(level);
        RadarMonitorDisplayData displayData = networkManager.displayDataForConsumer(
                        networkId,
                        monitor.cachedSnapshotLinkPos,
                        monitorPos,
                        monitorFacing,
                        controllers,
                        level.getGameTime(),
                        monitor.electricalState(),
                        monitor.electricalVoltageVolts(),
                        monitor.electricalResistanceOhms(),
                        monitor.activeDisplayCount(),
                        monitor.activeSize(),
                        monitor.isRendererEnabled(),
                        onlinePlayers.hash(),
                        onlinePlayers.names());
        return RadarMonitorSnapshotPayload.fromDisplayData(displayData, revision);
    }

    public Direction facing() {
        return this.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? this.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : this.activeFacing;
    }

    private RadarMonitorDisplayData noLinkDisplayData(
            BlockPos monitorPos,
            Direction monitorFacing,
            long serverGameTime,
            RadarNetworkConnectionStatus status
    ) {
        PowerRadarCeeState state = this.structureStatus == RadarDisplayStructureResolver.StructureStatus.INVALID_MULTIPLE_CONTROLLERS
                ? PowerRadarCeeState.INVALID_STRUCTURE
                : electricalState();
        return RadarMonitorDisplayBuilder.noLink(
                monitorPos,
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

    private SnapshotKey snapshotKey(ServerLevel level, BlockPos monitorPos) {
        refreshCachedSnapshotResolutionIfNeeded(level, monitorPos);
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
        int onlinePlayersHash = OnlinePlayersSnapshotCache.snapshot(level).hash();
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

    private void refreshCachedSnapshotResolutionIfNeeded(ServerLevel level, BlockPos monitorPos) {
        long gameTime = level.getGameTime();
        boolean monitorOnSable = RadarWorldPoseResolver.isOnSableStructure(level, monitorPos);
        if (!monitorOnSable
                && !this.dynamicSnapshotResolution
                && this.cachedSnapshotResolutionGameTime != Long.MIN_VALUE
                && gameTime - this.cachedSnapshotResolutionGameTime < 20L
                && !hasRemovedCachedSnapshotController()) {
            return;
        }
        UUID directNetworkId = directNetworkId();
        if (directNetworkId != null) {
            // Onboard Computer является прямым потребителем собственной сети и не ищет Radar Link.
            RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
            GlobalPos consumerPos = GlobalPos.of(level.dimension(), monitorPos);
            RadarNetworkManager.ControllersResolution resolution = networkManager
                    .resolveControllersForConsumer(directNetworkId, consumerPos);
            this.cachedSnapshotNetworkId = directNetworkId;
            this.cachedSnapshotLinkPos = consumerPos;
            this.cachedSnapshotControllers = resolution.controllers();
            this.cachedSnapshotConnectionStatus = resolution.status();
            this.cachedSnapshotResolutionGameTime = gameTime;
            this.dynamicSnapshotResolution = monitorOnSable
                    || hasControllerOnSableStructure(this.cachedSnapshotControllers, gameTime);
            return;
        }
        this.cachedSnapshotNetworkId = null;
        this.cachedSnapshotLinkPos = null;
        this.cachedSnapshotControllers = List.of();
        this.cachedSnapshotConnectionStatus = RadarNetworkConnectionStatus.NO_LINK;
        this.cachedSnapshotResolutionGameTime = gameTime;
    }

    @Nullable
    protected abstract UUID directNetworkId();

    @Override
    @Nullable
    public UUID radarNetworkId() {
        return directNetworkId();
    }

    @Override
    public abstract void setRadarNetworkId(@Nullable UUID networkId);

    protected List<RadarControllerBlockEntity> resolvedRadarControllers() {
        return this.cachedSnapshotControllers;
    }

    private boolean hasRemovedCachedSnapshotController() {
        for (RadarControllerBlockEntity controller : this.cachedSnapshotControllers) {
            if (controller == null || controller.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    private static List<RadarControllerBlockEntity> activeControllers(
            List<RadarControllerBlockEntity> controllers
    ) {
        ArrayList<RadarControllerBlockEntity> active = new ArrayList<>(controllers.size());
        for (RadarControllerBlockEntity controller : controllers) {
            if (controller != null && !controller.isRemoved()) {
                active.add(controller);
            }
        }
        return List.copyOf(active);
    }

    private static boolean hasControllerOnSableStructure(
            List<RadarControllerBlockEntity> controllers,
            long gameTime
    ) {
        for (RadarControllerBlockEntity controller : controllers) {
            if (controller != null
                    && !controller.isRemoved()
                    && controller.worldPoseAt(gameTime).onSableStructure()) {
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
            @Nullable BlockPos monitorPos,
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
        revision = 31 * revision + Objects.hashCode(monitorPos);
        revision = 31 * revision + Objects.hashCode(connectionStatus);
        return revision;
    }

    protected void invalidateSnapshotCache() {
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

    public int activeWidth() {
        return this.activeWidth > 0 ? this.activeWidth : this.activeSize;
    }

    public int activeHeight() {
        return this.activeHeight > 0 ? this.activeHeight : this.activeSize;
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
        return RadarDisplayStructure.rectanglePositions(
                this.activeOrigin, this.activeFacing, activeWidth(), activeHeight());
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
                && RadarDisplayStructureResolver.rectangleContains(
                        this.activeOrigin, this.activeFacing, activeWidth(), activeHeight(), pos);
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
                activeWidth(),
                activeHeight(),
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
        updateDisplayStructure(origin, size, size, facing, status);
    }

    public void updateDisplayStructure(
            @Nullable BlockPos origin,
            int width,
            int height,
            Direction facing,
            RadarDisplayStructureResolver.StructureStatus status
    ) {
        if (this.removingOrUnloading) {
            return;
        }
        int size = Math.min(width, height);
        boolean changed = this.activeOrigin == null ? origin != null : !this.activeOrigin.equals(origin);
        changed = changed
                || this.activeSize != size
                || this.activeWidth != width
                || this.activeHeight != height
                || this.activeFacing != facing
                || this.structureStatus != status;
        this.activeOrigin = origin;
        this.activeSize = size;
        this.activeWidth = width;
        this.activeHeight = height;
        this.activeFacing = facing;
        this.structureStatus = status;
        if (changed) {
            this.structureRevision++;
            invalidateSnapshotCache();
            onDisplayStructureChanged();
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
        return hasValidDisplayStructure() ? activeWidth() * activeHeight() : 0;
    }

    public PowerRadarCeeState electricalState() {
        return this.electricalState;
    }

    public boolean isElectricallyOperational() {
        return this.electricalState == PowerRadarCeeState.POWERED;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int firstNewLine = tooltip.size();
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.RADAR_DISPLAY)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> PowerRadarTooltipSettings.appendElectricalStatisticsTitle(tooltip);
                case ELECTRICAL_STATE -> tooltip.add(Component.translatable("power_radar.electrical.state",
                        Component.translatable(this.electricalState.translationKey())));
                case VOLTAGE -> tooltip.add(Component.translatable("power_radar.electrical.voltage",
                        PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
                case POWER -> tooltip.add(Component.translatable("power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
                default -> { }
            }
        }
        return PowerRadarTooltipSettings.finishGoggleTooltip(tooltip, firstNewLine);
    }

    public boolean isRendererEnabled() {
        return hasValidDisplayStructure() && isElectricallyOperational();
    }

    protected boolean hasValidDisplayStructure() {
        return this.structureStatus == RadarDisplayStructureResolver.StructureStatus.ACTIVE && this.activeSize > 0;
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
        tag.putInt("ActiveWidth", activeWidth());
        tag.putInt("ActiveHeight", activeHeight());
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
        this.activeWidth = tag.contains("ActiveWidth") ? tag.getInt("ActiveWidth") : this.activeSize;
        this.activeHeight = tag.contains("ActiveHeight") ? tag.getInt("ActiveHeight") : this.activeSize;
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
                : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
        if (this.level == null || !this.level.isClientSide()) {
            onDisplayStructureChanged();
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
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
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

    protected void onDisplayStructureChanged() {
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
        double power = safeElectrical(snapshot.powerWatts());
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
            @Nullable BlockPos monitorPos,
            long controllerScanTime,
            long controllerDisplayRevision,
            long networkRevision,
            int onlinePlayersHash
    ) {
    }

}
