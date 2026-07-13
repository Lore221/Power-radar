package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.api.radar.RadarTargetingDataSource;
import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.api.weapon.WeaponKind;
import com.limbo2136.powerradar.api.weapon.WeaponMount;
import com.limbo2136.powerradar.block.TargetControllerBlock;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.TargetControllerCeeSnapshot;
import com.limbo2136.powerradar.integration.cbc.CbcWeaponAdapter;
import com.limbo2136.powerradar.targeting.TargetLeadSolver;
import com.limbo2136.powerradar.targeting.TargetingMath;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public class TargetControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final double CBC_CANNON_AIM_ORIGIN_Y_OFFSET = 2.0;
    private static final int TARGET_LOCK_WARMUP_TICKS = 3;
    private static final int BIG_CANNON_FIRE_RETRY_INTERVAL_TICKS = 5;
    private static final long AIM_ANGLE_RESYNC_TICKS = 40L;
    private static final long MOUNT_CACHE_RESYNC_TICKS = 40L;
    private static final long TARGET_LEAD_CACHE_TICKS = 3L;
    private static final double TARGET_LEAD_CACHE_AIM_SHIFT_SQR = 0.25;
    private static final double TARGET_LEAD_CACHE_ORIGIN_SHIFT_SQR = 0.01;
    private static final long AUTOTARGET_READINESS_CACHE_TICKS = 20L;

    private boolean readyToFire;
    private double powerVoltageVolts;
    private double currentAmps;
    private double powerWatts;
    private float desiredYawDegrees;
    private float desiredPitchDegrees;
    private float currentYawDegrees;
    private float currentPitchDegrees;
    private double yawVelocityDegreesPerTick;
    private double pitchVelocityDegreesPerTick;
    private UUID lastVisibilityTargetUuid;
    private long lastVisibilityCheckGameTime = Long.MIN_VALUE;
    private boolean lastTargetVisible;
    private WeaponBallistics lastKnownBallistics;
    private String lastKnownBallisticsCannonKind = "none";
    private UUID lockedTargetUuid;
    private int targetLockTicks;
    private int bigCannonFireRetryTicks;
    private FireStatus fireStatus = FireStatus.NO_TARGET;
    private long lastDiagnosticSyncGameTime = Long.MIN_VALUE;
    private BlockPos lastMissingWeaponMountPos;
    private long lastMissingWeaponMountGameTime = Long.MIN_VALUE;
    private UUID cachedAutotargetUuid;
    private boolean cachedAutotargetAvailable;
    private long lastAutotargetSnapshotGameTime = Long.MIN_VALUE;
    private long lastAutotargetCacheResetGameTime = Long.MIN_VALUE;
    private final Map<UUID, AutotargetReadiness> autotargetReadinessCache = new HashMap<>();
    private BlockPos estimatedAimMountPos;
    private boolean hasEstimatedAimAngles;
    private float estimatedYawDegrees;
    private float estimatedPitchDegrees;
    private long lastAimAngleResyncGameTime = Long.MIN_VALUE;
    private BlockPos cachedBigCannonBallisticsMountPos;
    private WeaponBallistics cachedBigCannonBallistics;
    private long lastBigCannonBallisticsCacheGameTime = Long.MIN_VALUE;
    private BlockPos cachedWeaponKindMountPos;
    private WeaponKind cachedWeaponKind;
    private long lastWeaponKindCacheGameTime = Long.MIN_VALUE;
    private UUID cachedLeadTargetUuid;
    private BlockPos cachedLeadMountPos;
    private WeaponBallistics cachedLeadBallistics;
    private boolean cachedLeadPreferHighArc;
    private boolean cachedLeadAccelerationReady;
    private long cachedLeadGameTime = Long.MIN_VALUE;
    private Vec3 cachedLeadOrigin;
    private Vec3 cachedLeadBaseTargetPoint;
    private Vec3 cachedLeadAimOffset;
    private TargetLeadSolver.BallisticAim cachedLeadBallisticAim;
    private double cachedLeadFlightTicks;
    private boolean cachedLeadUsesAcceleration;
    private TrajectoryModeBehaviour trajectoryMode;
    private boolean cachedHighArcMode;

    public TargetControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TARGET_CONTROLLER.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        CenteredSideValueBoxTransform transform = new CenteredSideValueBoxTransform(
                TargetControllerBlockEntity::isTrajectoryPanelFace) {
            @Override
            public float getScale() {
                return 0.75F;
            }
        };
        this.trajectoryMode = new TrajectoryModeBehaviour(
                Component.translatable("message.power_radar.target_controller.trajectory"), this, transform);
        this.trajectoryMode.between(0, 1);
        this.trajectoryMode.value = 0;
        this.trajectoryMode.withCallback(value -> {
            this.cachedHighArcMode = value == 1;
            invalidateTargetLeadCache();
            setChanged();
        });
        behaviours.add(this.trajectoryMode);
    }

    private static boolean isTrajectoryPanelFace(BlockState state, Direction direction) {
        Direction facing = state.getValue(TargetControllerBlock.FACING);
        if (facing.getAxis().isVertical()) {
            return direction == Direction.EAST || direction == Direction.WEST;
        }
        return direction == facing.getClockWise() || direction == facing.getCounterClockWise();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TargetControllerBlockEntity controller) {
        if (level instanceof ServerLevel serverLevel) {
            controller.tick();
            controller.tickServer(serverLevel, state);
        }
    }

    public boolean readyToFire() {
        return this.readyToFire;
    }

    public void applyElectricalSnapshot(TargetControllerCeeSnapshot snapshot) {
        this.powerVoltageVolts = snapshot.powerVoltageVolts();
        this.currentAmps = snapshot.currentAmps();
        this.powerWatts = snapshot.powerWatts();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.cachedHighArcMode = this.trajectoryMode != null && this.trajectoryMode.getValue() == 1;
        // The firing output is transient. Restoring a saved high level would not create
        // the rising redstone edge that CBC big cannons require after a chunk reload.
        this.readyToFire = false;
        this.powerVoltageVolts = tag.getDouble("PowerVoltage");
        this.currentAmps = tag.getDouble("CurrentAmps");
        this.powerWatts = tag.getDouble("PowerWatts");
        this.desiredYawDegrees = tag.getFloat("DesiredYaw");
        this.desiredPitchDegrees = tag.getFloat("DesiredPitch");
        this.currentYawDegrees = tag.getFloat("CurrentYaw");
        this.currentPitchDegrees = tag.getFloat("CurrentPitch");
        try {
            this.fireStatus = FireStatus.valueOf(tag.getString("FireStatus"));
        } catch (IllegalArgumentException exception) {
            this.fireStatus = FireStatus.NO_TARGET;
        }
        invalidateEstimatedAimAngles();
        invalidateBigCannonBallisticsCache();
        invalidateWeaponKindCache();
        invalidateTargetLeadCache();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("ReadyToFire", false);
        tag.putDouble("PowerVoltage", this.powerVoltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("PowerWatts", this.powerWatts);
        tag.putFloat("DesiredYaw", this.desiredYawDegrees);
        tag.putFloat("DesiredPitch", this.desiredPitchDegrees);
        tag.putFloat("CurrentYaw", this.currentYawDegrees);
        tag.putFloat("CurrentPitch", this.currentPitchDegrees);
        tag.putString("FireStatus", this.fireStatus.name());
    }

    private void tickServer(ServerLevel level, BlockState state) {
        TargetSolution solution = solve(level, state);
        this.desiredYawDegrees = solution.desiredYawDegrees();
        this.desiredPitchDegrees = solution.desiredPitchDegrees();
        this.currentYawDegrees = solution.currentYawDegrees();
        this.currentPitchDegrees = solution.currentPitchDegrees();
        boolean powered = isPowerVoltageValid();
        boolean active = powered && solution.valid();
        AimStep step = active ? applyAimStep(level, solution) : AimStep.ZERO;
        if (!active) {
            this.yawVelocityDegreesPerTick = 0.0;
            this.pitchVelocityDegreesPerTick = 0.0;
        }
        boolean fireConditionsMet = active
                && solution.fireCapableContraptionPresent()
                && solution.ammunitionAvailable()
                && solution.targetVisible()
                && solution.targetReachable()
                && solution.targetOutsideMinimumDistance()
                && solution.targetLockTicks() >= TARGET_LOCK_WARMUP_TICKS
                && step.applied()
                && step.settled()
                && Math.abs(step.remainingYawErrorDegrees()) <= aimToleranceDegrees(solution)
                && Math.abs(step.remainingPitchErrorDegrees()) <= aimToleranceDegrees(solution);
        boolean nextFireOutput = updateFireOutput(solution, fireConditionsMet);
        this.fireStatus = determineFireStatus(solution, powered, step, fireConditionsMet);
        setReadyToFire(level, state, nextFireOutput);
        logDebug(solution, powered, active, step);
        setChanged();
        syncDiagnostics(level, state);
    }

    private boolean updateFireOutput(TargetSolution solution, boolean fireConditionsMet) {
        if (!fireConditionsMet) {
            this.bigCannonFireRetryTicks = 0;
            return false;
        }
        if (!"BIG_CANNON".equals(solution.cannonKind())) {
            this.bigCannonFireRetryTicks = 0;
            return true;
        }
        if (this.bigCannonFireRetryTicks > 0) {
            this.bigCannonFireRetryTicks--;
            return false;
        }
        invalidateBigCannonBallisticsCache();
        this.bigCannonFireRetryTicks = BIG_CANNON_FIRE_RETRY_INTERVAL_TICKS - 1;
        return true;
    }

    private TargetSolution solve(ServerLevel level, BlockState state) {
        if (!ModList.get().isLoaded("createbigcannons")) {
            return TargetSolution.invalid("cbc-missing");
        }
        RadarLinkConnectionResolver.Resolution linkResolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(level, this.worldPosition);
        if (linkResolution.status() != RadarLinkConnectionResolver.Status.SINGLE || linkResolution.link().networkId() == null) {
            return TargetSolution.invalid("no-radar-link");
        }
        UUID networkId = linkResolution.link().networkId();
        RadarNetworkManager networkManager = RadarNetworkManager.get(level.getServer());
        UUID selectedTarget = networkManager.selectedTargetUuid(networkId).orElse(null);
        boolean manualTarget = selectedTarget != null;
        int autotargetFilterMask = networkManager.autotargetFilterMask(networkId);
        if (!manualTarget && autotargetFilterMask == 0 && !networkManager.hasForcedAutotargetEntries(networkId)) {
            this.cachedAutotargetUuid = null;
            invalidateTargetLeadCache();
            return TargetSolution.invalid("no-target");
        }
        RadarNetworkManager.ControllersResolution controllerResolution = RadarNetworkManager.get(level.getServer())
                .resolveControllersForConsumer(
                        networkId,
                        GlobalPos.of(level.dimension(), linkResolution.link().getBlockPos()));
        RadarTargetingDataSource radarController = new CombinedRadarDataSource(controllerResolution.controllers());
        if (controllerResolution.status() != com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus.CONNECTED
                || controllerResolution.controllers().isEmpty()) {
            return TargetSolution.invalid("radar-offline");
        }
        TrackedTargetView track = selectedTarget == null ? null : radarController.findTrackedTarget(selectedTarget);
        if (manualTarget) {
            if (track == null) {
                invalidateTargetLeadCache();
                return TargetSolution.invalid("manual-target-unreachable");
            } else if (!isSelectedTargetAlive(level, track)) {
                networkManager.setSelectedTargetUuid(networkId, null);
                manualTarget = false;
                selectedTarget = null;
            } else if (track.classification() == TargetClassification.PROJECTILE) {
                networkManager.setSelectedTargetUuid(networkId, null);
                manualTarget = false;
                selectedTarget = null;
                track = null;
            }
        }
        if (!manualTarget || selectedTarget == null) {
            UUID previousAutotargetUuid = this.cachedAutotargetUuid;
            if (this.cachedAutotargetAvailable && previousAutotargetUuid != null) {
                track = radarController.findTrackedTarget(previousAutotargetUuid);
                if (track == null || !isSelectedTargetAlive(level, track)) track = null;
            } else {
                track = cachedAutotargetCandidate(
                        level, radarController, networkManager, networkId, autotargetFilterMask);
            }
            if (previousAutotargetUuid != null && track == null) {
                this.autotargetReadinessCache.clear();
                this.lastAutotargetCacheResetGameTime = level.getGameTime();
                this.cachedAutotargetAvailable = false;
            }
            selectedTarget = track == null ? null : track.targetUuid();
            this.cachedAutotargetUuid = selectedTarget;
        }
        if (manualTarget && (track == null || selectedTarget == null)) {
            this.cachedAutotargetUuid = null;
            invalidateTargetLeadCache();
            return TargetSolution.invalid("target-not-in-tracks");
        }
        net.minecraft.core.Direction facing = state.hasProperty(TargetControllerBlock.FACING)
                ? state.getValue(TargetControllerBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        BlockPos mountPos = this.worldPosition.relative(facing);
        Optional<WeaponMount> cannon = inspectWeaponMount(level, mountPos);
        if (cannon.isEmpty()) {
            invalidateEstimatedAimAngles();
            invalidateTargetLeadCache();
            return TargetSolution.invalid("no-cbc-mount");
        }
        WeaponMount cannonState = cannon.get();
        Vec3 origin = cannonAimOrigin(cannonState);
        boolean ammunitionAvailable = cannonState.kind() == WeaponKind.AUTOCANNON
                || cannonState.ballistics().available();
        WeaponBallistics aimBallistics = aimBallistics(cannonState);
        boolean preferHighArc = cannonState.kind() == WeaponKind.DROP_MORTAR
                || cannonState.kind() == WeaponKind.BIG_CANNON && this.cachedHighArcMode;
        if (!manualTarget) {
            long snapshotGameTime = Math.floorDiv(radarController.lastScanGameTime(), 5L);
            if (!this.cachedAutotargetAvailable
                    && snapshotGameTime != this.lastAutotargetSnapshotGameTime) {
                this.lastAutotargetSnapshotGameTime = snapshotGameTime;
                track = selectAutotargetForSnapshot(level, radarController, networkManager, networkId,
                        autotargetFilterMask, origin, aimBallistics, preferHighArc, cannonState.kind());
                selectedTarget = track == null ? null : track.targetUuid();
                this.cachedAutotargetUuid = selectedTarget;
            }
            if (track == null || selectedTarget == null) {
                this.cachedAutotargetUuid = null;
                invalidateTargetLeadCache();
                return TargetSolution.invalid("target-not-in-tracks");
            }
        }
        int lockTicks = updateTargetLock(selectedTarget);
        long gameTime = level.getGameTime();
        TrackedTargetView aimTrack = liveTargetView(level, track, gameTime);
        TargetLeadSolver.LeadSolution leadSolution = cachedLeadSolution(aimTrack, selectedTarget, cannonState.mountPos(), origin, aimBallistics,
                preferHighArc,
                lockTicks,
                gameTime);
        Vec3 target = leadSolution.aimPoint();
        Vec3 delta = target.subtract(origin);
        Vec3 stableYawOrigin = Vec3.atCenterOf(cannonState.mountPos())
                .add(0.0, CBC_CANNON_AIM_ORIGIN_Y_OFFSET, 0.0);
        Vec3 yawDelta = target.subtract(stableYawOrigin);
        double horizontal = TargetingMath.horizontalDistance(delta);
        float desiredYaw = TargetingMath.yawTo(yawDelta);
        TargetLeadSolver.BallisticAim ballisticAim = leadSolution.ballisticAim();
        float desiredPitch = ballisticAim.pitchDegrees();
        boolean targetVisible = !requiresDirectLineOfSight(cannonState.kind(), preferHighArc)
                || cachedTargetVisible(level, aimTrack, origin, gameTime);
        boolean targetReachable = ballisticAim.reachable()
                && TargetLeadSolver.withinLifetimeLimit(horizontal, delta.length(), aimBallistics);
        double targetDistance = TargetLeadSolver.currentTargetPoint(aimTrack).distanceTo(origin);
        double minimumFiringDistance = minimumFiringDistance(cannonState.kind());
        boolean targetOutsideMinimumDistance = targetDistance >= minimumFiringDistance;
        if (!manualTarget) {
            boolean available = targetVisible && targetReachable && targetOutsideMinimumDistance;
            if (this.cachedAutotargetAvailable && !available) {
                this.autotargetReadinessCache.clear();
                this.lastAutotargetCacheResetGameTime = gameTime;
            }
            this.cachedAutotargetAvailable = available;
        }
        AimAngles currentAngles = currentAimAngles(cannonState, level.getGameTime());
        float currentYaw = currentAngles.yawDegrees();
        float currentPitch = currentAngles.pitchDegrees();
        float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchError = Mth.wrapDegrees(desiredPitch - currentPitch);
        return new TargetSolution(
                true,
                "ok",
                selectedTarget,
                cannonState.kind().name(),
                cannonState.fireCapable(),
                cannonState,
                cannonState.mountPos(),
                leadMode(aimTrack.classification(), leadSolution.flightTicks(), leadSolution.usesAcceleration(), lockTicks),
                origin,
                target,
                targetVisible,
                targetReachable,
                targetOutsideMinimumDistance,
                ammunitionAvailable,
                ballisticMode(aimBallistics, ballisticAim, cannonState.ballistics()),
                targetDistance,
                minimumFiringDistance,
                lockTicks,
                desiredYaw,
                desiredPitch,
                currentYaw,
                currentPitch,
                yawError,
                pitchError);
    }

    private Optional<WeaponMount> inspectWeaponMount(ServerLevel level, BlockPos mountPos) {
        long gameTime = level.getGameTime();
        if (mountPos.equals(this.lastMissingWeaponMountPos) && gameTime - this.lastMissingWeaponMountGameTime < 10L) {
            return Optional.empty();
        }
        WeaponBallistics cachedBallistics = cachedBigCannonBallistics(mountPos, gameTime);
        WeaponKind cachedKind = cachedWeaponKind(mountPos, gameTime);
        Optional<WeaponMount> mount = CbcWeaponAdapter.inspectForPreciseTargeting(
                level,
                mountPos,
                cachedBallistics,
                cachedKind);
        if (mount.isEmpty()) {
            this.lastMissingWeaponMountPos = mountPos.immutable();
            this.lastMissingWeaponMountGameTime = gameTime;
            if (mountPos.equals(this.cachedBigCannonBallisticsMountPos)) {
                invalidateBigCannonBallisticsCache();
            }
            if (mountPos.equals(this.cachedWeaponKindMountPos)) {
                invalidateWeaponKindCache();
            }
        } else {
            this.lastMissingWeaponMountPos = null;
            this.lastMissingWeaponMountGameTime = Long.MIN_VALUE;
            WeaponMount cannonState = mount.get();
            if (cachedBallistics == null) {
                rememberAimBallistics(cannonState, gameTime);
            }
            if (cachedKind == null) {
                rememberWeaponKind(cannonState, gameTime);
            }
        }
        return mount;
    }

    @javax.annotation.Nullable
    private TrackedTargetView cachedAutotargetCandidate(
            ServerLevel level,
            RadarTargetingDataSource radarController,
            RadarNetworkManager networkManager,
            UUID networkId,
            int autotargetFilterMask
    ) {
        if (this.cachedAutotargetUuid == null) {
            return null;
        }
        TrackedTargetView track = radarController.findTrackedTarget(this.cachedAutotargetUuid);
        if (track == null
                    || !autotargetPermitted(networkManager, networkId, autotargetFilterMask, track)
                    || !isSelectedTargetAlive(level, track)) {
            this.cachedAutotargetUuid = null;
            return null;
        }
        return track;
    }

    @javax.annotation.Nullable
    private TrackedTargetView selectAutotargetForSnapshot(
            ServerLevel level,
            RadarTargetingDataSource radarController,
            RadarNetworkManager networkManager,
            UUID networkId,
            int autotargetFilterMask,
            Vec3 origin,
            WeaponBallistics aimBallistics,
            boolean preferHighArc,
            WeaponKind cannonKind
    ) {
        long gameTime = level.getGameTime();
        if (this.lastAutotargetCacheResetGameTime == Long.MIN_VALUE
                || gameTime - this.lastAutotargetCacheResetGameTime >= AUTOTARGET_READINESS_CACHE_TICKS) {
            this.autotargetReadinessCache.clear();
            this.lastAutotargetCacheResetGameTime = gameTime;
        }
        final TrackedTargetView[] readyMatch = new TrackedTargetView[1];
        Set<UUID> present = new HashSet<>();
        radarController.forEachTrackedTarget(track -> {
            UUID uuid = track.targetUuid();
            if (uuid == null || !autotargetPermitted(networkManager, networkId, autotargetFilterMask, track)
                    || !isSelectedTargetAlive(level, track)) return;
            present.add(uuid);
            if (readyMatch[0] != null) return;
            AutotargetReadiness cached = this.autotargetReadinessCache.get(uuid);
            boolean ready;
            if (cached != null && gameTime - cached.checkedGameTime() < AUTOTARGET_READINESS_CACHE_TICKS) {
                ready = cached.ready();
            } else {
                ready = autotargetReady(level, track, origin, aimBallistics, preferHighArc, cannonKind);
                this.autotargetReadinessCache.put(uuid, new AutotargetReadiness(gameTime, ready));
            }
            if (ready) readyMatch[0] = track;
        });
        this.autotargetReadinessCache.keySet().retainAll(present);
        this.cachedAutotargetAvailable = readyMatch[0] != null;
        return readyMatch[0];
    }

    private boolean autotargetReady(
            ServerLevel level,
            TrackedTargetView track,
            Vec3 origin,
            WeaponBallistics aimBallistics,
            boolean preferHighArc,
        WeaponKind cannonKind
    ) {
        TrackedTargetView aimTrack = liveTargetView(level, track, level.getGameTime());
        if (!preferHighArc && !hasLineOfSightToTrack(level, origin, aimTrack)) {
            return false;
        }
        if (TargetLeadSolver.currentTargetPoint(aimTrack).distanceTo(origin) < minimumFiringDistance(cannonKind)) {
            return false;
        }
        TargetLeadSolver.LeadSolution leadSolution = solveLead(
                aimTrack, origin, aimBallistics, preferHighArc, TARGET_LOCK_WARMUP_TICKS, level.getGameTime());
        Vec3 delta = leadSolution.aimPoint().subtract(origin);
        TargetLeadSolver.BallisticAim aim = leadSolution.ballisticAim();
        return aim.reachable()
                && TargetLeadSolver.withinLifetimeLimit(TargetingMath.horizontalDistance(delta), delta.length(), aimBallistics);
    }

    private static String targetDisplayName(TrackedTargetView track) {
        return track.displayName() == null || track.displayName().isBlank()
                ? track.entityTypeId().getPath()
                : track.displayName();
    }

    private static double minimumFiringDistance(WeaponKind cannonKind) {
        return cannonKind == WeaponKind.AUTOCANNON
                ? PowerRadarServerConfig.autocannonMinFiringDistanceBlocks()
                : PowerRadarServerConfig.bigCannonMinFiringDistanceBlocks();
    }

    private AimStep applyAimStep(ServerLevel level, TargetSolution solution) {
        double targetMaxStep = maxStepDegreesPerTick();
        if (targetMaxStep <= 0.0 || solution.mountPos() == null) {
            return AimStep.ZERO;
        }
        double acceleration = accelerationDegreesPerTickSquared();
        double targetYawVelocity = targetVelocityForError(solution.yawErrorDegrees(), targetMaxStep);
        double targetPitchVelocity = targetVelocityForError(solution.pitchErrorDegrees(), targetMaxStep);
        this.yawVelocityDegreesPerTick = approach(this.yawVelocityDegreesPerTick, targetYawVelocity, acceleration);
        this.pitchVelocityDegreesPerTick = approach(this.pitchVelocityDegreesPerTick, targetPitchVelocity, acceleration);
        float yawStep = (float) clamp(this.yawVelocityDegreesPerTick, -Math.abs(solution.yawErrorDegrees()), Math.abs(solution.yawErrorDegrees()));
        float pitchStep = (float) clamp(this.pitchVelocityDegreesPerTick, -Math.abs(solution.pitchErrorDegrees()), Math.abs(solution.pitchErrorDegrees()));
        float nextYaw = TargetingMath.normalize360(solution.currentYawDegrees() + yawStep);
        float nextPitch = solution.currentPitchDegrees() + pitchStep;
        boolean applied = CbcWeaponAdapter.applyAdjustableMountAngles(level, solution.mount(), nextYaw, nextPitch);
        if (applied) {
            recordEstimatedAimAngles(solution.mountPos(), nextYaw, nextPitch);
        } else {
            invalidateEstimatedAimAngles();
        }
        float remainingYawError = Mth.wrapDegrees(solution.desiredYawDegrees() - nextYaw);
        float remainingPitchError = Mth.wrapDegrees(solution.desiredPitchDegrees() - nextPitch);
        boolean settled = Math.abs(this.yawVelocityDegreesPerTick) <= PowerRadarCeeConstants.TARGET_CONTROLLER_READY_MAX_SPEED_DEGREES_PER_TICK
                && Math.abs(this.pitchVelocityDegreesPerTick) <= PowerRadarCeeConstants.TARGET_CONTROLLER_READY_MAX_SPEED_DEGREES_PER_TICK;
        return new AimStep(applied, yawStep, pitchStep, nextYaw, nextPitch, remainingYawError, remainingPitchError, settled);
    }

    private static boolean autotargetPermitted(RadarNetworkManager manager, UUID networkId, int mask, TrackedTargetView track) {
        String name = targetDisplayName(track);
        boolean sable = track.classification() == TargetClassification.STRUCTURE;
        if (manager.isAutotargetExcluded(networkId, name, sable)) return false;
        return manager.isAutotargetForced(networkId, name, sable) || autotargetEnabled(mask, track);
    }

    private AimAngles currentAimAngles(WeaponMount cannonState, long gameTime) {
        BlockPos mountPos = cannonState.mountPos();
        if (this.hasEstimatedAimAngles
                && mountPos.equals(this.estimatedAimMountPos)
                && gameTime - this.lastAimAngleResyncGameTime < AIM_ANGLE_RESYNC_TICKS) {
            return new AimAngles(this.estimatedYawDegrees, this.estimatedPitchDegrees);
        }
        float yaw = TargetingMath.normalize360(cannonState.currentYawDegrees());
        float pitch = cannonState.currentPitchDegrees();
        recordEstimatedAimAngles(mountPos, yaw, pitch);
        this.lastAimAngleResyncGameTime = gameTime;
        return new AimAngles(yaw, pitch);
    }

    private void recordEstimatedAimAngles(BlockPos mountPos, float yawDegrees, float pitchDegrees) {
        this.estimatedAimMountPos = mountPos.immutable();
        this.hasEstimatedAimAngles = true;
        this.estimatedYawDegrees = TargetingMath.normalize360(yawDegrees);
        this.estimatedPitchDegrees = pitchDegrees;
    }

    private void invalidateEstimatedAimAngles() {
        this.estimatedAimMountPos = null;
        this.hasEstimatedAimAngles = false;
        this.lastAimAngleResyncGameTime = Long.MIN_VALUE;
    }

    private double maxStepDegreesPerTick() {
        double voltage = Math.abs(this.powerVoltageVolts);
        if (!isPowerVoltageValid()) {
            return 0.0;
        }
        double denominator = Math.max(0.001,
                PowerRadarCeeConstants.TARGET_CONTROLLER_FULL_SPEED_VOLTAGE - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE);
        double fraction = clamp((voltage - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE) / denominator, 0.0, 1.0);
        double rpm = PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM
                + (PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_RPM - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM) * fraction;
        return rpm / 60.0 * 360.0 / 20.0;
    }

    private boolean isPowerVoltageValid() {
        double voltage = Math.abs(this.powerVoltageVolts);
        return voltage >= PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE
                && voltage <= PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE;
    }

    private double currentAimRpm() {
        double maxStep = maxStepDegreesPerTick();
        return maxStep * 20.0 * 60.0 / 360.0;
    }

    private double currentYawRpm() {
        return Math.abs(this.yawVelocityDegreesPerTick) * 20.0 * 60.0 / 360.0;
    }

    private WeaponBallistics aimBallistics(WeaponMount cannonState) {
        WeaponBallistics current = cannonState.ballistics();
        String cannonKind = cannonState.kind().name();
        if (current.available()) {
            this.lastKnownBallistics = current;
            this.lastKnownBallisticsCannonKind = cannonKind;
            return current;
        }
        if (this.lastKnownBallistics != null && cannonKind.equals(this.lastKnownBallisticsCannonKind)) {
            return this.lastKnownBallistics;
        }
        return current;
    }

    @javax.annotation.Nullable
    private WeaponBallistics cachedBigCannonBallistics(BlockPos mountPos, long gameTime) {
        if (this.cachedBigCannonBallistics == null || !mountPos.equals(this.cachedBigCannonBallisticsMountPos)) {
            return null;
        }
        if (gameTime - this.lastBigCannonBallisticsCacheGameTime >= MOUNT_CACHE_RESYNC_TICKS) {
            return null;
        }
        return this.cachedBigCannonBallistics;
    }

    private void rememberAimBallistics(WeaponMount cannonState, long gameTime) {
        if (cannonState.kind() != WeaponKind.BIG_CANNON && cannonState.kind() != WeaponKind.AUTOCANNON) {
            return;
        }
        WeaponBallistics ballistics = cannonState.ballistics();
        if (!ballistics.available()) {
            return;
        }
        this.cachedBigCannonBallisticsMountPos = cannonState.mountPos().immutable();
        this.cachedBigCannonBallistics = ballistics;
        this.lastBigCannonBallisticsCacheGameTime = gameTime;
    }

    private void invalidateBigCannonBallisticsCache() {
        this.cachedBigCannonBallisticsMountPos = null;
        this.cachedBigCannonBallistics = null;
        this.lastBigCannonBallisticsCacheGameTime = Long.MIN_VALUE;
    }

    @javax.annotation.Nullable
    private WeaponKind cachedWeaponKind(BlockPos mountPos, long gameTime) {
        if (this.cachedWeaponKind == null || !mountPos.equals(this.cachedWeaponKindMountPos)) {
            return null;
        }
        if (gameTime - this.lastWeaponKindCacheGameTime >= MOUNT_CACHE_RESYNC_TICKS) {
            return null;
        }
        return this.cachedWeaponKind;
    }

    private void rememberWeaponKind(WeaponMount cannonState, long gameTime) {
        WeaponKind kind = cannonState.kind();
        if (kind == WeaponKind.NONE || kind == WeaponKind.UNKNOWN) {
            return;
        }
        this.cachedWeaponKindMountPos = cannonState.mountPos().immutable();
        this.cachedWeaponKind = kind;
        this.lastWeaponKindCacheGameTime = gameTime;
    }

    private void invalidateWeaponKindCache() {
        this.cachedWeaponKindMountPos = null;
        this.cachedWeaponKind = null;
        this.lastWeaponKindCacheGameTime = Long.MIN_VALUE;
    }

    private double accelerationDegreesPerTickSquared() {
        return PowerRadarCeeConstants.TARGET_CONTROLLER_ACCELERATION_RPM_PER_SECOND * 360.0 / 60.0 / 20.0 / 20.0;
    }

    private static double targetVelocityForError(double errorDegrees, double maxVelocityDegreesPerTick) {
        double targetVelocity = errorDegrees * PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK;
        return clamp(targetVelocity, -maxVelocityDegreesPerTick, maxVelocityDegreesPerTick);
    }

    private TargetLeadSolver.LeadSolution solveLead(
            TrackedTargetView track,
            Vec3 origin,
            WeaponBallistics ballistics,
            boolean preferHighArc,
            int lockTicks,
            long gameTime
    ) {
        return TargetLeadSolver.solve(track, origin, ballistics, preferHighArc, lockTicks, TARGET_LOCK_WARMUP_TICKS, gameTime);
    }

    private TargetLeadSolver.LeadSolution cachedLeadSolution(
            TrackedTargetView track,
            UUID targetUuid,
            BlockPos mountPos,
            Vec3 origin,
            WeaponBallistics ballistics,
            boolean preferHighArc,
            int lockTicks,
            long gameTime
    ) {
        Vec3 baseTargetPoint = TargetLeadSolver.currentTargetPoint(track);
        boolean accelerationReady = lockTicks >= TARGET_LOCK_WARMUP_TICKS;
        boolean reusable = targetUuid.equals(this.cachedLeadTargetUuid)
                && mountPos.equals(this.cachedLeadMountPos)
                && sameBallistics(ballistics, this.cachedLeadBallistics)
                && preferHighArc == this.cachedLeadPreferHighArc
                && accelerationReady == this.cachedLeadAccelerationReady
                && this.cachedLeadBaseTargetPoint != null
                && this.cachedLeadOrigin != null
                && this.cachedLeadAimOffset != null
                && gameTime - this.cachedLeadGameTime < TARGET_LEAD_CACHE_TICKS
                && origin.distanceToSqr(this.cachedLeadOrigin) <= TARGET_LEAD_CACHE_ORIGIN_SHIFT_SQR;
        if (reusable) {
            Vec3 cachedAimPoint = this.cachedLeadBaseTargetPoint.add(this.cachedLeadAimOffset);
            Vec3 currentAimPoint = baseTargetPoint.add(this.cachedLeadAimOffset);
            if (currentAimPoint.distanceToSqr(cachedAimPoint) <= TARGET_LEAD_CACHE_AIM_SHIFT_SQR
                    && this.cachedLeadBallisticAim != null) {
                return new TargetLeadSolver.LeadSolution(
                        currentAimPoint,
                        this.cachedLeadBallisticAim,
                        this.cachedLeadFlightTicks,
                        this.cachedLeadUsesAcceleration);
            }
        }
        TargetLeadSolver.LeadSolution solution = solveLead(
                track, origin, ballistics, preferHighArc, lockTicks, gameTime);
        rememberTargetLeadSolution(targetUuid, mountPos, origin, ballistics, preferHighArc,
                accelerationReady, gameTime, baseTargetPoint, solution);
        return solution;
    }

    private void rememberTargetLeadSolution(
            UUID targetUuid,
            BlockPos mountPos,
            Vec3 origin,
            WeaponBallistics ballistics,
            boolean preferHighArc,
            boolean accelerationReady,
            long gameTime,
            Vec3 baseTargetPoint,
            TargetLeadSolver.LeadSolution solution
    ) {
        this.cachedLeadTargetUuid = targetUuid;
        this.cachedLeadMountPos = mountPos.immutable();
        this.cachedLeadBallistics = ballistics;
        this.cachedLeadPreferHighArc = preferHighArc;
        this.cachedLeadAccelerationReady = accelerationReady;
        this.cachedLeadGameTime = gameTime;
        this.cachedLeadOrigin = origin;
        this.cachedLeadBaseTargetPoint = baseTargetPoint;
        this.cachedLeadAimOffset = solution.aimPoint().subtract(baseTargetPoint);
        this.cachedLeadBallisticAim = solution.ballisticAim();
        this.cachedLeadFlightTicks = solution.flightTicks();
        this.cachedLeadUsesAcceleration = solution.usesAcceleration();
    }

    private void invalidateTargetLeadCache() {
        this.cachedLeadTargetUuid = null;
        this.cachedLeadMountPos = null;
        this.cachedLeadBallistics = null;
        this.cachedLeadGameTime = Long.MIN_VALUE;
        this.cachedLeadOrigin = null;
        this.cachedLeadBaseTargetPoint = null;
        this.cachedLeadAimOffset = null;
        this.cachedLeadBallisticAim = null;
        this.cachedLeadFlightTicks = 0.0;
        this.cachedLeadUsesAcceleration = false;
        this.cachedLeadAccelerationReady = false;
    }

    private static boolean sameBallistics(WeaponBallistics left, WeaponBallistics right) {
        return left == right || (left != null && left.equals(right));
    }

    private static boolean autotargetEnabled(int mask, TrackedTargetView track) {
        return switch (track.classification()) {
            case HOSTILE_MOB -> isPhantom(track)
                    ? (mask & RadarDetectionFilters.TARGETING_PHANTOMS) != 0
                    : (mask & RadarDetectionFilters.HOSTILE_MOBS) != 0;
            case PASSIVE_MOB -> (mask & RadarDetectionFilters.PASSIVE_MOBS) != 0;
            case PLAYER -> (mask & RadarDetectionFilters.PLAYERS) != 0;
            case STRUCTURE -> (mask & RadarDetectionFilters.SABLE_STRUCTURES) != 0;
            case PROJECTILE -> false;
            case UNKNOWN -> false;
        };
    }

    private static boolean isPhantom(TrackedTargetView track) {
        return "minecraft".equals(track.entityTypeId().getNamespace())
                && "phantom".equals(track.entityTypeId().getPath());
    }

    private static String ballisticMode(
            WeaponBallistics profile,
            TargetLeadSolver.BallisticAim aim,
            WeaponBallistics currentProfile
    ) {
        if (profile == null) {
            return aim.mode();
        }
        if (!profile.available()) {
            return aim.mode() + "/profile=" + profile.mode();
        }
        return aim.mode()
                + "/v=" + round(profile.speedBlocksPerTick())
                + "/g=" + round(profile.gravityBlocksPerTickSquared())
                + "/drag=" + round(profile.drag())
                + "/barrels=" + profile.barrelCount()
                + "/life=" + profile.lifetimeTicks()
                + "/ammo=" + profile.ammunition()
                + (currentProfile != null && !currentProfile.available() ? "/cached/profile=" + currentProfile.mode() : "");
    }

    private static boolean requiresDirectLineOfSight(WeaponKind cannonKind, boolean preferHighArc) {
        return cannonKind == WeaponKind.AUTOCANNON
                || cannonKind == WeaponKind.BIG_CANNON && !preferHighArc;
    }

    private static boolean isSelectedTargetAlive(ServerLevel level, TrackedTargetView track) {
        if (!level.dimension().location().equals(track.dimensionId()) || track.targetUuid() == null) {
            return false;
        }
        Entity entity = level.getEntity(track.targetUuid());
        return entity != null && entity.isAlive();
    }

    private static TrackedTargetView liveTargetView(ServerLevel level, TrackedTargetView track, long gameTime) {
        if (!level.dimension().location().equals(track.dimensionId()) || track.targetUuid() == null) {
            return track;
        }
        Entity entity = level.getEntity(track.targetUuid());
        if (entity == null || !entity.isAlive()) {
            return track;
        }
        return new LiveTrackedTargetView(track, entity, gameTime);
    }

    private boolean cachedTargetVisible(ServerLevel level, TrackedTargetView track, Vec3 origin, long gameTime) {
        UUID targetUuid = track.targetUuid();
        if (targetUuid == null) {
            return false;
        }
        boolean sameTarget = targetUuid.equals(this.lastVisibilityTargetUuid);
        boolean fresh = sameTarget
                && gameTime - this.lastVisibilityCheckGameTime < PowerRadarCeeConstants.TARGET_CONTROLLER_VISIBILITY_CHECK_INTERVAL_TICKS;
        if (fresh) {
            return this.lastTargetVisible;
        }
        this.lastVisibilityTargetUuid = targetUuid;
        this.lastVisibilityCheckGameTime = gameTime;
        this.lastTargetVisible = hasLineOfSightToTrack(level, origin, track);
        return this.lastTargetVisible;
    }

    private static boolean hasLineOfSightToTrack(ServerLevel level, Vec3 origin, TrackedTargetView track) {
        double height = Math.max(0.1, track.boundingHeight());
        double[] heightFactors = { 0.2, 0.5, 0.9 };
        Vec3 position = track.position();
        for (double factor : heightFactors) {
            Vec3 targetPoint = new Vec3(position.x, position.y + height * factor, position.z);
            if (hasClearLine(level, origin, targetPoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasClearLine(ServerLevel level, Vec3 origin, Vec3 targetPoint) {
        Vec3 delta = targetPoint.subtract(origin);
        if (delta.lengthSqr() < 0.0001) {
            return true;
        }
        Vec3 start = origin.add(delta.normalize().scale(0.75));
        HitResult hit = level.clip(new ClipContext(
                start,
                targetPoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3 cannonAimOrigin(WeaponMount cannonState) {
        return cannonState.muzzleOrigin() == null
                ? Vec3.atCenterOf(cannonState.mountPos()).add(0.0, CBC_CANNON_AIM_ORIGIN_Y_OFFSET, 0.0)
                : cannonState.muzzleOrigin();
    }

    private static double aimToleranceDegrees(TargetSolution solution) {
        return "BIG_CANNON".equals(solution.cannonKind())
                ? 0.35
                : PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_TOLERANCE_DEGREES;
    }

    private FireStatus determineFireStatus(
            TargetSolution solution,
            boolean powered,
            AimStep step,
            boolean nextReady
    ) {
        if (nextReady) {
            return FireStatus.READY;
        }
        if (!powered) {
            double voltage = Math.abs(this.powerVoltageVolts);
            return voltage > PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE
                    ? FireStatus.OVERVOLTAGE
                    : FireStatus.UNDERVOLTAGE;
        }
        if (!solution.valid()) {
            return switch (solution.reason()) {
                case "no-radar-link" -> FireStatus.NO_RADAR_LINK;
                case "radar-offline" -> FireStatus.RADAR_OFFLINE;
                case "no-cbc-mount", "cbc-missing" -> FireStatus.NO_CANNON;
                case "manual-target-unreachable" -> FireStatus.TARGET_UNREACHABLE;
                default -> FireStatus.NO_TARGET;
            };
        }
        if (!solution.fireCapableContraptionPresent() || !step.applied()) {
            return FireStatus.CANNON_NOT_READY;
        }
        if (!solution.targetOutsideMinimumDistance()) {
            return FireStatus.TARGET_TOO_CLOSE;
        }
        if (!solution.ammunitionAvailable()) {
            return FireStatus.NO_AMMUNITION;
        }
        if (!solution.targetReachable()) {
            return FireStatus.TARGET_UNREACHABLE;
        }
        if (!solution.targetVisible()) {
            return FireStatus.TARGET_OBSTRUCTED;
        }
        if (solution.targetLockTicks() < TARGET_LOCK_WARMUP_TICKS) {
            return FireStatus.TARGET_LOCKING;
        }
        return FireStatus.AIMING;
    }

    private void syncDiagnostics(ServerLevel level, BlockState state) {
        long gameTime = level.getGameTime();
        if (gameTime - this.lastDiagnosticSyncGameTime < 10L) {
            return;
        }
        this.lastDiagnosticSyncGameTime = gameTime;
        level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.target_controller")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                "power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(this.powerVoltageVolts)));
        tooltip.add(Component.translatable(
                "power_radar.electrical.current",
                PowerRadarCeeFormatter.currentComponent(this.currentAmps)));
        tooltip.add(Component.translatable(
                "power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(this.powerWatts)));
        tooltip.add(Component.translatable(
                "goggles.power_radar.target_controller.fire_status",
                Component.translatable(this.fireStatus.translationKey())));
        return true;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private void setReadyToFire(ServerLevel level, BlockState state, boolean nextReady) {
        if (this.readyToFire == nextReady) {
            return;
        }
        this.readyToFire = nextReady;
        notifyRedstoneOutputChanged(level, state);
        if (PowerRadarDebugOptions.targetSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][TargetSystem] redstone pos={} ready={}",
                    this.worldPosition,
                    this.readyToFire);
        }
    }

    private void notifyRedstoneOutputChanged(ServerLevel level, BlockState state) {
        Block block = state.getBlock();
        level.updateNeighborsAt(this.worldPosition, block);
        level.updateNeighbourForOutputSignal(this.worldPosition, block);
        level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(this.worldPosition.relative(direction), block);
        }
        Direction facing = state.hasProperty(TargetControllerBlock.FACING)
                ? state.getValue(TargetControllerBlock.FACING)
                : Direction.NORTH;
        BlockPos firingOutputPos = this.worldPosition.relative(facing);
        level.updateNeighborsAt(firingOutputPos, block);
        level.updateNeighbourForOutputSignal(firingOutputPos, block);
    }

    private void logDebug(TargetSolution solution, boolean powered, boolean active, AimStep step) {
        if (!PowerRadarDebugOptions.targetSystemBugReportLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][TargetSystem] state pos={} reason={} fireStatus={} powered={} active={} target={} cannon={} lead={} ballistic={} ammo={} visible={} reachable={} outsideMinDistance={} distance={} minDistance={} origin={} aim={} desiredYaw={} currentYaw={} yawError={} desiredPitch={} currentPitch={} pitchError={} stepYaw={} stepPitch={} nextYaw={} nextPitch={} settled={} voltage={} targetRpm={} yawRpm={} ready={}",
                this.worldPosition,
                solution.reason(),
                this.fireStatus,
                powered,
                active,
                solution.targetUuid(),
                solution.cannonKind(),
                solution.leadMode(),
                solution.ballisticMode(),
                solution.ammunitionAvailable(),
                solution.targetVisible(),
                solution.targetReachable(),
                solution.targetOutsideMinimumDistance(),
                round(solution.targetDistanceBlocks()),
                round(solution.minimumFiringDistanceBlocks()),
                shortVec(solution.origin()),
                shortVec(solution.aimPoint()),
                round(solution.desiredYawDegrees()),
                round(solution.currentYawDegrees()),
                round(solution.yawErrorDegrees()),
                round(solution.desiredPitchDegrees()),
                round(solution.currentPitchDegrees()),
                round(solution.pitchErrorDegrees()),
                round(step.yawStepDegrees()),
                round(step.pitchStepDegrees()),
                round(step.nextYawDegrees()),
                round(step.nextPitchDegrees()),
                step.settled(),
                round(this.powerVoltageVolts),
                round(currentAimRpm()),
                round(currentYawRpm()),
                this.readyToFire);
        if (solution.valid()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][TargetSystem] aim pos={} cannon={} target={} desiredYaw={} currentYaw={} yawError={} desiredPitch={} currentPitch={} pitchError={} nextYaw={} nextPitch={} ballistic={} ammo={} visible={} reachable={} outsideMinDistance={} ready={}",
                    this.worldPosition,
                    solution.cannonKind(),
                    solution.targetUuid(),
                    round(solution.desiredYawDegrees()),
                    round(solution.currentYawDegrees()),
                    round(solution.yawErrorDegrees()),
                    round(solution.desiredPitchDegrees()),
                    round(solution.currentPitchDegrees()),
                    round(solution.pitchErrorDegrees()),
                    round(step.nextYawDegrees()),
                    round(step.nextPitchDegrees()),
                    solution.ballisticMode(),
                    solution.ammunitionAvailable(),
                    solution.targetVisible(),
                    solution.targetReachable(),
                    solution.targetOutsideMinimumDistance(),
                    this.readyToFire);
        }
    }

    private static String leadMode(TargetClassification classification, double flightTicks, boolean usesAcceleration, int lockTicks) {
        String suffix = "/tof=" + round(flightTicks)
                + "/lock=" + lockTicks
                + "/accel=" + usesAcceleration;
        return switch (classification) {
            case PASSIVE_MOB, HOSTILE_MOB, PLAYER, STRUCTURE -> "iterative_velocity" + suffix;
            case PROJECTILE -> "projectile_placeholder" + suffix;
            default -> "partial_velocity" + suffix;
        };
    }

    private static String shortVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return "(" + round(vec.x) + "," + round(vec.y) + "," + round(vec.z) + ")";
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double approach(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        if (current > target) {
            return Math.max(target, current - maxDelta);
        }
        return current;
    }

    private int updateTargetLock(UUID targetUuid) {
        if (!targetUuid.equals(this.lockedTargetUuid)) {
            this.lockedTargetUuid = targetUuid;
            this.targetLockTicks = 0;
            invalidateTargetLeadCache();
            return this.targetLockTicks;
        }
        this.targetLockTicks = Math.min(this.targetLockTicks + 1, 20_000);
        return this.targetLockTicks;
    }

    private void resetTargetLock() {
        this.lockedTargetUuid = null;
        this.targetLockTicks = 0;
        invalidateTargetLeadCache();
    }

    private record TargetSolution(
            boolean valid,
            String reason,
            UUID targetUuid,
            String cannonKind,
            boolean fireCapableContraptionPresent,
            WeaponMount mount,
            BlockPos mountPos,
            String leadMode,
            Vec3 origin,
            Vec3 aimPoint,
            boolean targetVisible,
            boolean targetReachable,
            boolean targetOutsideMinimumDistance,
            boolean ammunitionAvailable,
            String ballisticMode,
            double targetDistanceBlocks,
            double minimumFiringDistanceBlocks,
            int targetLockTicks,
            float desiredYawDegrees,
            float desiredPitchDegrees,
            float currentYawDegrees,
            float currentPitchDegrees,
            float yawErrorDegrees,
            float pitchErrorDegrees
    ) {
        private static TargetSolution invalid(String reason) {
            return new TargetSolution(false, reason, null, "none", false, null, null, "none", null, null,
                    false, false, false, false, "none", 0.0, 0.0, 0,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    private enum FireStatus {
        READY("goggles.power_radar.target_controller.fire_status.ready"),
        UNDERVOLTAGE("goggles.power_radar.target_controller.fire_status.undervoltage"),
        OVERVOLTAGE("goggles.power_radar.target_controller.fire_status.overvoltage"),
        NO_RADAR_LINK("goggles.power_radar.target_controller.fire_status.no_radar_link"),
        RADAR_OFFLINE("goggles.power_radar.target_controller.fire_status.radar_offline"),
        NO_CANNON("goggles.power_radar.target_controller.fire_status.no_cannon"),
        NO_TARGET("goggles.power_radar.target_controller.fire_status.no_target"),
        TARGET_TOO_CLOSE("goggles.power_radar.target_controller.fire_status.target_too_close"),
        NO_AMMUNITION("goggles.power_radar.target_controller.fire_status.no_ammunition"),
        TARGET_OBSTRUCTED("goggles.power_radar.target_controller.fire_status.target_obstructed"),
        TARGET_UNREACHABLE("goggles.power_radar.target_controller.fire_status.target_unreachable"),
        TARGET_LOCKING("goggles.power_radar.target_controller.fire_status.target_locking"),
        CANNON_NOT_READY("goggles.power_radar.target_controller.fire_status.cannon_not_ready"),
        AIMING("goggles.power_radar.target_controller.fire_status.aiming");

        private final String translationKey;

        FireStatus(String translationKey) {
            this.translationKey = translationKey;
        }

        private String translationKey() {
            return this.translationKey;
        }
    }

    private record AimStep(
            boolean applied,
            float yawStepDegrees,
            float pitchStepDegrees,
            float nextYawDegrees,
            float nextPitchDegrees,
            float remainingYawErrorDegrees,
            float remainingPitchErrorDegrees,
            boolean settled
    ) {
        private static final AimStep ZERO = new AimStep(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
    }

    private record AimAngles(float yawDegrees, float pitchDegrees) {
    }

    private record AutotargetReadiness(long checkedGameTime, boolean ready) {
    }

    private static class TrajectoryModeBehaviour extends ScrollValueBehaviour {
        private TrajectoryModeBehaviour(Component label, SmartBlockEntity blockEntity,
                                        CenteredSideValueBoxTransform transform) {
            super(label, blockEntity, transform);
            withFormatter(value -> Component.translatable(value == 1
                    ? "message.power_radar.target_controller.trajectory.high"
                    : "message.power_radar.target_controller.trajectory.flat").getString());
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(
                    this.label,
                    1,
                    1,
                    ImmutableList.of(Component.empty()),
                    new ValueSettingsFormatter(settings -> Component.translatable(settings.value() == 1
                            ? "message.power_radar.target_controller.trajectory.high"
                            : "message.power_radar.target_controller.trajectory.flat")));
        }
    }

    private record LiveTrackedTargetView(
            TrackedTargetView fallback,
            Entity entity,
            long gameTime
    ) implements TrackedTargetView {
        @Override
        public UUID targetUuid() {
            return this.fallback.targetUuid();
        }

        @Override
        public int targetId() {
            return this.entity.getId();
        }

        @Override
        public ResourceLocation entityTypeId() {
            return this.fallback.entityTypeId();
        }

        @Override
        public TargetSourceType sourceType() {
            return this.fallback.sourceType();
        }

        @Override
        public String displayName() {
            return this.fallback.displayName();
        }

        @Override
        public TargetClassification classification() {
            return this.fallback.classification();
        }

        @Override
        public ResourceLocation dimensionId() {
            return this.entity.level().dimension().location();
        }

        @Override
        public Vec3 position() {
            return this.entity.position();
        }

        @Override
        public Vec3 velocity() {
            return this.entity.getDeltaMovement();
        }

        @Override
        public boolean hasVelocity() {
            return true;
        }

        @Override
        public Vec3 acceleration() {
            return this.fallback.acceleration();
        }

        @Override
        public boolean hasAcceleration() {
            return this.fallback.hasAcceleration();
        }

        @Override
        public long firstSeenGameTime() {
            return this.fallback.firstSeenGameTime();
        }

        @Override
        public long lastSeenGameTime() {
            return this.gameTime;
        }

        @Override
        public long lastConfirmedAliveGameTime() {
            return this.gameTime;
        }

        @Override
        public double boundingHeight() {
            return Math.max(0.1D, this.entity.getBbHeight());
        }

        @Override
        public double approximateSize() {
            return Math.max(this.fallback.approximateSize(), Math.max(this.entity.getBbWidth(), this.entity.getBbHeight()));
        }
    }

}
