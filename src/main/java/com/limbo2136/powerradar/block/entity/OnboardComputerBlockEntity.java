package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
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
import com.limbo2136.powerradar.onboard.OnboardCombinedModuleType;
import com.limbo2136.powerradar.onboard.OnboardModuleColumn;
import com.limbo2136.powerradar.onboard.OnboardModuleSlot;
import com.limbo2136.powerradar.onboard.OnboardModuleType;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OnboardComputerBlockEntity extends RadarMonitorControllerBlockEntity {
    private static final String NETWORK_TAG = "NetworkId";
    private static final String INTERCEPTION_NETWORK_TAG = "InterceptionNetworkId";
    private static final String MODULES_TAG = "OnboardModules";
    private static final String MODULE_SLOT_TAG = "Slot";
    private static final String MODULE_STACK_TAG = "Stack";
    private static final String ACCELEROMETER_COLUMNS_TAG = "AccelerometerColumns";
    private static final String VARIOMETER_COLUMNS_TAG = "VariometerColumns";
    @Nullable private UUID networkId;
    @Nullable private UUID interceptionNetworkId;
    private final ItemStack[] modules = new ItemStack[OnboardModuleSlot.values().length];
    private int accelerometerColumnMask;
    private int variometerColumnMask;
    private final MovingProtectedZoneTracker protectedZoneTracker = new MovingProtectedZoneTracker();
    @Nullable private MovingProtectedZone protectedZone;
    private long lastProcessedThreatScanGameTime = Long.MIN_VALUE;
    private boolean alarmActive;
    private boolean publishedThreats;
    private boolean networkRoleEnsured;

    public OnboardComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ONBOARD_COMPUTER.get(), pos, state);
        java.util.Arrays.fill(this.modules, ItemStack.EMPTY);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, OnboardComputerBlockEntity computer) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        computer.validateLodestoneModules(serverLevel);
        if (computer.networkId == null) computer.createOwnNetwork(serverLevel);
        computer.ensureOnboardNetworkRole(serverLevel);
        if (computer.interceptionNetworkId == null) computer.createOwnInterceptionNetwork();
        computer.updateDisplayStructure(pos, 1, state.getValue(OnboardComputerBlock.FACING),
                RadarDisplayStructureResolver.StructureStatus.ACTIVE);
        RadarMonitorControllerBlockEntity.tick(serverLevel, pos, state, computer);
        computer.tickShellAlarm(serverLevel, state);
    }

    private void validateLodestoneModules(ServerLevel level) {
        // Installed stacks do not receive Item.inventoryTick(), so reproduce the
        // vanilla tracker invalidation at a low, staggered rate.
        if (Math.floorMod(level.getGameTime() + this.worldPosition.asLong(), 20L) != 0L) {
            return;
        }
        ServerLevel worldLevel = level.getServer().getLevel(level.dimension());
        if (worldLevel == null) {
            worldLevel = level;
        }
        boolean changed = false;
        for (ItemStack module : this.modules) {
            LodestoneTracker tracker = module.get(DataComponents.LODESTONE_TRACKER);
            if (tracker == null) {
                continue;
            }
            LodestoneTracker updated = tracker.tick(worldLevel);
            if (updated != tracker) {
                module.set(DataComponents.LODESTONE_TRACKER, updated);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            sendData();
        }
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

    public ItemStack module(OnboardModuleSlot slot) {
        return this.modules[slot.index()];
    }

    public int installedModuleMask() {
        int mask = 0;
        for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
            if (!this.modules[slot.index()].isEmpty()) {
                mask |= 1 << slot.index();
            }
        }
        return mask;
    }

    @Nullable
    public OnboardCombinedModuleType assembledModule(OnboardModuleColumn column) {
        if ((this.accelerometerColumnMask & column.bit()) != 0) {
            return OnboardCombinedModuleType.ACCELEROMETER;
        }
        if ((this.variometerColumnMask & column.bit()) != 0) {
            return OnboardCombinedModuleType.VARIOMETER;
        }
        return null;
    }

    @Nullable
    public OnboardCombinedModuleType assemblableModule(OnboardModuleColumn column) {
        OnboardModuleType front = OnboardModuleType.fromStack(module(column.frontSlot()));
        OnboardModuleType rear = OnboardModuleType.fromStack(module(column.rearSlot()));
        return OnboardCombinedModuleType.fromParts(front, rear);
    }

    public boolean toggleCombinedModule(OnboardModuleColumn column) {
        OnboardCombinedModuleType assembled = assembledModule(column);
        if (assembled != null) {
            setCombinedModuleAssembled(assembled, column, false);
        } else {
            OnboardCombinedModuleType candidate = assemblableModule(column);
            if (candidate == null) {
                return false;
            }
            setCombinedModuleAssembled(candidate, column, true);
        }
        setChanged();
        sendData();
        return true;
    }

    // Маски остаются раздельными, чтобы старый тег акселерометра сохранял совместимость миров.
    private void setCombinedModuleAssembled(
            OnboardCombinedModuleType type,
            OnboardModuleColumn column,
            boolean assembled
    ) {
        int bit = column.bit();
        if (type == OnboardCombinedModuleType.ACCELEROMETER) {
            this.accelerometerColumnMask = assembled
                    ? this.accelerometerColumnMask | bit
                    : this.accelerometerColumnMask & ~bit;
        } else if (type == OnboardCombinedModuleType.VARIOMETER) {
            this.variometerColumnMask = assembled
                    ? this.variometerColumnMask | bit
                    : this.variometerColumnMask & ~bit;
        }
    }

    private void clearCombinedModule(OnboardModuleColumn column) {
        int inverseBit = ~column.bit();
        this.accelerometerColumnMask &= inverseBit;
        this.variometerColumnMask &= inverseBit;
    }

    private void validateCombinedModule(OnboardModuleColumn column) {
        OnboardCombinedModuleType candidate = assemblableModule(column);
        if (candidate != OnboardCombinedModuleType.ACCELEROMETER) {
            this.accelerometerColumnMask &= ~column.bit();
        }
        if (candidate != OnboardCombinedModuleType.VARIOMETER) {
            this.variometerColumnMask &= ~column.bit();
        }
    }

    public boolean hasAssembledModule(OnboardModuleColumn column) {
        return assembledModule(column) != null;
    }

    public boolean canAssembleModule(OnboardModuleColumn column) {
        return assemblableModule(column) != null;
    }

    private void clearAllCombinedModules() {
        this.accelerometerColumnMask = 0;
        this.variometerColumnMask = 0;
    }

    public boolean insertModule(OnboardModuleSlot slot, ItemStack held, boolean consume) {
        if (!this.modules[slot.index()].isEmpty() || !OnboardModuleType.accepts(held)) {
            return false;
        }
        this.modules[slot.index()] = held.copyWithCount(1);
        if (consume) {
            held.shrink(1);
        }
        setChanged();
        sendData();
        return true;
    }

    public ItemStack removeModule(OnboardModuleSlot slot) {
        ItemStack result = this.modules[slot.index()];
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }
        OnboardModuleColumn column = OnboardModuleColumn.containing(slot);
        if (column != null) {
            clearCombinedModule(column);
        }
        this.modules[slot.index()] = ItemStack.EMPTY;
        setChanged();
        sendData();
        return result;
    }

    public List<ItemStack> removeAllModules() {
        List<ItemStack> removed = new ArrayList<>();
        for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
            ItemStack stack = this.modules[slot.index()];
            if (!stack.isEmpty()) {
                removed.add(stack);
                this.modules[slot.index()] = ItemStack.EMPTY;
            }
        }
        if (!removed.isEmpty()) {
            clearAllCombinedModules();
            setChanged();
        }
        return List.copyOf(removed);
    }

    @Override protected UUID directNetworkId() { return this.networkId; }
    @Override protected boolean usesDisplayStructureResolver() { return false; }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.ONBOARD_COMPUTER)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> tooltip.add(Component.translatable("goggles.power_radar.onboard_computer")
                        .withStyle(ChatFormatting.GOLD));
                case ELECTRICAL_STATE -> tooltip.add(Component.translatable("power_radar.electrical.state",
                        Component.translatable(electricalState().translationKey())));
                case VOLTAGE -> tooltip.add(Component.translatable("power_radar.electrical.voltage",
                        PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
                case POWER -> tooltip.add(Component.translatable("power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
                default -> { }
            }
        }
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.networkId != null) tag.putUUID(NETWORK_TAG, this.networkId);
        if (this.interceptionNetworkId != null) {
            tag.putUUID(INTERCEPTION_NETWORK_TAG, this.interceptionNetworkId);
        }
        ListTag moduleTags = new ListTag();
        for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
            ItemStack stack = this.modules[slot.index()];
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag moduleTag = new CompoundTag();
            moduleTag.putByte(MODULE_SLOT_TAG, (byte) slot.index());
            moduleTag.put(MODULE_STACK_TAG, stack.save(registries));
            moduleTags.add(moduleTag);
        }
        if (!moduleTags.isEmpty()) {
            tag.put(MODULES_TAG, moduleTags);
        }
        if (this.accelerometerColumnMask != 0) {
            tag.putByte(ACCELEROMETER_COLUMNS_TAG, (byte) this.accelerometerColumnMask);
        }
        if (this.variometerColumnMask != 0) {
            tag.putByte(VARIOMETER_COLUMNS_TAG, (byte) this.variometerColumnMask);
        }
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
        java.util.Arrays.fill(this.modules, ItemStack.EMPTY);
        ListTag moduleTags = tag.getList(MODULES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < moduleTags.size(); i++) {
            CompoundTag moduleTag = moduleTags.getCompound(i);
            int slot = moduleTag.getByte(MODULE_SLOT_TAG);
            if (slot < 0 || slot >= this.modules.length) {
                continue;
            }
            this.modules[slot] = ItemStack.parseOptional(registries, moduleTag.getCompound(MODULE_STACK_TAG));
        }
        this.accelerometerColumnMask = tag.getByte(ACCELEROMETER_COLUMNS_TAG);
        this.variometerColumnMask = tag.getByte(VARIOMETER_COLUMNS_TAG);
        for (OnboardModuleColumn column : OnboardModuleColumn.values()) {
            validateCombinedModule(column);
        }
    }
}
