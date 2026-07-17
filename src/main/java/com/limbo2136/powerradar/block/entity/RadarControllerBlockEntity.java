package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.radar.RadarCoverage;
import com.limbo2136.powerradar.api.radar.RadarTargetingDataSource;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPose;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarModuleConstants;
import com.limbo2136.powerradar.radar.RadarOrientationState;
import com.limbo2136.powerradar.radar.RadarScanContext;
import com.limbo2136.powerradar.radar.RadarScanCoordinator;
import com.limbo2136.powerradar.radar.RadarScanRequest;
import com.limbo2136.powerradar.radar.RadarScanSlicePlan;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarScanProfile;
import com.limbo2136.powerradar.radar.RadarScanner;
import com.limbo2136.powerradar.radar.RadarStructure;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCache;
import com.limbo2136.powerradar.radar.RadarTargetTrack;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModEntities;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.GlobalPos;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public class RadarControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, RadarTargetingDataSource {
    private static final int REGULAR_DISCOVERY_WINDOW_MULTIPLIER = 5;
    private RadarScanMode scanMode = RadarScanMode.GROUND;
    private boolean assembled;
    private int validPanelCount;
    private int basicPanelCount;
    private int overviewModuleCount;
    private int baseStructureRange;
    private int currentRange;
    private int detectionFilterMask = RadarDetectionFilters.DEFAULT_MASK;
    private Direction radarFacing = Direction.NORTH;
    private RadarOrientationState orientationState = RadarOrientationState.fixed(RadarGeometry.yawDegrees(Direction.NORTH), 0L);
    private long lastScanGameTime;
    private long ticksSinceStructureValidation = RadarConstants.structureValidationIntervalTicks();
    private RadarStructure cachedStructure;
    private RadarId radarId;
    private double radarOriginX;
    private double radarOriginY;
    private double radarOriginZ;
    private final RadarTargetCache targetCache = new RadarTargetCache();
    private boolean removingOrUnloading;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
    private double cachedElectricalVoltageVolts;
    private double cachedElectricalCurrentAmps;
    private double cachedElectricalPowerWatts;
    private double cachedElectricalResistanceOhms = PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
    private RadarScanProfile activeScanProfile;
    private RadarScanProfile activeFrequentScanProfile;
    private RadarScanContext activeScanContext;
    private RadarScanSlicePlan activeScanSlicePlan;
    private ScanSlicePlanKey activeScanSlicePlanKey;
    private boolean activeScanPowered;
    private long displayRevision;
    private UUID radarStructureEntityUuid;
    private boolean radarStructureEntitySyncPending = true;

    public RadarControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.RADAR_CONTROLLER.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.removingOrUnloading = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.removingOrUnloading = false;
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.removingOrUnloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        this.removingOrUnloading = true;
        super.remove();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarControllerBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (blockEntity.removingOrUnloading) {
            return;
        }

        blockEntity.tick();
        if (blockEntity.radarStructureEntitySyncPending) {
            blockEntity.radarStructureEntitySyncPending = false;
            blockEntity.syncRadarStructureEntity(serverLevel);
        }
        blockEntity.ticksSinceStructureValidation++;
        blockEntity.runBudgetedScan(serverLevel);
    }

    private RadarScanMode fixedScanMode() {
        return this.getBlockState().getBlock() instanceof RadarControllerBlock controllerBlock
                ? controllerBlock.scanMode()
                : RadarScanMode.GROUND;
    }

    private void runBudgetedScan(ServerLevel level) {
        this.ensureRadarId(level);
        int updateIntervalTicks = Math.max(1, trackUpdateIntervalTicks());
        int scanSliceTicks = Math.max(1, updateIntervalTicks - 1);
        int bucket = scanBucket(level.getGameTime(), updateIntervalTicks);
        if (bucket == 0) {
            refreshScanWindow(level);
        } else if (this.activeScanContext == null) {
            return;
        }

        boolean scanSliceTick = bucket < scanSliceTicks;
        boolean publishTick = bucket == updateIntervalTicks - 1;
        RadarScanProfile discoveryProfile = null;
        List<AABB> discoverySlices = List.of();
        if (this.activeScanPowered && this.activeScanProfile != null) {
            RadarScanContext tickContext = scanContextAt(level.getGameTime());
            if (scanSliceTick) {
                ScanSlicePlanKey tickSlicePlanKey = scanSlicePlanKey(this.activeScanProfile, tickContext);
                if (this.activeScanSlicePlan == null || !tickSlicePlanKey.equals(this.activeScanSlicePlanKey)) {
                    this.activeScanSlicePlan = RadarScanner.buildSlicePlan(this.activeScanProfile, tickContext);
                    this.activeScanSlicePlanKey = tickSlicePlanKey;
                }
                discoveryProfile = isRegularDiscoveryWindow(
                        level.getGameTime(), updateIntervalTicks)
                        ? this.activeScanProfile
                        : this.activeFrequentScanProfile;
                if (hasDiscoveryTargets(discoveryProfile)) {
                    ArrayList<AABB> selected = new ArrayList<>();
                    List<AABB> slices = this.activeScanSlicePlan.slices();
                    for (int index = 0; index < slices.size(); index++) {
                        if (index % scanSliceTicks == bucket) {
                            selected.add(slices.get(index));
                        }
                    }
                    discoverySlices = List.copyOf(selected);
                }
            }
            if (!discoverySlices.isEmpty() || publishTick) {
                RadarScanCoordinator.submit(level, new RadarScanRequest(
                        this.radarId(),
                        discoverySlices.isEmpty() ? null : discoveryProfile,
                        publishTick ? this.activeScanProfile : null,
                        tickContext,
                        this.targetCache,
                        discoverySlices,
                        publishTick,
                        publishTick ? () -> this.lastScanGameTime = tickContext.gameTime() : null));
            }
        } else if (publishTick && this.activeScanContext != null) {
            RadarScanContext tickContext = scanContextAt(level.getGameTime());
            RadarScanCoordinator.submit(level, new RadarScanRequest(
                    this.radarId(), null, null, tickContext, this.targetCache, List.of(), true,
                    () -> this.lastScanGameTime = tickContext.gameTime()));
        }
    }

    private int scanBucket(long gameTime, int scanWindowTicks) {
        return Math.floorMod((int) gameTime, scanWindowTicks);
    }

    private boolean isRegularDiscoveryWindow(long gameTime, int scanWindowTicks) {
        long window = Math.floorDiv(gameTime, scanWindowTicks);
        return Math.floorMod(window, REGULAR_DISCOVERY_WINDOW_MULTIPLIER) == 0;
    }

    private static boolean hasDiscoveryTargets(RadarScanProfile profile) {
        return profile.detectPlayers()
                || profile.detectHostileMobs()
                || profile.detectPassiveMobs()
                || profile.detectProjectiles()
                || profile.detectSableStructures()
                || profile.detectUnknown();
    }

    private RadarScanContext scanContextAt(long gameTime) {
        if (this.activeScanContext == null) {
            return null;
        }
        RadarWorldPose worldPose = worldPoseAt(gameTime);
        return new RadarScanContext(
                this.activeScanContext.level(),
                this.activeScanContext.dimensionId(),
                this.activeScanContext.radarId(),
                worldPose.origin().x,
                worldPose.origin().y,
                worldPose.origin().z,
                this.activeScanContext.assemblyFacing(),
                worldPose.yawDegrees(),
                gameTime
        );
    }

    public int trackUpdateIntervalTicks() {
        boolean overview = this.cachedStructure != null
                ? this.cachedStructure.structureType() == RadarStructureType.OVERVIEW
                : this.orientationState.structureType() == RadarStructureType.OVERVIEW;
        return overview
                ? RadarModuleConstants.overviewTrackUpdateIntervalTicks()
                : RadarConstants.radarScanUpdateIntervalTicks();
    }

    private void refreshScanWindow(ServerLevel level) {
        RadarScanMode blockMode = fixedScanMode();
        int networkDisplayMask = RadarNetworkManager.get(level.getServer()).displayFilterMaskForController(
                GlobalPos.of(level.dimension(), this.worldPosition));
        if (this.scanMode != blockMode || this.detectionFilterMask != networkDisplayMask) {
            this.scanMode = blockMode;
            this.detectionFilterMask = networkDisplayMask;
            this.targetCache.clear();
            this.activeScanProfile = null;
            this.activeFrequentScanProfile = null;
            this.activeScanContext = null;
            this.activeScanSlicePlan = null;
            this.activeScanSlicePlanKey = null;
            this.displayRevision++;
            syncChanged();
        }
        this.ensureRadarId(level);
        long structureValidationInterval = RadarConstants.structureValidationIntervalTicks();
        if (this.cachedStructure == null
                || this.ticksSinceStructureValidation >= structureValidationInterval) {
            this.cachedStructure = RadarScanner.validateStructure(level, this.worldPosition);
            this.ticksSinceStructureValidation = 0L;
        }

        int nextRange = 0;
        int nextBaseRange = 0;
        Direction nextFacing = this.cachedStructure.facing();
        RadarStructureType nextStructureType = this.cachedStructure.structureType();
        RadarOrientationState nextOrientationState = this.cachedStructure.orientationState();
        int nextBasicPanelCount = this.cachedStructure.phasedArrayPanelCount();
        int nextOverviewModuleCount = this.cachedStructure.overviewModuleCount();
        int nextPanelCount = nextBasicPanelCount + nextOverviewModuleCount;
        boolean structureValid = this.cachedStructure.assembled() && nextPanelCount > 0;
        if (structureValid) {
            PowerRadarCeeIntegration.configureRadarLoad(level, this.worldPosition, true, nextStructureType,
                    nextBasicPanelCount, nextOverviewModuleCount);
        } else {
            PowerRadarCeeIntegration.configureRadarLoad(level, this.worldPosition, false, nextStructureType, 0, 0);
        }
        PowerRadarCeeState nextElectricalState = this.electricalState;

        if (structureValid) {
            nextBaseRange = nextStructureType == RadarStructureType.OVERVIEW
                    ? RadarScanner.calculateOverviewRange(this.scanMode, nextOverviewModuleCount)
                    : RadarScanner.calculateRange(this.scanMode, nextBasicPanelCount);
            Vec3 origin = Vec3.atCenterOf(this.cachedStructure.corePos());
            this.radarOriginX = origin.x;
            this.radarOriginY = origin.y;
            this.radarOriginZ = origin.z;
            RadarScanContext context = this.buildScanContext(level, nextFacing, nextOrientationState);
            if (isScanPowered(nextElectricalState)) {
                nextRange = (int) Math.floor(nextBaseRange * PowerRadarCeeConstants.radarRangeMultiplier(this.cachedElectricalVoltageVolts));
                logOptimizationScanDecision(level, structureValid, nextStructureType, nextBasicPanelCount, nextOverviewModuleCount,
                        nextBaseRange, nextRange, nextElectricalState, "scan");
                RadarScanProfile nextScanProfile = this.buildScanProfile(nextRange, level);
                ScanSlicePlanKey nextSlicePlanKey = scanSlicePlanKey(nextScanProfile, context);
                RadarScanSlicePlan nextSlicePlan = nextSlicePlanKey.equals(this.activeScanSlicePlanKey)
                        ? this.activeScanSlicePlan
                        : RadarScanner.buildSlicePlan(nextScanProfile, context);
                this.activeScanProfile = nextScanProfile;
                this.activeFrequentScanProfile = nextScanProfile.frequentDiscoveryOnly();
                this.activeScanContext = context;
                this.activeScanSlicePlan = nextSlicePlan;
                this.activeScanSlicePlanKey = nextSlicePlanKey;
                this.activeScanPowered = true;
            } else {
                logOptimizationScanDecision(level, structureValid, nextStructureType, nextBasicPanelCount, nextOverviewModuleCount,
                        nextBaseRange, 0, nextElectricalState, "housekeeping-power");
                this.activeScanProfile = null;
                this.activeFrequentScanProfile = null;
                this.activeScanContext = context;
                this.activeScanSlicePlan = null;
                this.activeScanSlicePlanKey = null;
                this.activeScanPowered = false;
            }
        } else {
            Vec3 origin = Vec3.atCenterOf(this.worldPosition);
            this.radarOriginX = origin.x;
            this.radarOriginY = origin.y;
            this.radarOriginZ = origin.z;
            RadarScanContext context = this.buildScanContext(level, nextFacing, nextOrientationState);
            logOptimizationScanDecision(level, structureValid, nextStructureType, nextBasicPanelCount, nextOverviewModuleCount,
                    0, 0, nextElectricalState, "housekeeping-structure");
            this.activeScanProfile = null;
            this.activeFrequentScanProfile = null;
            this.activeScanContext = context;
            this.activeScanSlicePlan = null;
            this.activeScanSlicePlanKey = null;
            this.activeScanPowered = false;
        }

        boolean wasRadarActive = this.assembled && this.currentRange > 0;
        boolean changed = this.assembled != this.cachedStructure.assembled()
                || this.validPanelCount != nextPanelCount
                || this.basicPanelCount != nextBasicPanelCount
                || this.overviewModuleCount != nextOverviewModuleCount
                || this.baseStructureRange != nextBaseRange
                || this.currentRange != nextRange
                || this.radarFacing != nextFacing
                || this.orientationState.structureType() != nextOrientationState.structureType()
                || this.orientationState.referenceYawDegrees() != nextOrientationState.referenceYawDegrees()
                || this.orientationState.rotationSpeedDegreesPerTick() != nextOrientationState.rotationSpeedDegreesPerTick()
                || this.electricalState != nextElectricalState;
        this.assembled = this.cachedStructure.assembled();
        this.validPanelCount = nextPanelCount;
        this.basicPanelCount = nextBasicPanelCount;
        this.overviewModuleCount = nextOverviewModuleCount;
        this.baseStructureRange = nextBaseRange;
        this.currentRange = nextRange;
        this.radarFacing = nextFacing;
        this.orientationState = nextOrientationState;
        this.electricalState = nextElectricalState;
        boolean isRadarActive = this.assembled && this.currentRange > 0;
        if (wasRadarActive != isRadarActive) {
            this.radarStructureEntitySyncPending = true;
        }
        if (changed) {
            this.displayRevision++;
            syncChanged();
        }
    }

    private void syncRadarStructureEntity(ServerLevel level) {
        if (!this.assembled || this.currentRange <= 0) {
            removeRadarStructureEntity(level);
            return;
        }

        RadarStructureEntity existing = findRadarStructureEntity(level);
        if (existing != null) {
            this.radarStructureEntityUuid = existing.getUUID();
            return;
        }

        RadarStructureEntity marker = ModEntities.RADAR_STRUCTURE.get().create(level);
        if (marker == null) {
            return;
        }
        marker.setControllerPos(this.worldPosition);
        if (level.addFreshEntity(marker)) {
            this.radarStructureEntityUuid = marker.getUUID();
            setChanged();
        }
    }

    private RadarStructureEntity findRadarStructureEntity(ServerLevel level) {
        if (this.radarStructureEntityUuid != null
                && level.getEntity(this.radarStructureEntityUuid) instanceof RadarStructureEntity marker
                && marker.belongsTo(this.worldPosition)) {
            return marker;
        }
        AABB searchArea = new AABB(this.worldPosition).inflate(1.0D);
        return level.getEntitiesOfClass(RadarStructureEntity.class, searchArea,
                        marker -> marker.belongsTo(this.worldPosition))
                .stream()
                .findFirst()
                .orElse(null);
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
        this.assembled = false;
        this.currentRange = 0;
        this.radarStructureEntitySyncPending = false;
        if (this.level instanceof ServerLevel serverLevel) {
            removeRadarStructureEntity(serverLevel);
        }
    }

    private RadarScanProfile buildScanProfile(int range, ServerLevel level) {
        RadarScanProfile profile = buildScanProfile(range);
        return RadarWorldPoseResolver.isOnSableStructure(level, this.worldPosition)
                ? profile.withFullHorizontalCoverage()
                : profile;
    }

    private RadarScanProfile buildScanProfile(int range) {
        RadarScanProfile profile = this.orientationState.structureType() == RadarStructureType.OVERVIEW
                ? RadarScanProfile.overviewController(this.scanMode, range)
                : RadarScanProfile.sectorController(this.scanMode, range);
        return profile.withDetectionFilter(this.detectionFilterMask);
    }

    private RadarScanContext buildScanContext(
            ServerLevel level,
            Direction assemblyFacing,
            RadarOrientationState orientationState
    ) {
        RadarWorldPose worldPose = RadarWorldPoseResolver.resolve(
                level,
                this.worldPosition,
                new Vec3(this.radarOriginX, this.radarOriginY, this.radarOriginZ),
                orientationState.yawAt(level.getGameTime())
        );
        RadarScanContext context = new RadarScanContext(
                level,
                level.dimension().location(),
                this.radarId,
                worldPose.origin().x,
                worldPose.origin().y,
                worldPose.origin().z,
                assemblyFacing,
                worldPose.yawDegrees(),
                level.getGameTime()
        );
        return context;
    }

    public RadarWorldPose worldPoseAt(long gameTime) {
        Vec3 localOrigin = new Vec3(this.radarOriginX, this.radarOriginY, this.radarOriginZ);
        float localYaw = this.orientationState.yawAt(gameTime);
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return new RadarWorldPose(localOrigin, localYaw, false);
        }
        return RadarWorldPoseResolver.resolve(serverLevel, this.worldPosition, localOrigin, localYaw);
    }

    public BlockPos worldControllerPos() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return this.worldPosition;
        }
        RadarWorldPose pose = RadarWorldPoseResolver.resolve(
                serverLevel,
                this.worldPosition,
                Vec3.atCenterOf(this.worldPosition),
                RadarGeometry.yawDegrees(this.radarFacing)
        );
        return BlockPos.containing(pose.origin());
    }

    private void ensureRadarId(ServerLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        if (this.radarId == null || !this.radarId.dimensionId().equals(dimensionId)) {
            this.radarId = new RadarId(dimensionId, this.worldPosition);
        }
    }

    private void logOptimizationScanDecision(
            ServerLevel level,
            boolean structureValid,
            RadarStructureType structureType,
            int nextBasicPanelCount,
            int nextOverviewModuleCount,
            int nextBaseRange,
            int nextRange,
            PowerRadarCeeState nextElectricalState,
            String path
    ) {
        if (!PowerRadarDebugOptions.scanOptimizationLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar] Scan optimization controller radarId={} tick={} path={} structureValid={} assembled={} structure={} panels={} overviewModules={} baseRange={} range={} electricalState={} voltage={} cache={}",
                this.radarId(),
                level.getGameTime(),
                path,
                structureValid,
                this.cachedStructure != null && this.cachedStructure.assembled(),
                structureType,
                nextBasicPanelCount,
                nextOverviewModuleCount,
                nextBaseRange,
                nextRange,
                nextElectricalState,
                String.format(java.util.Locale.ROOT, "%.1f", this.cachedElectricalVoltageVolts),
                this.targetCache.size()
        );
    }

    public RadarId radarId() {
        if (this.radarId == null) {
            ResourceLocation dimensionId = this.level == null ? Level.OVERWORLD.location() : this.level.dimension().location();
            this.radarId = new RadarId(dimensionId, this.worldPosition);
        }
        return this.radarId;
    }

    public ResourceLocation dimensionId() {
        return this.radarId().dimensionId();
    }

    public RadarScanMode scanMode() {
        return this.scanMode;
    }

    public boolean assembled() {
        return this.assembled;
    }

    public int validPanelCount() {
        return this.validPanelCount;
    }

    public int basicPanelCount() {
        return this.basicPanelCount;
    }

    public int overviewModuleCount() {
        return this.overviewModuleCount;
    }

    public int currentRange() {
        return this.currentRange;
    }

    public int displayCurrentRange() {
        if (!this.assembled || this.validPanelCount <= 0 || !isScanPowered(this.electricalState)) {
            return 0;
        }
        int baseRange = this.orientationState.structureType() == RadarStructureType.OVERVIEW
                ? RadarScanner.calculateOverviewRange(this.scanMode, this.overviewModuleCount)
                : RadarScanner.calculateRange(this.scanMode, this.basicPanelCount);
        return (int) Math.floor(baseRange * PowerRadarCeeConstants.radarRangeMultiplier(this.cachedElectricalVoltageVolts));
    }

    public int baseStructureRange() {
        return this.baseStructureRange;
    }

    public int maxRange() {
        return this.orientationState.structureType() == RadarStructureType.OVERVIEW
                ? RadarScanner.calculateOverviewRange(this.scanMode, RadarModuleConstants.maxOverviewModules())
                : RadarScanner.calculateRange(this.scanMode, PowerRadarCeeConstants.maxRadarPanels());
    }

    public Direction radarFacing() {
        return this.radarFacing;
    }

    public RadarOrientationState orientationState() {
        return this.orientationState;
    }

    public double radarOriginX() {
        return this.radarOriginX;
    }

    public double radarOriginY() {
        return this.radarOriginY;
    }

    public double radarOriginZ() {
        return this.radarOriginZ;
    }

    public long lastScanGameTime() {
        return this.lastScanGameTime;
    }

    public long displayRevision() {
        return this.displayRevision;
    }

    public void forEachTargetTrack(Consumer<RadarTargetTrack> consumer) {
        this.targetCache.forEachTrack(consumer);
    }

    @Override
    public void forEachTrackedTarget(Consumer<? super TrackedTargetView> consumer) {
        this.targetCache.forEachTrack(consumer::accept);
    }

    @Override
    public void forEachTrackedTargetBySource(
            TargetSourceType sourceType,
            Consumer<? super TrackedTargetView> consumer
    ) {
        this.targetCache.forEachTrackBySource(sourceType, consumer::accept);
    }

    @javax.annotation.Nullable
    public RadarTargetTrack findTargetTrack(UUID targetUuid) {
        return this.targetCache.findByUuid(targetUuid);
    }

    @Override
    @javax.annotation.Nullable
    public TrackedTargetView findTrackedTarget(UUID targetUuid) {
        return this.findTargetTrack(targetUuid);
    }

    @javax.annotation.Nullable
    public RadarTargetTrack findFirstAutotargetTrack(int autotargetFilterMask) {
        final RadarTargetTrack[] match = new RadarTargetTrack[1];
        this.targetCache.forEachTrack(track -> {
            if (match[0] == null && RadarDetectionFilters.enabled(autotargetFilterMask, track.category()) && track.targetUuid() != null) {
                match[0] = track;
            }
        });
        return match[0];
    }

    public int cachedTargetCount() {
        return this.targetCache.size();
    }

    @Override
    public int trackedTargetCount() {
        return this.cachedTargetCount();
    }

    public double calculateElectricalResistanceOhms() {
        return this.electricalResistanceOhms();
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

    public boolean isElectricallyOperational() {
        return isScanPowered(this.electricalState);
    }

    public PowerRadarCeeState electricalState() {
        return this.electricalState;
    }

    public double voltageRangeMultiplier() {
        return PowerRadarCeeConstants.radarRangeMultiplier(electricalVoltageVolts());
    }

    public int effectiveScanRangeBlocks() {
        return this.currentRange;
    }

    @Override
    public RadarCoverage coverage() {
        RadarScanProfile profile = this.activeScanProfile != null
                ? this.activeScanProfile
                : this.buildScanProfile(this.currentRange);
        return new RadarCoverage(
                new Vec3(this.radarOriginX, this.radarOriginY, this.radarOriginZ),
                this.currentRange,
                profile.verticalMinOffset(),
                profile.verticalMaxOffset(),
                this.radarFacing,
                this.orientationState.yawAt(this.lastScanGameTime),
                profile.coverageShape(),
                this.scanMode,
                this.orientationState.structureType());
    }

    public int detectionFilterMask() {
        return this.detectionFilterMask;
    }

    private boolean isScanPowered(PowerRadarCeeState state) {
        return state == PowerRadarCeeState.POWERED;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.radar_controller")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("goggles.power_radar.radar_controller.status",
                Component.translatable(statusKey())));
        tooltip.add(Component.translatable("goggles.power_radar.radar_controller.scan_mode",
                Component.translatable(scanModeKey())));
        tooltip.add(Component.translatable("goggles.power_radar.radar_controller.range", this.currentRange));
        tooltip.add(Component.translatable("power_radar.electrical.state",
                Component.translatable(this.electricalState.translationKey())));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
        tooltip.add(Component.translatable("power_radar.electrical.panel_count", this.validPanelCount));
        tooltip.add(Component.translatable("power_radar.electrical.basic_panel_count", this.basicPanelCount));
        tooltip.add(Component.translatable("power_radar.electrical.effective_range", this.effectiveScanRangeBlocks()));
        return true;
    }

    private String statusKey() {
        if (!this.assembled) {
            return "goggles.power_radar.radar_controller.status.incomplete";
        }
        return this.currentRange > 0
                ? "goggles.power_radar.radar_controller.status.active"
                : "goggles.power_radar.radar_controller.status.inactive";
    }

    private String scanModeKey() {
        return switch (this.scanMode) {
            case GROUND -> "goggles.power_radar.radar_controller.scan_mode.ground";
            case SKY -> "goggles.power_radar.radar_controller.scan_mode.sky";
            case SURFACE_SCANNER -> "goggles.power_radar.radar_controller.scan_mode.surface_scanner";
        };
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("ScanMode", this.scanMode.name());
        tag.putBoolean("Assembled", this.assembled);
        tag.putInt("ValidPanelCount", this.validPanelCount);
        tag.putInt("BasicPanelCount", this.basicPanelCount);
        tag.putInt("OverviewModuleCount", this.overviewModuleCount);
        tag.putInt("BaseStructureRange", this.baseStructureRange);
        tag.putInt("CurrentRange", this.currentRange);
        tag.putInt("DetectionFilterMask", this.detectionFilterMask);
        tag.putString("RadarFacing", this.radarFacing.getName());
        tag.putString("RadarStructureType", this.orientationState.structureType().name());
        tag.putFloat("RadarYawDegrees", this.orientationState.referenceYawDegrees());
        tag.putFloat("RadarRotationSpeedDegreesPerTick", this.orientationState.rotationSpeedDegreesPerTick());
        tag.putLong("RadarRotationReferenceGameTime", this.orientationState.referenceGameTime());
        tag.putLong("LastScanGameTime", this.lastScanGameTime);
        tag.putString("ElectricalState", this.electricalState.name());
        tag.putDouble("ElectricalVoltageVolts", this.cachedElectricalVoltageVolts);
        tag.putDouble("ElectricalCurrentAmps", this.cachedElectricalCurrentAmps);
        tag.putDouble("ElectricalPowerWatts", this.cachedElectricalPowerWatts);
        tag.putDouble("ElectricalResistanceOhms", this.cachedElectricalResistanceOhms);
        if (this.radarStructureEntityUuid != null) {
            tag.putUUID("RadarStructureEntity", this.radarStructureEntityUuid);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.scanMode = RadarScanMode.byName(tag.getString("ScanMode"));
        this.assembled = tag.getBoolean("Assembled");
        this.validPanelCount = tag.getInt("ValidPanelCount");
        this.basicPanelCount = tag.contains("BasicPanelCount") ? tag.getInt("BasicPanelCount") : this.validPanelCount;
        this.overviewModuleCount = tag.getInt("OverviewModuleCount");
        this.baseStructureRange = tag.getInt("BaseStructureRange");
        this.currentRange = tag.getInt("CurrentRange");
        this.detectionFilterMask = tag.contains("DetectionFilterMask")
                ? RadarDetectionFilters.sanitize(tag.getInt("DetectionFilterMask"))
                : RadarDetectionFilters.DEFAULT_MASK;
        this.radarFacing = Direction.byName(tag.getString("RadarFacing"));
        if (this.radarFacing == null) {
            this.radarFacing = Direction.NORTH;
        }
        float yaw = tag.contains("RadarYawDegrees") ? tag.getFloat("RadarYawDegrees") : RadarGeometry.yawDegrees(this.radarFacing);
        float speed = tag.contains("RadarRotationSpeedDegreesPerTick") ? tag.getFloat("RadarRotationSpeedDegreesPerTick") : 0.0F;
        long referenceGameTime = tag.contains("RadarRotationReferenceGameTime") ? tag.getLong("RadarRotationReferenceGameTime") : this.lastScanGameTime;
        RadarStructureType structureType;
        try {
            structureType = tag.contains("RadarStructureType")
                    ? RadarStructureType.valueOf(tag.getString("RadarStructureType"))
                    : RadarStructureType.PHASED_ARRAY;
        } catch (IllegalArgumentException exception) {
            structureType = RadarStructureType.PHASED_ARRAY;
        }
        this.orientationState = new RadarOrientationState(structureType, yaw, speed, referenceGameTime);
        this.lastScanGameTime = tag.getLong("LastScanGameTime");
        try {
            this.electricalState = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            this.electricalState = this.assembled ? PowerRadarCeeState.UNDERVOLTAGE : PowerRadarCeeState.INVALID_STRUCTURE;
        }
        this.cachedElectricalVoltageVolts = safeSignedElectrical(tag.getDouble("ElectricalVoltageVolts"));
        this.cachedElectricalCurrentAmps = safeElectrical(tag.getDouble("ElectricalCurrentAmps"));
        this.cachedElectricalPowerWatts = safeElectrical(tag.getDouble("ElectricalPowerWatts"));
        this.cachedElectricalResistanceOhms = tag.contains("ElectricalResistanceOhms")
                ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ElectricalResistanceOhms"))
                : PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
        this.radarStructureEntityUuid = tag.hasUUID("RadarStructureEntity")
                ? tag.getUUID("RadarStructureEntity")
                : null;
        if (!clientPacket) {
            this.radarStructureEntitySyncPending = true;
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

    private void syncChanged() {
        if (this.removingOrUnloading) {
            return;
        }
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
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
            this.displayRevision++;
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

    private static ScanSlicePlanKey scanSlicePlanKey(RadarScanProfile profile, RadarScanContext context) {
        return new ScanSlicePlanKey(
                profile.range(),
                profile.verticalMinOffset(),
                profile.verticalMaxOffset(),
                context.radarOriginX(),
                context.radarOriginY(),
                context.radarOriginZ(),
                RadarConstants.entityQuerySliceSize());
    }

    private record ScanSlicePlanKey(
            int range,
            int verticalMinOffset,
            int verticalMaxOffset,
            double originX,
            double originY,
            double originZ,
            double sliceSize
    ) {
    }
}
