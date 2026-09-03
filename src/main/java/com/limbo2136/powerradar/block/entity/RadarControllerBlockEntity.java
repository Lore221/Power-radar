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
import com.limbo2136.powerradar.compat.create.RadarPanelContraption;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
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
import com.limbo2136.powerradar.radar.RadarAssemblyValidator;
import com.limbo2136.powerradar.radar.RadarScanCoordinator;
import com.limbo2136.powerradar.radar.RadarScanRequest;
import com.limbo2136.powerradar.radar.RadarSableCoverageAccumulator;
import com.limbo2136.powerradar.radar.RadarScanSlicePlan;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarScanProfile;
import com.limbo2136.powerradar.radar.RadarScanSlicePlanner;
import com.limbo2136.powerradar.radar.RadarStructure;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCache;
import com.limbo2136.powerradar.radar.RadarTargetTrack;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.RadarNetworkMember;
import com.limbo2136.powerradar.radar.network.RadarNetworkKind;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.network.RadarContraptionAnglePayload;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModEntities;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadarControllerBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, RadarTargetingDataSource, RadarNetworkMember, IControlContraption {
    private static final int REGULAR_DISCOVERY_WINDOW_MULTIPLIER = 5;
    private static final float ASSEMBLY_TILT_STEP_DEGREES = 1.0F;
    private static final float ASSEMBLY_TILT_DEGREES = 15.0F;
    private static final int MAX_MISSING_CONTRAPTION_VALIDATION_WINDOWS = 5;

    // Синхронизируемое состояние конструкции и последнего опубликованного сканирования.
    private RadarScanMode scanMode = RadarScanMode.GROUND;
    private boolean assembled;
    /** True after the player explicitly assembled this stationary radar. */
    private boolean assemblyConfirmed;
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
    private final RadarSableCoverageAccumulator activeScanSableCoverage =
            new RadarSableCoverageAccumulator();
    private boolean removingOrUnloading;
    private UUID networkId;

    // Электрический снимок изменяет дальность только при открытии следующего окна сканирования.
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
    private double cachedElectricalVoltageVolts;
    private double cachedElectricalCurrentAmps;
    private double cachedElectricalPowerWatts;
    private double cachedElectricalResistanceOhms = PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;

    // Окно делит поиск сущностей на бюджетные срезы, а публикацию выполняет строго в последний тик.
    private RadarScanProfile activeScanProfile;
    private RadarScanProfile activeRegularScanProfile;
    private RadarScanProfile activeFrequentScanProfile;
    private RadarScanProfile activeFrequentUnknownScanProfile;
    private RadarScanContext activeScanContext;
    private RadarScanSlicePlan activeScanSlicePlan;
    private ScanSlicePlanKey activeScanSlicePlanKey;
    private boolean activeScanPowered;
    private long displayRevision;
    private UUID radarStructureEntityUuid;
    private boolean radarStructureEntitySyncPending = true;
    private ControlledContraptionEntity panelContraption;
    private UUID panelContraptionUuid;
    private float panelContraptionTargetAngle;
    private boolean panelContraptionAnimating;
    private int missingPanelContraptionValidationWindows;

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
            if (this.networkId != null) {
                RadarNetworkManager.get(serverLevel.getServer()).loadRadarSource(
                        this.networkId, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
            }
            RadarPanelBlock.refreshControllerType(
                    serverLevel,
                    this.worldPosition,
                    this.assemblyConfirmed
                            ? RadarPanelBlock.fromScanMode(fixedScanMode())
                            : RadarPanelBlock.ControllerType.SURFACE);
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
    }

    @Override
    public void onChunkUnloaded() {
        this.removingOrUnloading = true;
        unregisterRadarSource();
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        this.removingOrUnloading = true;
        unregisterRadarSource();
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }

    @Override
    public UUID radarNetworkId() {
        return this.networkId;
    }

    public UUID ensureRadarNetworkId() {
        if (this.networkId == null && this.level instanceof ServerLevel serverLevel) {
            setRadarNetworkId(RadarNetworkManager.get(serverLevel.getServer()).createNetwork(radarNetworkKind()));
        }
        return this.networkId;
    }

    public RadarNetworkKind radarNetworkKind() {
        return this.getBlockState().getBlock() instanceof RadarControllerBlock controller
                ? controller.networkKind()
                : RadarNetworkKind.STANDARD;
    }

    public boolean canJoinRadarNetwork(UUID networkId) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return RadarNetworkManager.get(serverLevel.getServer())
                .canRadarSourceJoinNetwork(
                        networkId,
                        radarNetworkKind(),
                        GlobalPos.of(serverLevel.dimension(), this.worldPosition));
    }

    @Override
    public boolean createsRadarNetworkWhenUntuned() {
        return true;
    }

    @Override
    public void setRadarNetworkId(UUID networkId) {
        UUID replacement = networkId;
        if (replacement == null && this.level instanceof ServerLevel serverLevel) {
            replacement = RadarNetworkManager.get(serverLevel.getServer()).createNetwork(radarNetworkKind());
        }
        if (replacement != null && this.level instanceof ServerLevel serverLevel
                && !RadarNetworkManager.get(serverLevel.getServer())
                        .canRadarSourceJoinNetwork(
                                replacement,
                                radarNetworkKind(),
                                GlobalPos.of(serverLevel.dimension(), this.worldPosition))) {
            return;
        }
        if (java.util.Objects.equals(this.networkId, replacement)) {
            return;
        }
        UUID oldNetworkId = this.networkId;
        if (this.level instanceof ServerLevel serverLevel && oldNetworkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadRadarSource(
                    oldNetworkId, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
        this.networkId = replacement;
        if (this.level instanceof ServerLevel serverLevel && replacement != null) {
            RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
            manager.ensureNetwork(replacement);
            manager.loadRadarSource(replacement, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldNetworkId, replacement);
        syncChanged();
    }

    private void unregisterRadarSource() {
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadRadarSource(
                    this.networkId, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarControllerBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (blockEntity.removingOrUnloading) {
            return;
        }

        blockEntity.ensureRadarNetworkId();
        blockEntity.tick();
        blockEntity.tickPanelContraptionAnimation(serverLevel);
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
            // Границы окна фиксируют конструкцию, питание, фильтр и исходную позу одним снимком.
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
                // Для движущегося Sable план перестраивается только при изменении геометрического ключа.
                ScanSlicePlanKey tickSlicePlanKey = scanSlicePlanKey(this.activeScanProfile, tickContext);
                if (this.activeScanSlicePlan == null || !tickSlicePlanKey.equals(this.activeScanSlicePlanKey)) {
                    this.activeScanSlicePlan = RadarScanSlicePlanner.build(this.activeScanProfile, tickContext);
                    this.activeScanSlicePlanKey = tickSlicePlanKey;
                }
                boolean regularDiscovery = isRegularDiscoveryWindow(level.getGameTime(), updateIntervalTicks);
                boolean unknownDiscovery = isUnknownDiscoveryWindow(level.getGameTime(), updateIntervalTicks);
                discoveryProfile = regularDiscovery
                        ? (unknownDiscovery ? this.activeScanProfile : this.activeRegularScanProfile)
                        : (unknownDiscovery ? this.activeFrequentUnknownScanProfile : this.activeFrequentScanProfile);
                if (hasDiscoveryTargets(discoveryProfile)) {
                    List<AABB> slices = this.activeScanSlicePlan.slices();
                    int selectedCount = bucket >= slices.size()
                            ? 0
                            : (slices.size() - 1 - bucket) / scanSliceTicks + 1;
                    ArrayList<AABB> selected = new ArrayList<>(selectedCount);
                    // Индексы этого bucket образуют арифметическую прогрессию: нет смысла каждый тик
                    // обходить весь план и вычислять остаток для срезов остальных bucket.
                    for (int index = bucket; index < slices.size(); index += scanSliceTicks) {
                        selected.add(slices.get(index));
                    }
                    discoverySlices = selected;
                }
            }
            if (!discoverySlices.isEmpty() || publishTick) {
                // Даже пустой последний срез публикуется, чтобы устаревшие цели покинули кэш вовремя.
                RadarScanCoordinator.submit(level, new RadarScanRequest(
                        this.radarId(),
                        discoverySlices.isEmpty() ? null : discoveryProfile,
                        publishTick ? this.activeScanProfile : null,
                        tickContext,
                        this.targetCache,
                        this.activeScanSableCoverage,
                        discoverySlices,
                        publishTick,
                        publishTick ? () -> this.lastScanGameTime = tickContext.gameTime() : null));
            }
        } else if (publishTick && this.activeScanContext != null) {
            RadarScanContext tickContext = scanContextAt(level.getGameTime());
            RadarScanCoordinator.submit(level, new RadarScanRequest(
                    this.radarId(), null, null, tickContext, this.targetCache,
                    this.activeScanSableCoverage, List.of(), true,
                    () -> this.lastScanGameTime = tickContext.gameTime()));
        }
    }

    private int scanBucket(long gameTime, int scanWindowTicks) {
        return (int) Math.floorMod(gameTime, (long) scanWindowTicks);
    }

    private boolean isRegularDiscoveryWindow(long gameTime, int scanWindowTicks) {
        long window = Math.floorDiv(gameTime, scanWindowTicks);
        return Math.floorMod(window, REGULAR_DISCOVERY_WINDOW_MULTIPLIER) == 0;
    }

    private boolean isUnknownDiscoveryWindow(long gameTime, int scanWindowTicks) {
        long window = Math.floorDiv(gameTime, scanWindowTicks);
        long periodWindows = Math.max(1L,
                Math.ceilDiv(RadarConstants.RADAR_UNKNOWN_DISCOVERY_INTERVAL_TICKS, scanWindowTicks));
        long phase = Math.floorMod((long) this.radarId().hashCode(), periodWindows);
        return Math.floorMod(window, periodWindows) == phase;
    }

    private static boolean hasDiscoveryTargets(RadarScanProfile profile) {
        return profile.detectPlayers()
                || profile.detectHostileMobs()
                || profile.detectPassiveMobs()
                || profile.detectProjectiles()
                || profile.detectSableStructures()
                || profile.detectRadars()
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
                worldPose.forward(),
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
        this.activeScanSableCoverage.reset();
        // Режим задаёт тип блока, а display-карта сети фильтрует цели до их публикации.
        RadarScanMode blockMode = fixedScanMode();
        int networkDisplayMask = RadarNetworkManager.get(level.getServer()).displayFilterMaskForController(
                GlobalPos.of(level.dimension(), this.worldPosition));
        if (this.scanMode != blockMode || this.detectionFilterMask != networkDisplayMask) {
            this.scanMode = blockMode;
            this.detectionFilterMask = networkDisplayMask;
            this.targetCache.clear();
            this.activeScanProfile = null;
            this.activeRegularScanProfile = null;
            this.activeFrequentScanProfile = null;
            this.activeFrequentUnknownScanProfile = null;
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
            // Полная проверка многоблока выполняется редко. Обычный радар
            // начинает работать только после явной сборки; между проверками
            // используется сохранённый снимок структуры.
            if (blockMode == RadarScanMode.AIRCRAFT) {
                // Бортовой радар — уже готовый многоблочный блок и не имеет
                // отдельных панелей, которые нужно собирать вручную.
                this.assemblyConfirmed = true;
                this.cachedStructure = RadarAssemblyValidator.validate(level, this.worldPosition);
            } else if (this.assemblyConfirmed && this.cachedStructure != null
                    && this.cachedStructure.assembled()) {
                if (findPanelContraption(level) != null) {
                    // После сборки панели больше не являются блоками мира. Их
                    // неизменяемый снимок хранится в контроллере, а Create
                    // ContraptionEntity отвечает за рендер и столкновения.
                    this.missingPanelContraptionValidationWindows = 0;
                } else if (this.panelContraptionUuid != null
                        && this.missingPanelContraptionValidationWindows++
                                < MAX_MISSING_CONTRAPTION_VALIDATION_WINDOWS) {
                    // Entity loading can lag one or two ticks behind the block
                    // entity when a saved chunk is reopened. Keep the cached
                    // assembly for a few validation windows before declaring it
                    // lost.
                } else {
                    clearConfirmedAssembly(level);
                }
            } else {
                this.cachedStructure = RadarStructure.invalid(this.worldPosition);
            }
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
        boolean structureValid = this.assemblyConfirmed
                && this.cachedStructure.assembled()
                && nextPanelCount > 0;
        if (structureValid) {
            PowerRadarCeeIntegration.configureRadarLoad(level, this.worldPosition, true, nextStructureType,
                    nextBasicPanelCount, nextOverviewModuleCount);
        } else {
            PowerRadarCeeIntegration.configureRadarLoad(level, this.worldPosition, false, nextStructureType, 0, 0);
        }
        PowerRadarCeeState nextElectricalState = this.electricalState;

        if (structureValid) {
            nextBaseRange = nextStructureType == RadarStructureType.OVERVIEW
                    ? RadarAssemblyValidator.calculateOverviewRange(this.scanMode, nextOverviewModuleCount)
                    : RadarAssemblyValidator.calculateRange(this.scanMode, nextBasicPanelCount);
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
                        : RadarScanSlicePlanner.build(nextScanProfile, context);
                this.activeScanProfile = nextScanProfile;
                this.activeRegularScanProfile = nextScanProfile.regularDiscoveryOnly();
                this.activeFrequentScanProfile = nextScanProfile.frequentDiscoveryOnly();
                this.activeFrequentUnknownScanProfile = nextScanProfile.frequentAndUnknownDiscoveryOnly();
                this.activeScanContext = context;
                this.activeScanSlicePlan = nextSlicePlan;
                this.activeScanSlicePlanKey = nextSlicePlanKey;
                this.activeScanPowered = true;
            } else {
                logOptimizationScanDecision(level, structureValid, nextStructureType, nextBasicPanelCount, nextOverviewModuleCount,
                        nextBaseRange, 0, nextElectricalState, "housekeeping-power");
                this.activeScanProfile = null;
                this.activeRegularScanProfile = null;
                this.activeFrequentScanProfile = null;
                this.activeFrequentUnknownScanProfile = null;
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
            this.activeRegularScanProfile = null;
            this.activeFrequentScanProfile = null;
            this.activeFrequentUnknownScanProfile = null;
            this.activeScanContext = context;
            this.activeScanSlicePlan = null;
            this.activeScanSlicePlanKey = null;
            this.activeScanPowered = false;
        }

        boolean wasRadarActive = this.assembled && this.currentRange > 0;
        boolean changed = this.assembled != structureValid
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
        this.assembled = structureValid;
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

    /** Переключает явную сборку стационарной радарной конструкции. */
    public void toggleAssembly(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)
                || fixedScanMode() == RadarScanMode.AIRCRAFT) {
            return;
        }

        if (this.assemblyConfirmed) {
            disassemble(serverLevel);
            player.displayClientMessage(
                    Component.translatable("message.power_radar.radar_controller.disassembled"),
                    true);
            return;
        }

        RadarAssemblyValidator.Discovery discovery = RadarAssemblyValidator.discover(serverLevel, this.worldPosition);
        RadarStructure detectedStructure = discovery.structure();
        if (!detectedStructure.assembled()) {
            failAssembly(serverLevel, player);
            return;
        }

        List<BlockPos> assemblyPositions = discovery.capturedPositions();
        if (assemblyPositions.isEmpty()) {
            failAssembly(serverLevel, player);
            return;
        }

        RadarPanelBlock.applyControllerType(
                serverLevel,
                assemblyPositions,
                RadarPanelBlock.fromScanMode(fixedScanMode()));

        RadarPanelContraption contraption = new RadarPanelContraption(detectedStructure.facing());
        try {
            if (!contraption.assembleRadar(serverLevel, this.worldPosition, assemblyPositions)) {
                failAssembly(serverLevel, player);
                return;
            }
        } catch (AssemblyException | RuntimeException exception) {
            PowerRadar.LOGGER.error(
                    "[PowerRadar] Failed to assemble radar panel contraption at {}",
                    this.worldPosition,
                    exception);
            failAssembly(serverLevel, player);
            return;
        }

        // Remove the captured blocks first, exactly as Create's bearing
        // assembly does.  The controller remains in the world as the CEE and
        // radar-network anchor.
        contraption.removeBlocksFromWorld(serverLevel, BlockPos.ZERO);
        BlockPos anchor = this.worldPosition.above();
        ControlledContraptionEntity entity = ControlledContraptionEntity.create(serverLevel, this, contraption);
        entity.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        entity.setRotationAxis(detectedStructure.facing().getClockWise().getAxis());
        entity.setAngle(0.0F);
        if (!serverLevel.addFreshEntity(entity)) {
            contraption.addBlocksToWorld(
                    serverLevel,
                    new com.simibubi.create.content.contraptions.StructureTransform(BlockPos.ZERO, 0, 0, 0));
            failAssembly(serverLevel, player);
            return;
        }

        this.cachedStructure = detectedStructure;
        this.assemblyConfirmed = true;
        this.panelContraption = entity;
        this.panelContraptionUuid = entity.getUUID();
        this.panelContraptionTargetAngle = targetAssemblyAngle(
                detectedStructure.facing(),
                fixedScanMode());
        this.panelContraptionAnimating = this.panelContraptionTargetAngle != 0.0F;
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(serverLevel, this.worldPosition);
        this.ticksSinceStructureValidation = 0L;
        this.targetCache.clear();
        refreshScanWindow(serverLevel);
        this.radarStructureEntitySyncPending = true;
        syncChanged();
        player.displayClientMessage(
                Component.translatable("message.power_radar.radar_controller.assembled"),
                true);
    }

    private void failAssembly(ServerLevel level, Player player) {
        clearConfirmedAssembly(level);
        this.targetCache.clear();
        refreshScanWindow(level);
        player.displayClientMessage(
                Component.translatable("message.power_radar.radar_controller.assembly_failed"), true);
    }

    private void clearConfirmedAssembly(ServerLevel level) {
        this.assemblyConfirmed = false;
        this.cachedStructure = RadarStructure.invalid(this.worldPosition);
        this.panelContraption = null;
        this.panelContraptionUuid = null;
        this.panelContraptionTargetAngle = 0.0F;
        this.panelContraptionAnimating = false;
        this.missingPanelContraptionValidationWindows = 0;
        this.targetCache.clear();
        RadarPanelBlock.refreshControllerType(level, this.worldPosition, RadarPanelBlock.ControllerType.SURFACE);
    }

    private void disassemble(ServerLevel level) {
        disassemblePanelContraption(level);
        this.assemblyConfirmed = false;
        this.cachedStructure = RadarStructure.invalid(this.worldPosition);
        this.ticksSinceStructureValidation = 0L;
        this.missingPanelContraptionValidationWindows = 0;
        this.targetCache.clear();
        RadarPanelBlock.refreshControllerType(
                level,
                this.worldPosition,
                RadarPanelBlock.ControllerType.SURFACE);
        refreshScanWindow(level);
        this.radarStructureEntitySyncPending = true;
        syncChanged();
    }

    private void disassemblePanelContraption(ServerLevel level) {
        ControlledContraptionEntity entity = findPanelContraption(level);
        this.panelContraption = null;
        this.panelContraptionUuid = null;
        this.panelContraptionAnimating = false;
        this.panelContraptionTargetAngle = 0.0F;
        this.missingPanelContraptionValidationWindows = 0;
        if (entity != null && entity.isAlive()) {
            entity.disassemble();
        }
    }

    private ControlledContraptionEntity findPanelContraption(ServerLevel level) {
        if (this.panelContraption != null && this.panelContraption.isAlive()) {
            return this.panelContraption;
        }
        if (this.panelContraptionUuid != null
                && level.getEntity(this.panelContraptionUuid) instanceof ControlledContraptionEntity entity
                && entity.isAlive()) {
            this.panelContraption = entity;
            return entity;
        }
        return null;
    }

    private void tickPanelContraptionAnimation(ServerLevel level) {
        ControlledContraptionEntity entity = findPanelContraption(level);
        if (entity == null || !this.panelContraptionAnimating) {
            return;
        }

        float currentAngle = entity.getAngle(1.0F);
        float difference = this.panelContraptionTargetAngle - currentAngle;
        float nextAngle = Math.abs(difference) <= ASSEMBLY_TILT_STEP_DEGREES
                ? this.panelContraptionTargetAngle
                : currentAngle + Math.copySign(ASSEMBLY_TILT_STEP_DEGREES, difference);
        entity.setAngle(nextAngle);
        PacketDistributor.sendToPlayersTrackingEntity(
                entity,
                new RadarContraptionAnglePayload(entity.getId(), nextAngle));
        if (nextAngle == this.panelContraptionTargetAngle) {
            this.panelContraptionAnimating = false;
        }
    }

    private static float targetAssemblyAngle(Direction facing, RadarScanMode scanMode) {
        if (scanMode != RadarScanMode.SKY && scanMode != RadarScanMode.SURFACE_SCANNER) {
            return 0.0F;
        }
        // ControlledContraptionEntity stores only the axis (not its direction).
        // Compensate for that here so positive air-radar tilt always rotates
        // the panel normal toward +Y, regardless of its horizontal facing.
        float upwardSign = switch (facing) {
            case NORTH, EAST -> 1.0F;
            case SOUTH, WEST -> -1.0F;
            default -> 1.0F;
        };
        float directionSign = scanMode == RadarScanMode.SKY ? 1.0F : -1.0F;
        return directionSign * upwardSign * ASSEMBLY_TILT_DEGREES;
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

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return this.panelContraption == contraption;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        this.panelContraption = contraption;
        this.panelContraptionUuid = contraption.getUUID();
    }

    @Override
    public void onStall() {
        // Radar apertures do not have kinetic actors that can stall.
    }

    @Override
    public boolean isValid() {
        return !this.isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return this.worldPosition;
    }

    public void deactivateRadarStructureEntity() {
        if (this.level instanceof ServerLevel serverLevel) {
            disassemblePanelContraption(serverLevel);
            removeRadarStructureEntity(serverLevel);
        }
        this.assemblyConfirmed = false;
        this.assembled = false;
        this.cachedStructure = RadarStructure.invalid(this.worldPosition);
        this.validPanelCount = 0;
        this.basicPanelCount = 0;
        this.overviewModuleCount = 0;
        this.baseStructureRange = 0;
        this.currentRange = 0;
        this.panelContraptionAnimating = false;
        this.panelContraptionTargetAngle = 0.0F;
        this.missingPanelContraptionValidationWindows = 0;
        this.targetCache.clear();
        this.radarStructureEntitySyncPending = false;
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
                : this.orientationState.structureType() == RadarStructureType.AIRCRAFT
                        ? RadarScanProfile.aircraftController(range)
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
        return new RadarScanContext(
                level,
                level.dimension().location(),
                this.radarId,
                worldPose.origin().x,
                worldPose.origin().y,
                worldPose.origin().z,
                assemblyFacing,
                worldPose.yawDegrees(),
                worldPose.forward(),
                level.getGameTime()
        );
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
                ? RadarAssemblyValidator.calculateOverviewRange(this.scanMode, this.overviewModuleCount)
                : RadarAssemblyValidator.calculateRange(this.scanMode, this.basicPanelCount);
        return (int) Math.floor(baseRange * PowerRadarCeeConstants.radarRangeMultiplier(this.cachedElectricalVoltageVolts));
    }

    public int baseStructureRange() {
        return this.baseStructureRange;
    }

    public int maxRange() {
        return this.orientationState.structureType() == RadarStructureType.OVERVIEW
                ? RadarAssemblyValidator.calculateOverviewRange(this.scanMode, RadarModuleConstants.maxOverviewModules())
                : RadarAssemblyValidator.calculateRange(this.scanMode, PowerRadarCeeConstants.maxRadarPanels());
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
        int firstNewLine = tooltip.size();
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.RADAR_CONTROLLER)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> PowerRadarTooltipSettings.appendElectricalStatisticsTitle(tooltip);
                case STATUS -> tooltip.add(Component.translatable("goggles.power_radar.radar_controller.status",
                        Component.translatable(statusKey())));
                case SCAN_MODE -> tooltip.add(Component.translatable("goggles.power_radar.radar_controller.scan_mode",
                        Component.translatable(scanModeKey())));
                case ELECTRICAL_STATE -> tooltip.add(Component.translatable("power_radar.electrical.state",
                        Component.translatable(this.electricalState.translationKey())));
                case VOLTAGE -> tooltip.add(Component.translatable("power_radar.electrical.voltage",
                        PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
                case POWER -> tooltip.add(Component.translatable("power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
                case EFFECTIVE_RANGE -> tooltip.add(Component.translatable(
                        "power_radar.electrical.effective_range", this.effectiveScanRangeBlocks()));
                default -> { }
            }
        }
        return PowerRadarTooltipSettings.finishGoggleTooltip(tooltip, firstNewLine);
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
            case AIRCRAFT -> "goggles.power_radar.radar_controller.scan_mode.aircraft";
        };
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.networkId != null) {
            tag.putUUID("PowerRadarNetworkId", this.networkId);
        }
        tag.putString("ScanMode", this.scanMode.name());
        tag.putBoolean("AssemblyConfirmed", this.assemblyConfirmed);
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
        if (this.panelContraptionUuid != null) {
            tag.putUUID("PanelContraptionEntity", this.panelContraptionUuid);
        }
        tag.putFloat("PanelContraptionTargetAngle", this.panelContraptionTargetAngle);
        tag.putBoolean("PanelContraptionAnimating", this.panelContraptionAnimating);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        UUID oldNetworkId = this.networkId;
        super.read(tag, registries, clientPacket);
        this.networkId = tag.hasUUID("PowerRadarNetworkId")
                ? tag.getUUID("PowerRadarNetworkId")
                : null;
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldNetworkId, this.networkId);
        this.scanMode = RadarScanMode.byName(tag.getString("ScanMode"));
        // Older saves only stored the automatically detected state. That
        // state is intentionally not treated as an explicit assembly.
        this.assemblyConfirmed = tag.getBoolean("AssemblyConfirmed");
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
        this.panelContraptionUuid = tag.hasUUID("PanelContraptionEntity")
                ? tag.getUUID("PanelContraptionEntity")
                : null;
        float savedPanelContraptionTargetAngle = tag.contains("PanelContraptionTargetAngle")
                ? tag.getFloat("PanelContraptionTargetAngle")
                : 0.0F;
        // The tilt is a code-defined presentation setting. Recalculate it so
        // an already saved radar also picks up a changed angle (for example,
        // the 10° -> 15° adjustment) instead of keeping the old NBT value.
        this.panelContraptionTargetAngle = targetAssemblyAngle(this.radarFacing, this.scanMode);
        this.panelContraptionAnimating = this.assemblyConfirmed
                && this.assembled
                && (tag.getBoolean("PanelContraptionAnimating")
                        || Float.compare(savedPanelContraptionTargetAngle, this.panelContraptionTargetAngle) != 0);
        if (this.assemblyConfirmed && this.assembled && this.validPanelCount > 0) {
            this.cachedStructure = new RadarStructure(
                    true,
                    this.worldPosition,
                    this.worldPosition,
                    this.worldPosition.above(),
                    this.radarFacing,
                    this.basicPanelCount,
                    this.overviewModuleCount,
                    structureType,
                    this.orientationState);
        }
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
                : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
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
                profile.structureType(),
                profile.useFovCheck(),
                context.radarOriginX(),
                context.radarOriginY(),
                context.radarOriginZ(),
                profile.useFovCheck() ? context.radarYawDegrees() : 0.0F,
                profile.sectorAngle(),
                RadarConstants.entityQuerySliceSize());
    }

    private record ScanSlicePlanKey(
            int range,
            int verticalMinOffset,
            int verticalMaxOffset,
            RadarStructureType structureType,
            boolean useFovCheck,
            double originX,
            double originY,
            double originZ,
            float yawDegrees,
            int sectorAngle,
            double sliceSize
    ) {
    }
}
