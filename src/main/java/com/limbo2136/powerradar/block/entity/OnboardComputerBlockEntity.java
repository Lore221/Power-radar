package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureNames;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.interception.MovingAabbThreatEvaluator;
import com.limbo2136.powerradar.item.NameCardItem;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OnboardComputerBlockEntity extends RadarMonitorControllerBlockEntity {
    private static final String NETWORK_TAG = "NetworkId";
    private static final String INTERCEPTION_NETWORK_TAG = "InterceptionNetworkId";
    private static final String CARD_TAG = "NameCard";
    private static final String ASSIGNED_STRUCTURE_TAG = "AssignedStructureId";
    @Nullable private UUID networkId;
    @Nullable private UUID interceptionNetworkId;
    @Nullable private UUID assignedStructureUuid;
    private ItemStack nameCard = ItemStack.EMPTY;
    @Nullable private UUID cachedShipStructureUuid;
    @Nullable private AABB cachedShipBounds;
    private Vec3 cachedShipVelocity = Vec3.ZERO;
    private Vec3 cachedShipAcceleration = Vec3.ZERO;
    private long cachedShipMotionGameTime = Long.MIN_VALUE;
    private boolean cachedShipVelocityInitialized;
    private boolean cachedShipAccelerationInitialized;
    private long lastProcessedThreatScanGameTime = Long.MIN_VALUE;
    private boolean alarmActive;
    private boolean publishedThreats;
    private boolean networkRoleEnsured;

    public OnboardComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ONBOARD_COMPUTER.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, OnboardComputerBlockEntity computer) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (computer.networkId == null) computer.createOwnNetwork(serverLevel);
        computer.ensureOnboardNetworkRole(serverLevel);
        if (computer.interceptionNetworkId == null) computer.createOwnInterceptionNetwork();
        computer.updateDisplayStructure(pos, 1, state.getValue(OnboardComputerBlock.FACING),
                RadarDisplayStructureResolver.StructureStatus.ACTIVE);
        RadarMonitorControllerBlockEntity.tick(serverLevel, pos, state, computer);
        computer.tickShellAlarm(serverLevel, state);
    }

    private void tickShellAlarm(ServerLevel level, BlockState state) {
        updateShipMotion(level);
        if (!isElectricallyOperational()
                || this.networkId == null
                || this.interceptionNetworkId == null
                || this.cachedShipBounds == null
                || resolvedRadarControllers().isEmpty()) {
            this.lastProcessedThreatScanGameTime = Long.MIN_VALUE;
            clearPublishedThreats(level);
            setAlarmActive(level, state, false);
            return;
        }

        CombinedRadarDataSource radar = new CombinedRadarDataSource(resolvedRadarControllers());
        long scanGameTime = radar.lastScanGameTime();
        long previousScanGameTime = this.lastProcessedThreatScanGameTime;
        if (scanGameTime == previousScanGameTime) {
            return;
        }
        this.lastProcessedThreatScanGameTime = scanGameTime;

        ServerLevel worldLevel = level.getServer().getLevel(level.dimension());
        if (worldLevel == null) {
            worldLevel = level;
        }
        ServerLevel projectileLevel = worldLevel;
        AABB shipBounds = this.cachedShipBounds;
        Vec3 shipVelocity = this.cachedShipVelocity;
        Vec3 shipAcceleration = this.cachedShipAccelerationInitialized
                ? this.cachedShipAcceleration
                : Vec3.ZERO;
        Vec3 alarmReference = shipBounds.getCenter();
        List<ThreatSnapshot> threats = new ArrayList<>();

        radar.forEachTrackedTargetBySource(TargetSourceType.CBC_BIG_CANNON_PROJECTILE, track -> {
            ThreatSnapshot threat = evaluateThreat(
                    projectileLevel,
                    alarmReference,
                    shipBounds,
                    shipVelocity,
                    shipAcceleration,
                    track);
            if (threat != null) {
                threats.add(threat);
            }
        });

        InterceptionCoordinator.publishThreats(
                projectileLevel,
                this.interceptionNetworkId,
                BlockPos.containing(alarmReference),
                threats,
                InterceptionCoordinator.threatTtlTicksForScanInterval(
                        radarScanIntervalTicks(scanGameTime, previousScanGameTime)));
        this.publishedThreats = !threats.isEmpty();
        setAlarmActive(level, state, !threats.isEmpty());
    }

    @Nullable
    private static ThreatSnapshot evaluateThreat(
            ServerLevel worldLevel,
            Vec3 alarmReference,
            AABB shipBounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            TrackedTargetView track
    ) {
        UUID targetUuid = track.targetUuid();
        if (targetUuid == null) {
            return null;
        }
        Entity liveProjectile = worldLevel.getEntity(targetUuid);
        Vec3 position = liveProjectile != null && liveProjectile.isAlive()
                ? liveProjectile.position()
                : track.position();
        Vec3 velocity = liveProjectile != null && liveProjectile.isAlive()
                ? liveProjectile.getDeltaMovement()
                : track.velocity();
        ShellAlarmCbcCompat.Ballistics ballistics = ShellAlarmCbcCompat.ballistics(liveProjectile);
        double projectileRadius = projectileRadius(track, liveProjectile);
        if (!MovingAabbThreatEvaluator.threatens(
                shipBounds,
                shipVelocity,
                shipAcceleration,
                position,
                velocity,
                projectileRadius,
                ballistics,
                PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS)) {
            return null;
        }
        return new ThreatSnapshot(
                targetUuid,
                worldLevel.dimension(),
                position,
                velocity,
                track.lastSeenGameTime(),
                ballistics.gravity(),
                ballistics.drag(),
                ballistics.quadraticDrag(),
                alarmReference,
                shipVelocity,
                shipAcceleration,
                worldLevel.getGameTime(),
                null,
                null);
    }

    private static double projectileRadius(TrackedTargetView track, @Nullable Entity projectile) {
        if (projectile != null) {
            AABB bounds = projectile.getBoundingBox();
            return Math.max(0.05D, Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5D);
        }
        double size = track.approximateSize();
        return Double.isFinite(size) ? Math.max(0.05D, size * 0.5D) : 0.05D;
    }

    private void updateShipMotion(ServerLevel level) {
        UUID structureUuid = SableRadarIntegration.containingStructureUuid(level, this.worldPosition).orElse(null);
        SableStructureObservation observation = structureUuid == null
                ? null
                : SableRadarIntegration.loadedStructure(level, structureUuid).orElse(null);
        if (observation == null) {
            resetShipMotion();
            return;
        }
        long gameTime = level.getGameTime();
        Vec3 velocity = observation.velocity();
        boolean reset = !structureUuid.equals(this.cachedShipStructureUuid)
                || this.cachedShipMotionGameTime == Long.MIN_VALUE
                || gameTime <= this.cachedShipMotionGameTime
                || gameTime - this.cachedShipMotionGameTime > 5L;
        if (reset) {
            this.cachedShipAcceleration = Vec3.ZERO;
            this.cachedShipVelocityInitialized = false;
            this.cachedShipAccelerationInitialized = false;
        } else if (this.cachedShipVelocityInitialized) {
            double elapsedTicks = gameTime - this.cachedShipMotionGameTime;
            Vec3 rawAcceleration = clampAcceleration(
                    velocity.subtract(this.cachedShipVelocity).scale(1.0D / elapsedTicks));
            this.cachedShipAcceleration = this.cachedShipAccelerationInitialized
                    ? lerp(this.cachedShipAcceleration, rawAcceleration, 0.65D)
                    : rawAcceleration;
            this.cachedShipAccelerationInitialized = true;
        }
        this.cachedShipStructureUuid = structureUuid;
        this.cachedShipBounds = observation.worldBounds();
        this.cachedShipVelocity = velocity;
        this.cachedShipVelocityInitialized = true;
        this.cachedShipMotionGameTime = gameTime;
    }

    private void resetShipMotion() {
        this.cachedShipStructureUuid = null;
        this.cachedShipBounds = null;
        this.cachedShipVelocity = Vec3.ZERO;
        this.cachedShipAcceleration = Vec3.ZERO;
        this.cachedShipMotionGameTime = Long.MIN_VALUE;
        this.cachedShipVelocityInitialized = false;
        this.cachedShipAccelerationInitialized = false;
    }

    private void setAlarmActive(ServerLevel level, BlockState state, boolean active) {
        if (this.alarmActive == active) {
            return;
        }
        this.alarmActive = active;
        Block block = state.getBlock();
        level.updateNeighborsAt(this.worldPosition, block);
        level.updateNeighbourForOutputSignal(this.worldPosition, block);
        setChanged();
    }

    private void clearPublishedThreats(ServerLevel level) {
        if (!this.publishedThreats || this.interceptionNetworkId == null) {
            return;
        }
        InterceptionCoordinator.clearPublishedThreats(
                level.getServer(), this.interceptionNetworkId, level.getGameTime());
        this.publishedThreats = false;
    }

    public void destroyOwnedNetworks() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        clearPublishedThreats(serverLevel);
        if (this.interceptionNetworkId != null) {
            InterceptionCoordinator.removeNetwork(serverLevel.getServer(), this.interceptionNetworkId);
        }
        if (this.networkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).cleanupEmptyNetwork(this.networkId);
        }
    }

    private static long radarScanIntervalTicks(long scanGameTime, long previousScanGameTime) {
        if (previousScanGameTime == Long.MIN_VALUE || scanGameTime <= previousScanGameTime) {
            return RadarConstants.radarScanUpdateIntervalTicks();
        }
        return scanGameTime - previousScanGameTime;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double factor) {
        return from.add(to.subtract(from).scale(factor));
    }

    private static Vec3 clampAcceleration(Vec3 acceleration) {
        double limit = 0.25D;
        return new Vec3(
                Math.clamp(acceleration.x, -limit, limit),
                Math.clamp(acceleration.y, -limit, limit),
                Math.clamp(acceleration.z, -limit, limit));
    }

    public boolean alarmActive() {
        return this.alarmActive;
    }

    public UUID ensureNetworkId() {
        if (this.networkId == null && this.level instanceof ServerLevel serverLevel) {
            createOwnNetwork(serverLevel);
        }
        return this.networkId;
    }

    @Nullable public UUID networkId() { return this.networkId; }

    public UUID ensureInterceptionNetworkId() {
        if (this.interceptionNetworkId == null && this.level instanceof ServerLevel) {
            createOwnInterceptionNetwork();
        }
        return this.interceptionNetworkId;
    }

    @Nullable
    public UUID interceptionNetworkId() {
        return this.interceptionNetworkId;
    }

    private void createOwnNetwork(ServerLevel serverLevel) {
        if (this.networkId != null) return;
        createNewOwnNetwork(serverLevel);
    }

    public void createNewOwnNetwork(ServerLevel serverLevel) {
        UUID id = RadarNetworkManager.get(serverLevel.getServer()).createOnboardNetwork();
        UUID oldId = this.networkId;
        this.networkId = id;
        this.networkRoleEnsured = true;
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(this.level, this.worldPosition, oldId, id);
        setChanged();
        sendData();
    }

    private void ensureOnboardNetworkRole(ServerLevel level) {
        if (this.networkId == null) {
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(level.getServer());
        if (this.networkRoleEnsured
                && manager.networkExists(this.networkId)
                && !manager.controlConsumersAllowed(this.networkId)) {
            return;
        }
        manager.setControlConsumersAllowed(this.networkId, false);
        this.networkRoleEnsured = true;
    }

    private void createOwnInterceptionNetwork() {
        if (this.interceptionNetworkId != null) {
            return;
        }
        UUID oldId = this.interceptionNetworkId;
        this.interceptionNetworkId = UUID.randomUUID();
        InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldId, this.interceptionNetworkId);
        setChanged();
        sendData();
    }

    @Override public void onLoad() {
        super.onLoad();
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
        InterceptionNetworkNodeClientCacheBridge.onLoaded(
                this.level, this.worldPosition, this.interceptionNetworkId);
        refreshStructureName();
    }

    @Override public void clearRemoved() {
        super.clearRemoved();
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
        InterceptionNetworkNodeClientCacheBridge.onLoaded(
                this.level, this.worldPosition, this.interceptionNetworkId);
    }

    @Override public void remove() {
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }

    @Override public void onChunkUnloaded() {
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    public ItemStack nameCard() { return this.nameCard; }

    public boolean insertNameCard(ItemStack held) {
        if (!this.nameCard.isEmpty() || !(held.getItem() instanceof NameCardItem)) return false;
        this.nameCard = held.copyWithCount(1);
        held.shrink(1);
        setChanged();
        refreshStructureName();
        sendData();
        return true;
    }

    public ItemStack removeNameCard() {
        ItemStack result = this.nameCard;
        this.nameCard = ItemStack.EMPTY;
        setChanged();
        refreshStructureName();
        sendData();
        return result;
    }

    public void clearStructureName() {
        if (this.assignedStructureUuid != null && this.level instanceof ServerLevel serverLevel) {
            SableStructureNames.remove(serverLevel.getServer(), this.assignedStructureUuid);
            this.assignedStructureUuid = null;
            setChanged();
        }
    }

    private void refreshStructureName() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        String name = NameCardItem.name(this.nameCard).trim();
        UUID desiredStructure = isElectricallyOperational() && !name.isEmpty()
                ? SableRadarIntegration.containingStructureUuid(serverLevel, this.worldPosition).orElse(null)
                : null;
        if (this.assignedStructureUuid != null && !this.assignedStructureUuid.equals(desiredStructure)) {
            SableStructureNames.remove(serverLevel.getServer(), this.assignedStructureUuid);
            this.assignedStructureUuid = null;
        }
        if (desiredStructure != null) {
            SableStructureNames.assign(serverLevel.getServer(), desiredStructure, name);
            this.assignedStructureUuid = desiredStructure;
        }
        setChanged();
    }

    @Override public boolean applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        boolean wasPowered = isElectricallyOperational();
        boolean changed = super.applyElectricalSnapshot(snapshot);
        if (wasPowered != isElectricallyOperational()) refreshStructureName();
        return changed;
    }

    @Override public void updateDisplayStructure(@Nullable BlockPos origin, int size,
            net.minecraft.core.Direction facing, RadarDisplayStructureResolver.StructureStatus status) {
        PowerRadarCeeState previousState = electricalState();
        super.updateDisplayStructure(origin, size, facing, status);
        if (previousState != electricalState()) refreshStructureName();
    }

    @Override protected UUID directNetworkId() { return this.networkId; }
    @Override protected boolean usesDisplayStructureResolver() { return false; }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.onboard_computer")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("power_radar.electrical.state",
                Component.translatable(electricalState().translationKey())));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.networkId != null) tag.putUUID(NETWORK_TAG, this.networkId);
        if (this.interceptionNetworkId != null) {
            tag.putUUID(INTERCEPTION_NETWORK_TAG, this.interceptionNetworkId);
        }
        if (this.assignedStructureUuid != null) tag.putUUID(ASSIGNED_STRUCTURE_TAG, this.assignedStructureUuid);
        if (!this.nameCard.isEmpty()) tag.put(CARD_TAG, this.nameCard.save(registries));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        UUID oldNetworkId = this.networkId;
        UUID oldInterceptionNetworkId = this.interceptionNetworkId;
        super.read(tag, registries, clientPacket);
        this.networkId = tag.hasUUID(NETWORK_TAG) ? tag.getUUID(NETWORK_TAG) : null;
        this.networkRoleEnsured = false;
        this.interceptionNetworkId = tag.hasUUID(INTERCEPTION_NETWORK_TAG)
                ? tag.getUUID(INTERCEPTION_NETWORK_TAG)
                : null;
        if (clientPacket) {
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level, this.worldPosition, oldNetworkId, this.networkId);
            InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level,
                    this.worldPosition,
                    oldInterceptionNetworkId,
                    this.interceptionNetworkId);
        }
        this.assignedStructureUuid = tag.hasUUID(ASSIGNED_STRUCTURE_TAG)
                ? tag.getUUID(ASSIGNED_STRUCTURE_TAG) : null;
        this.nameCard = tag.contains(CARD_TAG) ? ItemStack.parseOptional(registries, tag.getCompound(CARD_TAG)) : ItemStack.EMPTY;
    }
}
