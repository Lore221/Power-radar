package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.radar.RadarDataSource;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.bridge.ShellAlarmIconBridge;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.interception.MovingAabbThreatEvaluator;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShellAlarmBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final BehaviourType<ShellAlarmDimensionsBehaviour> DIMENSIONS_BEHAVIOUR_TYPE = new BehaviourType<>();
    private final Map<UUID, ThreatEvaluation> evaluations = new HashMap<>();
    private ShellAlarmDimensionsBehaviour protectionDimensions;
    private boolean networkConnected;
    private boolean alarmActive;
    private int trackedShellCount;
    private long lastProcessedRadarScanGameTime = Long.MIN_VALUE;
    private long lastStatusLogGameTime = Long.MIN_VALUE;
    private String lastStatusLog = "";
    private PowerRadarCeeSnapshot electrical = PowerRadarCeeSnapshot.EMPTY;
    private UUID radarStructureEntityUuid;
    private boolean radarStructureEntityActive;
    private UUID networkId;
    private boolean runtimeRegisteredLoaded;
    private boolean needsRuntimeRegister;
    private int startupSafetyTicks;
    private UUID interceptionNetworkId;

    public ShellAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHELL_ALARM.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.protectionDimensions = new ShellAlarmDimensionsBehaviour(
                Component.translatable("message.power_radar.shell_alarm.dimensions"),
                this,
                new ShellAlarmDimensionsTransform());
        behaviours.add(this.protectionDimensions);
    }

    private void onProtectionDimensionsChanged() {
        this.evaluations.clear();
        this.lastProcessedRadarScanGameTime = Long.MIN_VALUE;
        setChanged();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  ShellAlarmBlockEntity alarm) {
        if (level instanceof ServerLevel serverLevel) {
            alarm.tick();
            alarm.tickServer(serverLevel, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        if (this.interceptionNetworkId == null) {
            createInterceptionNetwork();
        }
        if (this.startupSafetyTicks > 0) {
            this.startupSafetyTicks--;
        } else if (this.networkId != null && (this.needsRuntimeRegister || !this.runtimeRegisteredLoaded)) {
            registerLoaded(level);
            this.needsRuntimeRegister = false;
        }
        boolean powered = this.electrical.electricalState() == PowerRadarCeeState.POWERED;
        this.networkConnected = false;
        Set<UUID> present = new HashSet<>();
        int shellCount = 0;
        long gameTime = level.getGameTime();
        UUID connectedNetworkId = null;
        RadarDataSource controller = null;

        if (powered && this.networkId != null) {
            RadarNetworkManager.ControllersResolution resolution = RadarNetworkManager.get(level.getServer())
                    .resolveControllersForConsumer(this.networkId, globalPos());
            controller = resolution.controllers().isEmpty() ? null : new CombinedRadarDataSource(resolution.controllers());
            this.networkConnected = controller != null;
            connectedNetworkId = this.networkId;
        }
        logStatus(level, powered, connectedNetworkId, controller);
        syncRadarStructureEntityState(level, powered && this.networkConnected);

        if (controller == null) {
            this.lastProcessedRadarScanGameTime = Long.MIN_VALUE;
            clearInactiveState(level, state);
            return;
        }
        long radarScanGameTime = controller.lastScanGameTime();
        long previousRadarScanGameTime = this.lastProcessedRadarScanGameTime;
        if (radarScanGameTime == previousRadarScanGameTime) {
            return;
        }
        this.lastProcessedRadarScanGameTime = radarScanGameTime;

        RadarDataSource radarController = controller;
        final int[] count = {0};
        radarController.forEachTrackedTargetBySource(TargetSourceType.CBC_BIG_CANNON_PROJECTILE, track -> {
            if (track.targetUuid() == null) {
                return;
            }
            count[0]++;
            UUID uuid = track.targetUuid();
            present.add(uuid);
            this.evaluations.put(uuid, trajectoryThreatens(level, track));
        });
        shellCount = count[0];

        Iterator<Map.Entry<UUID, ThreatEvaluation>> iterator = this.evaluations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!present.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
        this.trackedShellCount = shellCount;
        UUID interceptionChannelId = this.interceptionNetworkId;
        if (powered && this.networkConnected && connectedNetworkId != null && interceptionChannelId != null) {
            Set<UUID> dangerousShells = new HashSet<>();
            List<ThreatSnapshot> threatSnapshots = new ArrayList<>();
            for (Map.Entry<UUID, ThreatEvaluation> entry : this.evaluations.entrySet()) {
                if (entry.getValue().dangerous()) {
                    UUID threatUuid = entry.getKey();
                    dangerousShells.add(threatUuid);
                    TrackedTargetView track = radarController.findTrackedTarget(threatUuid);
                    if (track != null && track.targetUuid() != null) {
                        ThreatEvaluation evaluation = entry.getValue();
                        threatSnapshots.add(new ThreatSnapshot(
                                threatUuid,
                                level.dimension(),
                                track.position(),
                                track.velocity(),
                                track.lastSeenGameTime(),
                                evaluation.ballistics().gravity(),
                                evaluation.ballistics().drag(),
                                evaluation.ballistics().quadraticDrag(),
                                Vec3.atCenterOf(this.worldPosition),
                                Vec3.ZERO,
                                Vec3.ZERO,
                                level.getGameTime(),
                                null,
                                null));
                    }
                }
            }
            InterceptionCoordinator.publishThreats(
                    level,
                    interceptionChannelId,
                    this.worldPosition,
                    threatSnapshots,
                    InterceptionCoordinator.threatTtlTicksForScanInterval(
                            radarScanIntervalTicks(radarScanGameTime, previousRadarScanGameTime)));
            if (PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
                PowerRadar.LOGGER.info(
                        "[PowerRadar BugReport][ShellAlarm][Scan] alarm={} network={} scanTick={} width={} height={} depth={} trackedShells={} dangerousShells={}",
                        this.worldPosition,
                        interceptionChannelId,
                        radarScanGameTime,
                        protectionWidthBlocks(),
                        protectionHeightBlocks(),
                        protectionDepthBlocks(),
                        shellCount,
                        dangerousShells.size());
            }
        }
        boolean nextActive = powered && this.networkConnected
                && this.evaluations.values().stream().anyMatch(ThreatEvaluation::dangerous);
        if (nextActive != this.alarmActive) {
            this.alarmActive = nextActive;
            notifyRedstone(level, state);
        }
        setChanged();
    }

    private void logStatus(
            ServerLevel level,
            boolean powered,
            UUID connectedNetworkId,
            RadarDataSource controller
    ) {
        if (!PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
            return;
        }
        String status = powered + "|" + this.electrical.electricalState()
                + "|" + this.networkId + "|" + (controller != null);
        long gameTime = level.getGameTime();
        if (status.equals(this.lastStatusLog) && gameTime - this.lastStatusLogGameTime < 20L) {
            return;
        }
        this.lastStatusLog = status;
        this.lastStatusLogGameTime = gameTime;
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][ShellAlarm][Status] alarm={} tick={} powered={} electricalState={} voltage={} linkStatus={} linkNetwork={} connectedNetwork={} controllerFound={}",
                this.worldPosition,
                gameTime,
                powered,
                this.electrical.electricalState(),
                round(this.electrical.voltageVolts()),
                this.networkId == null ? "UNTUNED" : "TUNED",
                this.networkId,
                connectedNetworkId,
                controller != null);
    }

    private void clearInactiveState(ServerLevel level, BlockState state) {
        boolean changed = !this.evaluations.isEmpty() || this.trackedShellCount != 0 || this.alarmActive;
        this.evaluations.clear();
        this.trackedShellCount = 0;
        if (this.alarmActive) {
            this.alarmActive = false;
            notifyRedstone(level, state);
        }
        if (changed) {
            setChanged();
        }
    }

    private ThreatEvaluation trajectoryThreatens(ServerLevel level, TrackedTargetView track) {
        Entity entity = track.targetUuid() == null ? null : level.getEntity(track.targetUuid());
        Vec3 position = entity != null && entity.isAlive() ? entity.position() : track.position();
        Vec3 velocity = entity != null && entity.isAlive() ? entity.getDeltaMovement() : track.velocity();
        ShellAlarmCbcCompat.Ballistics fallbackBallistics = ShellAlarmCbcCompat.ballistics(null);
        ShellAlarmCbcCompat.Ballistics ballistics = entity == null
                ? fallbackBallistics
                : ShellAlarmCbcCompat.ballistics(entity);
        double projectileRadius = projectileRadius(track, entity);
        boolean dangerous = MovingAabbThreatEvaluator.threatens(
                protectedBounds(),
                Vec3.ZERO,
                Vec3.ZERO,
                position,
                velocity,
                projectileRadius,
                0.0D,
                ballistics,
                PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS);
        return trajectoryResult(
                track,
                position,
                velocity,
                dangerous ? "protected-aabb-hit" : "protected-aabb-miss",
                dangerous,
                ballistics);
    }

    private AABB protectedBounds() {
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        double halfWidth = protectionWidthBlocks() * 0.5D;
        double halfHeight = protectionHeightBlocks() * 0.5D;
        double halfDepth = protectionDepthBlocks() * 0.5D;
        return new AABB(
                center.x - halfWidth,
                center.y - halfHeight,
                center.z - halfDepth,
                center.x + halfWidth,
                center.y + halfHeight,
                center.z + halfDepth);
    }

    private static double projectileRadius(TrackedTargetView track, Entity projectile) {
        if (projectile != null) {
            AABB bounds = projectile.getBoundingBox();
            return Math.max(0.05D,
                    Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5D);
        }
        double size = track.approximateSize();
        return Double.isFinite(size) ? Math.max(0.05D, size * 0.5D) : 0.05D;
    }

    private static long radarScanIntervalTicks(long scanGameTime, long previousScanGameTime) {
        if (previousScanGameTime == Long.MIN_VALUE || scanGameTime <= previousScanGameTime) {
            return RadarConstants.radarScanUpdateIntervalTicks();
        }
        return scanGameTime - previousScanGameTime;
    }

    private ThreatEvaluation trajectoryResult(
            TrackedTargetView track,
            Vec3 simulatedPosition,
            Vec3 simulatedVelocity,
            String reason,
            boolean dangerous,
            ShellAlarmCbcCompat.Ballistics ballistics
    ) {
        if (PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][ShellAlarm][Trajectory] alarm={} target={} entityType={} source={} trackTick={} trackPos={} trackVelocity={} simulatedPos={} simulatedVelocity={} width={} height={} depth={} result={} dangerous={}",
                    this.worldPosition,
                    track.targetUuid(),
                    track.entityTypeId(),
                    track.sourceType(),
                    track.lastSeenGameTime(),
                    shortVec(track.position()),
                    shortVec(track.velocity()),
                    shortVec(simulatedPosition),
                    shortVec(simulatedVelocity),
                    protectionWidthBlocks(),
                    protectionHeightBlocks(),
                    protectionDepthBlocks(),
                    reason,
                    dangerous);
        }
        return new ThreatEvaluation(dangerous, ballistics);
    }

    private static String shortVec(Vec3 vec) {
        return "(" + round(vec.x) + "," + round(vec.y) + "," + round(vec.z) + ")";
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 1000.0) / 1000.0 : value;
    }


    private void notifyRedstone(ServerLevel level, BlockState state) {
        Block block = state.getBlock();
        level.updateNeighborsAt(this.worldPosition, block);
        level.updateNeighbourForOutputSignal(this.worldPosition, block);
    }

    public int protectionWidthBlocks() {
        return this.protectionDimensions == null
                ? PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_WIDTH_BLOCKS
                : this.protectionDimensions.width();
    }

    public int protectionHeightBlocks() {
        return this.protectionDimensions == null
                ? PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS
                : this.protectionDimensions.height();
    }

    public int protectionDepthBlocks() {
        return this.protectionDimensions == null
                ? PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_DEPTH_BLOCKS
                : this.protectionDimensions.depth();
    }

    private void syncRadarStructureEntityState(ServerLevel level, boolean active) {
        if (active == this.radarStructureEntityActive) {
            return;
        }
        this.radarStructureEntityActive = active;
        if (!active) {
            removeRadarStructureEntity(level);
            return;
        }
        RadarStructureEntity existing = findRadarStructureEntity(level);
        if (existing != null) {
            this.radarStructureEntityUuid = existing.getUUID();
            return;
        }
        RadarStructureEntity marker = ModEntities.RADAR_STRUCTURE.get().create(level);
        if (marker != null) {
            marker.setControllerPos(this.worldPosition);
            if (level.addFreshEntity(marker)) {
                this.radarStructureEntityUuid = marker.getUUID();
                setChanged();
            }
        }
    }

    private RadarStructureEntity findRadarStructureEntity(ServerLevel level) {
        if (this.radarStructureEntityUuid != null
                && level.getEntity(this.radarStructureEntityUuid) instanceof RadarStructureEntity marker
                && marker.belongsTo(this.worldPosition)) {
            return marker;
        }
        return level.getEntitiesOfClass(RadarStructureEntity.class, new AABB(this.worldPosition).inflate(1.0D),
                        marker -> marker.belongsTo(this.worldPosition))
                .stream().findFirst().orElse(null);
    }

    private void removeRadarStructureEntity(ServerLevel level) {
        RadarStructureEntity marker = findRadarStructureEntity(level);
        if (marker != null) {
            marker.discard();
        }
        if (this.radarStructureEntityUuid != null) {
            this.radarStructureEntityUuid = null;
            setChanged();
        }
    }

    public void deactivateRadarStructureEntity() {
        this.radarStructureEntityActive = false;
        if (this.level instanceof ServerLevel serverLevel) {
            removeRadarStructureEntity(serverLevel);
        }
    }

    public void initializeNetwork(UUID networkId) {
        if (networkId == null || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
        if (this.networkId != null && !this.networkId.equals(networkId)) {
            manager.removePersistentLink(this.networkId, globalPos());
        }
        this.networkId = networkId;
        manager.loadLink(networkId, globalPos());
        this.runtimeRegisteredLoaded = true;
        this.needsRuntimeRegister = false;
        setChanged();
        sendData();
    }

    public UUID networkId() {
        return this.networkId;
    }

    public UUID ensureNetworkId() {
        if (this.networkId == null && this.level instanceof ServerLevel serverLevel) {
            initializeNetwork(RadarNetworkManager.get(serverLevel.getServer()).createNetwork());
        }
        return this.networkId;
    }

    public UUID ensureInterceptionNetworkId() {
        if (this.interceptionNetworkId == null && this.level instanceof ServerLevel) {
            createInterceptionNetwork();
        }
        return this.interceptionNetworkId;
    }

    public UUID interceptionNetworkId() {
        return this.interceptionNetworkId;
    }

    private void createInterceptionNetwork() {
        UUID oldId = this.interceptionNetworkId;
        this.interceptionNetworkId = UUID.randomUUID();
        InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldId, this.interceptionNetworkId);
        setChanged();
        sendData();
    }

    public void destroyNetworkMembership() {
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).removePersistentLink(this.networkId, globalPos());
            this.runtimeRegisteredLoaded = false;
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level instanceof ServerLevel && this.networkId != null) {
            this.needsRuntimeRegister = true;
            this.startupSafetyTicks = 2;
        }
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
            InterceptionNetworkNodeClientCacheBridge.onLoaded(
                    this.level, this.worldPosition, this.interceptionNetworkId);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
            InterceptionNetworkNodeClientCacheBridge.onLoaded(
                    this.level, this.worldPosition, this.interceptionNetworkId);
        }
    }

    @Override
    public void remove() {
        unloadNetworkRuntime();
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
            InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        }
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        unloadNetworkRuntime();
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
            InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    private void unloadNetworkRuntime() {
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null
                && this.runtimeRegisteredLoaded) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadLink(this.networkId, globalPos());
            this.runtimeRegisteredLoaded = false;
        }
    }

    private void registerLoaded(ServerLevel level) {
        if (this.networkId != null) {
            RadarNetworkManager.get(level.getServer()).loadLink(this.networkId, globalPos());
            this.runtimeRegisteredLoaded = true;
        }
    }

    private GlobalPos globalPos() {
        return GlobalPos.of(this.level == null ? net.minecraft.world.level.Level.OVERWORLD : this.level.dimension(),
                this.worldPosition);
    }

    public boolean networkConnected() {
        return this.networkConnected;
    }

    public boolean alarmActive() {
        return this.alarmActive;
    }

    public int trackedShellCount() {
        return this.trackedShellCount;
    }

    public PowerRadarCeeState electricalState() {
        return this.electrical.electricalState();
    }

    public void applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        PowerRadarCeeSnapshot next = sanitizeElectricalSnapshot(snapshot);
        if (electricalSnapshotChanged(this.electrical, next)) {
            this.electrical = next;
            setChanged();
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("ElectricalBridgeEnabled", this.electrical.bridgeEnabled());
        tag.putString("ElectricalState", this.electrical.electricalState().name());
        tag.putDouble("ElectricalVoltageVolts", this.electrical.voltageVolts());
        tag.putDouble("ElectricalCurrentAmps", this.electrical.currentAmps());
        tag.putDouble("ElectricalPowerWatts", this.electrical.powerWatts());
        tag.putDouble("ElectricalResistanceOhms", this.electrical.resistanceOhms());
        if (this.radarStructureEntityUuid != null) {
            tag.putUUID("RadarStructureEntity", this.radarStructureEntityUuid);
        }
        if (this.networkId != null) {
            tag.putUUID("PowerRadarNetworkId", this.networkId);
        }
        if (this.interceptionNetworkId != null) {
            tag.putUUID("InterceptionNetworkId", this.interceptionNetworkId);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        UUID oldNetworkId = this.networkId;
        UUID oldInterceptionNetworkId = this.interceptionNetworkId;
        PowerRadarCeeState state;
        try {
            state = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            state = PowerRadarCeeState.INVALID_STRUCTURE;
        }
        this.electrical = sanitizeElectricalSnapshot(new PowerRadarCeeSnapshot(
                tag.getBoolean("ElectricalBridgeEnabled"),
                state,
                tag.getDouble("ElectricalVoltageVolts"),
                tag.getDouble("ElectricalCurrentAmps"),
                tag.getDouble("ElectricalPowerWatts"),
                tag.contains("ElectricalResistanceOhms")
                        ? tag.getDouble("ElectricalResistanceOhms")
                        : PowerRadarCeeConstants.OFF_RESISTANCE_OHMS));
        this.radarStructureEntityUuid = tag.hasUUID("RadarStructureEntity")
                ? tag.getUUID("RadarStructureEntity") : null;
        this.networkId = tag.hasUUID("PowerRadarNetworkId")
                ? tag.getUUID("PowerRadarNetworkId") : null;
        this.interceptionNetworkId = tag.hasUUID("InterceptionNetworkId")
                ? tag.getUUID("InterceptionNetworkId") : null;
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level, this.worldPosition, oldNetworkId, this.networkId);
            InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level,
                    this.worldPosition,
                    oldInterceptionNetworkId,
                    this.interceptionNetworkId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ProtectionRadius")) {
            int legacySide = Mth.clamp(
                    tag.getInt("ProtectionRadius"),
                    PowerRadarCeeConstants.SHELL_ALARM_MIN_DIMENSION_BLOCKS,
                    PowerRadarCeeConstants.SHELL_ALARM_MAX_DIMENSION_BLOCKS);
            if (this.protectionDimensions != null) {
                this.protectionDimensions.setDimensions(
                        legacySide,
                        legacySide,
                        PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS);
            }
        }
    }

    private static PowerRadarCeeSnapshot sanitizeElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        if (snapshot == null) {
            return PowerRadarCeeSnapshot.EMPTY;
        }
        return new PowerRadarCeeSnapshot(
                snapshot.bridgeEnabled(),
                snapshot.electricalState(),
                Double.isFinite(snapshot.voltageVolts()) ? snapshot.voltageVolts() : 0.0,
                Double.isFinite(snapshot.currentAmps()) ? Math.abs(snapshot.currentAmps()) : 0.0,
                Double.isFinite(snapshot.powerWatts()) ? Math.max(0.0, snapshot.powerWatts()) : 0.0,
                PowerRadarCeeConstants.sanitizeResistance(snapshot.resistanceOhms()));
    }

    private static boolean electricalSnapshotChanged(PowerRadarCeeSnapshot previous, PowerRadarCeeSnapshot next) {
        return previous.bridgeEnabled() != next.bridgeEnabled()
                || previous.electricalState() != next.electricalState()
                || Math.abs(previous.voltageVolts() - next.voltageVolts()) > 0.01
                || Math.abs(previous.currentAmps() - next.currentAmps()) > 0.001
                || Math.abs(previous.powerWatts() - next.powerWatts()) > 0.1
                || Math.abs(previous.resistanceOhms() - next.resistanceOhms()) > 0.001;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("power_radar.electrical.state",
                Component.translatable(this.electrical.electricalState().translationKey()))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(this.electrical.voltageVolts()))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(this.electrical.powerWatts()))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm.zone",
                        protectionWidthBlocks(), protectionHeightBlocks(), protectionDepthBlocks())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm.shells", this.trackedShellCount)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(this.alarmActive
                        ? "goggles.power_radar.shell_alarm.danger"
                        : "goggles.power_radar.shell_alarm.clear")
                .withStyle(this.alarmActive ? ChatFormatting.RED : ChatFormatting.GREEN));
        return true;
    }

    private record ThreatEvaluation(
            boolean dangerous,
            ShellAlarmCbcCompat.Ballistics ballistics
    ) {
    }

    private static class ShellAlarmDimensionsBehaviour extends ScrollOptionBehaviour<ShellAlarmSettingsOption> {
        private int width = PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_WIDTH_BLOCKS;
        private int depth = PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_DEPTH_BLOCKS;
        private int height = PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS;

        private ShellAlarmDimensionsBehaviour(
                Component label,
                SmartBlockEntity blockEntity,
                CenteredSideValueBoxTransform transform
        ) {
            super(ShellAlarmSettingsOption.class, label, blockEntity, transform);
        }

        @Override
        public BehaviourType<?> getType() {
            return DIMENSIONS_BEHAVIOUR_TYPE;
        }

        @Override
        public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            tag.putInt("ProtectionWidth", this.width);
            tag.putInt("ProtectionDepth", this.depth);
            tag.putInt("ProtectionHeight", this.height);
        }

        @Override
        public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            this.width = readDimension(
                    tag, "ProtectionWidth", PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_WIDTH_BLOCKS);
            this.depth = readDimension(
                    tag, "ProtectionDepth", PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_DEPTH_BLOCKS);
            this.height = readDimension(
                    tag, "ProtectionHeight", PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_HEIGHT_BLOCKS);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(
                    this.label,
                    PowerRadarCeeConstants.SHELL_ALARM_MAX_DIMENSION_BLOCKS,
                    10,
                    ImmutableList.of(
                            Component.translatable("message.power_radar.shell_alarm.width"),
                            Component.translatable("message.power_radar.shell_alarm.depth"),
                            Component.translatable("message.power_radar.shell_alarm.height")),
                    new ValueSettingsFormatter(settings ->
                            Component.translatable("power_radar.unit.blocks", settings.value())));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings settings, boolean ctrlHeld) {
            int dimension = clampDimension(settings.value());
            switch (Mth.clamp(settings.row(), 0, 2)) {
                case 0 -> this.width = dimension;
                case 1 -> this.depth = dimension;
                case 2 -> this.height = dimension;
                default -> {
                }
            }
            if (this.blockEntity instanceof ShellAlarmBlockEntity alarm) {
                alarm.onProtectionDimensionsChanged();
                alarm.sendData();
            }
            playFeedbackSound(this);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, this.width);
        }

        int width() {
            return clampDimension(this.width);
        }

        int depth() {
            return clampDimension(this.depth);
        }

        int height() {
            return clampDimension(this.height);
        }

        void setDimensions(int width, int depth, int height) {
            this.width = clampDimension(width);
            this.depth = clampDimension(depth);
            this.height = clampDimension(height);
        }

        private static int readDimension(CompoundTag tag, String key, int fallback) {
            return tag.contains(key) ? clampDimension(tag.getInt(key)) : fallback;
        }

        private static int clampDimension(int value) {
            return Mth.clamp(
                    value,
                    PowerRadarCeeConstants.SHELL_ALARM_MIN_DIMENSION_BLOCKS,
                    PowerRadarCeeConstants.SHELL_ALARM_MAX_DIMENSION_BLOCKS);
        }
    }

    private enum ShellAlarmSettingsOption implements INamedIconOptions {
        DIMENSIONS;

        @Override
        public AllIcons getIcon() {
            return ShellAlarmIconBridge.dimensions();
        }

        @Override
        public String getTranslationKey() {
            return "message.power_radar.shell_alarm.dimensions";
        }
    }

    private static class ShellAlarmDimensionsTransform extends CenteredSideValueBoxTransform {
        private ShellAlarmDimensionsTransform() {
            super((state, direction) -> direction == state.getValue(
                    com.limbo2136.powerradar.block.ShellAlarmBlock.FACING));
        }

        @Override
        public float getScale() {
            return 0.55F;
        }
    }
}
