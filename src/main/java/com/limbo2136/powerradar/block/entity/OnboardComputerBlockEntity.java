package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureNames;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.interception.MovingProtectedZone;
import com.limbo2136.powerradar.interception.MovingProtectedZoneTracker;
import com.limbo2136.powerradar.interception.ProtectedZoneThreatEvaluator;
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
    private final MovingProtectedZoneTracker protectedZoneTracker = new MovingProtectedZoneTracker();
    @Nullable private MovingProtectedZone protectedZone;
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
        if (this.protectedZone == null) {
            this.protectedZone = this.protectedZoneTracker.broadPhaseZone(
                    level,
                    this.worldPosition,
                    new AABB(this.worldPosition),
                    10.0D);
        }
        if (!isElectricallyOperational()
                || this.networkId == null
                || this.interceptionNetworkId == null
                || this.protectedZone == null
                || !this.protectedZone.onSable()
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

        this.protectedZone = this.protectedZoneTracker.broadPhaseZone(
                level,
                this.worldPosition,
                new AABB(this.worldPosition),
                10.0D);
        if (this.protectedZone == null) {
            clearPublishedThreats(level);
            setAlarmActive(level, state, false);
            return;
        }

        ServerLevel worldLevel = level.getServer().getLevel(level.dimension());
        if (worldLevel == null) {
            worldLevel = level;
        }
        ServerLevel projectileLevel = worldLevel;
        MovingProtectedZone zone = this.protectedZone;
        Vec3 alarmReference = zone.referencePosition();
        List<TrackedTargetView> tracks = new ArrayList<>();
        radar.forEachTrackedTargetBySource(TargetSourceType.CBC_BIG_CANNON_PROJECTILE, tracks::add);
        List<ThreatSnapshot> threats = new ArrayList<>();
        MovingProtectedZone initialZone = zone;
        List<TrackedTargetView> candidateTracks = tracks.stream()
                .filter(track -> ProtectedZoneThreatEvaluator.passesInitialBroadPhase(
                        projectileLevel,
                        initialZone,
                        track,
                        PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS))
                .toList();
        if (!candidateTracks.isEmpty()) {
            zone = this.protectedZoneTracker.refreshGeometryIfDue(level, zone, 10.0D);
            this.protectedZone = zone;
        }
        if (zone == null) {
            clearPublishedThreats(level);
            setAlarmActive(level, state, false);
            return;
        }
        if (!candidateTracks.isEmpty()) {
            zone = this.protectedZoneTracker.sampleVelocity(level, zone);
            this.protectedZone = zone;
        }
        if (zone == null) {
            clearPublishedThreats(level);
            setAlarmActive(level, state, false);
            return;
        }
        MovingProtectedZone sampledZone = zone;
        candidateTracks = candidateTracks.stream()
                .filter(track -> ProtectedZoneThreatEvaluator.passesInitialBroadPhase(
                        projectileLevel,
                        sampledZone,
                        track,
                        PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS))
                .toList();
        if (!candidateTracks.isEmpty()) {
            zone = this.protectedZoneTracker.completeMotionSample(zone);
            this.protectedZone = zone;
        }
        if (zone == null) {
            clearPublishedThreats(level);
            setAlarmActive(level, state, false);
            return;
        }
        MovingProtectedZone evaluatedZone = zone;
        for (TrackedTargetView track : candidateTracks) {
            ThreatSnapshot threat = evaluateThreat(
                    projectileLevel,
                    evaluatedZone,
                    track);
            if (threat != null) {
                threats.add(threat);
            }
        }

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
            MovingProtectedZone zone,
            TrackedTargetView track
    ) {
        UUID targetUuid = track.targetUuid();
        if (targetUuid == null) {
            return null;
        }
        ProtectedZoneThreatEvaluator.Evaluation evaluation = ProtectedZoneThreatEvaluator.evaluate(
                worldLevel,
                zone,
                track,
                PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS);
        if (!evaluation.dangerous()) {
            return null;
        }
        return new ThreatSnapshot(
                targetUuid,
                worldLevel.dimension(),
                evaluation.projectilePosition(),
                evaluation.projectileVelocity(),
                track.lastSeenGameTime(),
                evaluation.ballistics().gravity(),
                evaluation.ballistics().drag(),
                evaluation.ballistics().quadraticDrag(),
                zone.referencePosition(),
                zone.velocity(),
                zone.acceleration(),
                zone.sampleGameTime(),
                null,
                null);
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
