package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.api.weapon.WeaponKind;
import com.limbo2136.powerradar.api.weapon.WeaponMount;
import com.limbo2136.powerradar.block.InterceptionControllerBlock;
import com.limbo2136.powerradar.bridge.InterceptionNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.compat.electroenergetics.InterceptionControllerCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.interception.InterceptionBallistics;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.interception.InterceptionPlan;
import com.limbo2136.powerradar.interception.SableInterceptSearch;
import com.limbo2136.powerradar.api.weapon.WeaponPitchLimits;
import java.util.function.BiPredicate;
import java.util.function.DoubleFunction;
import com.limbo2136.powerradar.integration.cbc.CbcWeaponAdapter;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import com.limbo2136.powerradar.targeting.TargetingMath;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class InterceptionControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    // Времена заданы в серверных тиках, углы — в градусах, скорости — в блоках за тик.
    private static final int MAX_INTERCEPTION_TICKS = 240;
    private static final int MIN_INTERCEPTION_TICKS = 4;
    private static final int INTERCEPT_COARSE_STEP_TICKS = 3;
    private static final int INTERCEPT_FAST_STEP_TICKS = 5;
    private static final int INTERCEPT_FAST_ROOT_ITERATIONS = 16;
    private static final int INTERCEPT_FAST_SECANT_ITERATIONS = 8;
    private static final int INTERCEPT_FAST_MINIMIZE_ITERATIONS = 12;
    private static final int SABLE_MUZZLE_PREDICTION_ITERATIONS = 2;
    private static final double MIN_LINEAR_DRAG = 1.0E-6;
    private static final double INTERCEPT_PITCH_HINT_RANGE_DEGREES = 10.0;
    private static final double MAX_TIMING_ERROR_TICKS = 3.0;
    private static final double AIM_TOLERANCE_DEGREES = 2.0;
    private static final double AIM_REACTION_TICKS = 1.0;
    private static final double CBC_FIRE_SIGNAL_DELAY_TICKS = 1.0;
    private static final long CONTROLLER_SNAPSHOT_REFRESH_TICKS = 10L;
    private static final long AIM_ANGLE_RESYNC_TICKS = 40L;
    private static final long MOUNT_CACHE_RESYNC_TICKS = 40L;
    private static final int UNSOLVED_ASSIGNMENT_RETRY_TICKS = 20;

    // Синхронизируемые питание, команда наведения и итоговый краснокаменный выход.
    private boolean readyToFire;
    private double powerVoltageVolts;
    private double currentAmps;
    private double powerWatts;
    private double yawVelocityDegreesPerTick;
    private double pitchVelocityDegreesPerTick;
    private UUID interceptionNetworkId;
    private UUID assignedThreatUuid;
    private float desiredYawDegrees;
    private float desiredPitchDegrees;
    private float currentYawDegrees;
    private float currentPitchDegrees;
    private double interceptTicks;
    private Status status = Status.NO_NETWORK;

    // Назначение угрозы и режим сопровождения принадлежат runtime-координатору и не сохраняются в NBT.
    private String lastNetworkStatus = "NO_NETWORK";
    private String lastSolveReason = "startup";
    private long lastClientSyncGameTime = Long.MIN_VALUE;
    private long lastPublishedThreatRevision = Long.MIN_VALUE;
    private long lastControllerSnapshotGameTime = Long.MIN_VALUE;
    private UUID trackingThreatUuid;
    private UUID unsolvedThreatUuid;
    private int consecutiveUnsolvedTicks;
    private InterceptionPlan interceptionPlan;
    @Nullable private SableInterceptSearch.Pose lastSableSearchPose;
    private UUID engagedThreatUuid;

    // Кэши CBC ограничивают дорогую инспекцию установки и периодически сверяются с живым mount.
    private BlockPos lastMissingWeaponMountPos;
    private long lastMissingWeaponMountGameTime = Long.MIN_VALUE;
    private BlockPos estimatedAimMountPos;
    private boolean hasEstimatedAimAngles;
    private float estimatedYawDegrees;
    private float estimatedPitchDegrees;
    private long lastAimAngleResyncGameTime = Long.MIN_VALUE;
    private BlockPos cachedAutocannonBallisticsMountPos;
    private WeaponBallistics cachedAutocannonBallistics;
    private long lastAutocannonBallisticsCacheGameTime = Long.MIN_VALUE;
    private BlockPos cachedWeaponKindMountPos;
    private WeaponKind cachedWeaponKind;
    private long lastWeaponKindCacheGameTime = Long.MIN_VALUE;

    // Поза дула и feed-forward выражены в мировых координатах, включая движение Sable.
    private Vec3 lastWorldMuzzle;
    private long lastWorldMuzzleGameTime = Long.MIN_VALUE;
    private Vec3 worldMuzzleVelocity = Vec3.ZERO;
    private UUID aimRateThreatUuid;
    private float lastDesiredYawDegrees;
    private float lastDesiredPitchDegrees;
    private long lastAimRateGameTime = Long.MIN_VALUE;
    private double yawFeedForwardDegreesPerTick;
    private double pitchFeedForwardDegreesPerTick;
    private long lastSableDebugLogGameTime = Long.MIN_VALUE;

    public InterceptionControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERCEPTION_CONTROLLER.get(), pos, state);
    }

    public void setInterceptionNetworkId(@Nullable UUID networkId) {
        if (java.util.Objects.equals(this.interceptionNetworkId, networkId)) {
            return;
        }
        UUID oldId = this.interceptionNetworkId;
        if (this.level instanceof ServerLevel serverLevel && oldId != null) {
            releaseAssignment(serverLevel);
        }
        this.interceptionNetworkId = networkId;
        InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldId, networkId);
        setChanged();
        sendData();
    }

    @Nullable
    public UUID interceptionNetworkId() {
        return this.interceptionNetworkId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        InterceptionNetworkNodeClientCacheBridge.onLoaded(
                this.level, this.worldPosition, this.interceptionNetworkId);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        InterceptionNetworkNodeClientCacheBridge.onLoaded(
                this.level, this.worldPosition, this.interceptionNetworkId);
    }

    @Override
    public void remove() {
        if (this.level instanceof ServerLevel serverLevel) {
            releaseAssignment(serverLevel);
        }
        InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level instanceof ServerLevel serverLevel) {
            releaseAssignment(serverLevel);
        }
        InterceptionNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    public static void serverTick(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockState state,
            InterceptionControllerBlockEntity controller
    ) {
        if (level instanceof ServerLevel serverLevel) {
            controller.tick();
            controller.tickServer(serverLevel, state);
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean readyToFire() {
        return this.readyToFire;
    }

    public void applyElectricalSnapshot(InterceptionControllerCeeSnapshot snapshot) {
        double voltage = finite(snapshot.powerVoltageVolts());
        double current = Math.abs(finite(snapshot.currentAmps()));
        double power = Math.max(0.0, finite(snapshot.powerWatts()));
        boolean changed = Math.abs(this.powerVoltageVolts - voltage) > 0.01
                || Math.abs(this.currentAmps - current) > 0.001
                || Math.abs(this.powerWatts - power) > 0.1;
        this.powerVoltageVolts = voltage;
        this.currentAmps = current;
        this.powerWatts = power;
        if (changed) {
            setChanged();
            if (this.level instanceof ServerLevel serverLevel) {
                BlockState state = getBlockState();
                serverLevel.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        // Расчёт решения отделён от применения приводов и публикации фронта сигнала.
        Solution solution = solve(level, state);
        this.desiredYawDegrees = solution.desiredYaw;
        this.desiredPitchDegrees = solution.desiredPitch;
        this.currentYawDegrees = solution.currentYaw;
        this.currentPitchDegrees = solution.currentPitch;
        this.interceptTicks = solution.interceptTicks;

        boolean powered = validVoltage();
        AimStep aim = powered && solution.valid ? applyAim(level, solution) : AimStep.ZERO;
        if (!powered || !solution.valid) {
            this.yawVelocityDegreesPerTick = 0.0;
            this.pitchVelocityDegreesPerTick = 0.0;
        }
        boolean nextReady = powered
                && solution.valid
                && solution.ammunitionAvailable
                && solution.clearShot
                && solution.timingError <= MAX_TIMING_ERROR_TICKS
                && aim.applied
                && Math.abs(aim.remainingYawError) <= AIM_TOLERANCE_DEGREES
                && Math.abs(aim.remainingPitchError) <= AIM_TOLERANCE_DEGREES;
        if (nextReady && this.interceptionNetworkId != null && this.assignedThreatUuid != null) {
            this.engagedThreatUuid = this.assignedThreatUuid;
            this.trackingThreatUuid = this.assignedThreatUuid;
            InterceptionCoordinator.LaunchDebugSnapshot debugSnapshot = null;
            if (PowerRadarDebugOptions.sableInterceptionDebugLogging()
                    && RadarWorldPoseResolver.isOnSableStructure(level, this.worldPosition)
                    && solution.cannonState() != null) {
                debugSnapshot = new InterceptionCoordinator.LaunchDebugSnapshot(
                        solution.muzzle(),
                        solution.aimPoint(),
                        solution.desiredYaw(),
                        solution.desiredPitch(),
                        solution.currentYaw(),
                        solution.currentPitch(),
                        solution.interceptTicks(),
                        solution.timingError(),
                        solution.cannonState().ballistics().speedBlocksPerTick());
            }
            InterceptionCoordinator.registerPendingLaunch(
                    level,
                    this.interceptionNetworkId,
                    this.worldPosition,
                    solution.muzzle,
                    this.assignedThreatUuid,
                    debugSnapshot);
        }
        setReadyToFire(level, state, nextReady);
        this.status = statusFor(solution, powered, nextReady);
        logControllerState(level, solution, aim, powered, nextReady);
        setChanged();
        syncClient(level, state);
    }

    private Solution solve(ServerLevel level, BlockState state) {
        // Сначала проверяются неизменяемые контракты: интеграция, сеть и питание.
        if (!CreateBigCannonsIntegration.isLoaded()) {
            this.lastSolveReason = "cbc-missing";
            releaseAssignment(level);
            return Solution.invalid();
        }
        UUID resolvedNetworkId = this.interceptionNetworkId;
        this.lastNetworkStatus = resolvedNetworkId == null ? "NO_NETWORK" : "NETWORK";
        if (resolvedNetworkId == null) {
            this.lastSolveReason = "no-network";
            releaseAssignment(level);
            return Solution.invalid();
        }
        if (!validVoltage()) {
            this.lastSolveReason = "voltage-invalid";
            releaseAssignment(level);
            return Solution.invalid();
        }

        long gameTime = level.getGameTime();
        long threatRevision = InterceptionCoordinator.threatRevision(
                level.getServer(), resolvedNetworkId, gameTime);
        this.assignedThreatUuid = InterceptionCoordinator.assignedThreat(
                level, resolvedNetworkId, this.worldPosition);
        boolean snapshotDue = gameTime - this.lastControllerSnapshotGameTime
                >= CONTROLLER_SNAPSHOT_REFRESH_TICKS;
        if (this.assignedThreatUuid == null
                && threatRevision == this.lastPublishedThreatRevision
                && !snapshotDue) {
            this.trackingThreatUuid = null;
            resetUnsolvedAssignment();
            clearInterceptionPlan();
            clearEngagement();
            this.lastSolveReason = "idle";
            return Solution.invalid();
        }

        Direction facing = state.getValue(InterceptionControllerBlock.FACING);
        BlockPos mountPos = this.worldPosition.relative(facing);
        // Снимок контроллера публикуется после проверки живого autocannon и его боеприпаса.
        Optional<WeaponMount> cannonOptional = inspectWeaponMount(level, mountPos);
        if (cannonOptional.isEmpty()) {
            this.lastSolveReason = "no-cbc-mount";
            releaseAssignment(level);
            rememberIdleInspectAttempt(threatRevision, gameTime);
            return Solution.invalid();
        }
        WeaponMount cannon = cannonOptional.get();
        if (!cannon.fireCapable()) {
            this.lastSolveReason = "no-cannon-contraption";
            releaseAssignment(level);
            rememberIdleInspectAttempt(threatRevision, gameTime);
            return Solution.invalid();
        }
        if (cannon.kind() != WeaponKind.AUTOCANNON) {
            this.lastSolveReason = "unsupported-cannon-kind";
            releaseAssignment(level);
            rememberIdleInspectAttempt(threatRevision, gameTime);
            return Solution.invalid();
        }
        WeaponBallistics interceptorBallistics = cannon.ballistics();
        if (!interceptorBallistics.available() || interceptorBallistics.speedBlocksPerTick() <= 0.001) {
            this.lastSolveReason = "no-ammunition";
            releaseAssignment(level);
            rememberIdleInspectAttempt(threatRevision, gameTime);
            return Solution.noAmmo(cannon);
        }
        AimAngles currentAngles = currentAimAngles(cannon, gameTime);
        Vec3 worldMuzzle = RadarWorldPoseResolver.worldPosition(
                level, this.worldPosition, cannon.muzzleOrigin());
        Vec3 launchMuzzle = predictedLaunchMuzzle(worldMuzzle, gameTime);
        Vec3 worldCurrentDirection = RadarWorldPoseResolver.worldDirection(
                level,
                this.worldPosition,
                TargetingMath.directionFromAngles(currentAngles.yawDegrees(), currentAngles.pitchDegrees()));
        float worldCurrentYaw = TargetingMath.yawTo(worldCurrentDirection);
        float worldCurrentPitch = pitchTo(worldCurrentDirection);
        if (threatRevision != this.lastPublishedThreatRevision
                || snapshotDue) {
            InterceptionCoordinator.publishControllerSnapshot(
                    level,
                    resolvedNetworkId,
                    this.worldPosition,
                    new InterceptionCoordinator.ControllerSnapshot(
                            launchMuzzle,
                            worldCurrentYaw,
                            worldCurrentPitch,
                            maxStepDegreesPerTick(),
                            interceptorBallistics.speedBlocksPerTick(),
                            true));
            this.lastPublishedThreatRevision = threatRevision;
            this.lastControllerSnapshotGameTime = gameTime;
        }
        this.assignedThreatUuid = InterceptionCoordinator.assignedThreat(
                level, resolvedNetworkId, this.worldPosition);
        if (this.assignedThreatUuid == null) {
            this.trackingThreatUuid = null;
            resetUnsolvedAssignment();
            clearInterceptionPlan();
            clearEngagement();
            this.lastSolveReason = "idle";
            return Solution.hold(cannon, currentAngles);
        }
        if (this.trackingThreatUuid != null
                && !this.trackingThreatUuid.equals(this.assignedThreatUuid)) {
            this.trackingThreatUuid = null;
            clearInterceptionPlan();
            clearEngagement();
        }
        if (this.interceptionPlan != null && !planMatches(this.assignedThreatUuid)) {
            clearInterceptionPlan();
        }
        ThreatSnapshot threatSnapshot = InterceptionCoordinator.threatSnapshot(
                level.getServer(),
                resolvedNetworkId,
                this.assignedThreatUuid,
                level.getGameTime());
        if (threatSnapshot == null) {
            rejectAssignment(level);
            this.lastSolveReason = "invalid-threat-snapshot";
            return Solution.invalid();
        }
        ServerLevel worldLevel = authoritativeLevel(level);
        Entity targetEntity = worldLevel.getEntity(this.assignedThreatUuid);
        boolean liveTarget = targetEntity != null && targetEntity.isAlive();
        if (!liveTarget || ShellAlarmCbcCompat.isInGround(targetEntity)) {
            this.lastSolveReason = "threat-not-flying";
            clearInterceptionPlan();
            clearEngagement();
            return Solution.hold(cannon, currentAngles);
        }
        long trackAgeTicks = 0L;
        Vec3 trackedPosition = targetEntity.position();
        Vec3 trackedVelocity = targetEntity.getDeltaMovement();
        double remainingTicks = InterceptionCoordinator.remainingInterceptionTicks(
                level.getServer(), resolvedNetworkId, this.assignedThreatUuid, gameTime);
        InterceptionWindow threatReference = new InterceptionWindow(
                Math.min(MAX_INTERCEPTION_TICKS, remainingTicks - 1.0D));
        if (threatReference.maximumTicks() < MIN_INTERCEPTION_TICKS) {
            rejectAssignment(level);
            this.lastSolveReason = "protected-boundary-too-close";
            return Solution.hold(cannon, currentAngles);
        }
        ShellAlarmCbcCompat.Ballistics threatBallistics = new ShellAlarmCbcCompat.Ballistics(
                threatSnapshot.gravity(),
                threatSnapshot.drag(),
                threatSnapshot.quadraticDrag());
        boolean onSable = RadarWorldPoseResolver.isOnSableStructure(level, this.worldPosition);
        SableInterceptSearch.Pose sablePose = onSable ? sampleSablePose(level) : null;
        WeaponPitchLimits pitchLimits = onSable ? CbcWeaponAdapter.pitchLimits(level, cannon).orElse(null) : null;
        BiPredicate<Vec3, Intercept> candidateAllowed = (muzzle, candidate) -> {
            if (!candidate.reachable) return false;
            Vec3 direction = TargetingMath.directionFromAngles(
                    TargetingMath.yawTo(candidate.position.subtract(muzzle)), candidate.pitchDegrees);
            double localPitch = sablePose == null ? candidate.pitchDegrees
                    : Math.toDegrees(Math.asin(Math.clamp(direction.dot(sablePose.up().normalize()), -1.0D, 1.0D)));
            return (pitchLimits == null || pitchLimits.contains(localPitch))
                    && hasClearLine(worldLevel, worldMuzzle, candidate.position);
        };
        if (onSable && this.interceptionPlan != null) {
            double planTicks = this.interceptionPlan.remainingTicks(gameTime);
            if (planTicks >= 0.0D && planTicks <= threatReference.maximumTicks()
                    && projectilePositionAt(trackedPosition, trackedVelocity, threatBallistics, planTicks)
                            .distanceToSqr(this.interceptionPlan.aimPoint()) > 0.25D) {
                clearInterceptionPlan();
                this.trackingThreatUuid = null;
            }
        }
        boolean engaging = engagementActiveFor(this.assignedThreatUuid);
        if (engaging) {
            clearInterceptionPlan();
        }
        PlannedIntercept planned = null;
        if (!engaging && planMatches(this.assignedThreatUuid)) {
            planned = solveInterceptionPlan(
                    level,
                    worldLevel,
                    cannon,
                    currentAngles,
                    worldMuzzle,
                    launchMuzzle,
                    worldCurrentYaw,
                    worldCurrentPitch,
                    interceptorBallistics,
                    gameTime,
                    onSable,
                    threatReference.maximumTicks(),
                    pitchLimits);
            if (planned != null && (!onSable || !sablePose.differsFrom(this.lastSableSearchPose))) {
                if (onSable) this.lastSolveReason = "planned-sable-stationary";
                return planned.solution();
            }
            if (planned == null) {
                clearInterceptionPlan();
                this.trackingThreatUuid = null;
            }
        }

        // На земле прежний режим. На Sable новый поиск нужен при изменении позы
        // носителя или непригодности плана, а не из-за самого течения времени.
        // После открытия огня обе платформы переходят к точному сопровождению живой угрозы.
        boolean preAim = !engaging
                && (onSable || !this.assignedThreatUuid.equals(this.trackingThreatUuid));
        boolean earlySablePlan = onSable && !engaging;
        if (earlySablePlan) this.lastSableSearchPose = sablePose;
        Vec3 interceptMuzzle = launchMuzzle;
        Intercept intercept = findIntercept(
                interceptMuzzle, threatReference, trackedPosition, trackedVelocity,
                threatBallistics, interceptorBallistics, worldCurrentYaw, worldCurrentPitch,
                maxStepDegreesPerTick(), aimAccelerationDegreesPerTickSquared(),
                preAim, earlySablePlan, candidateAllowed);
        if (onSable) {
            for (int iteration = 0;
                    iteration < SABLE_MUZZLE_PREDICTION_ITERATIONS && intercept.reachable;
                    iteration++) {
                interceptMuzzle = launchMuzzle.add(this.worldMuzzleVelocity.scale(intercept.aimTicks));
                intercept = findIntercept(
                        interceptMuzzle, threatReference, trackedPosition, trackedVelocity,
                        threatBallistics, interceptorBallistics, worldCurrentYaw, worldCurrentPitch,
                        maxStepDegreesPerTick(), aimAccelerationDegreesPerTickSquared(),
                        preAim, earlySablePlan, candidateAllowed);
            }
        }
        if (!intercept.reachable
                || !(earlySablePlan ? correctionReserveTicks(intercept) >= 0.0D
                        : intercept.timingError <= MAX_TIMING_ERROR_TICKS)) {
            if (planned != null) {
                this.lastSolveReason = "planned-sable-no-alternative";
                return planned.solution();
            }
            boolean retrying = retainUnsolvedAssignmentForRetry(level);
            this.lastSolveReason = retrying
                    ? "no-intercept-solution-retrying"
                    : "no-intercept-solution";
            return Solution.unreachable(cannon, currentAngles);
        }
        boolean clearShot = onSable ? candidateAllowed.test(interceptMuzzle, intercept)
                : hasClearLine(worldLevel, worldMuzzle, intercept.position);
        if (!clearShot) {
            if (planned != null) {
                this.lastSolveReason = "planned-sable-alternative-obstructed";
                return planned.solution();
            }
            if (onSable) {
                boolean retrying = retainUnsolvedAssignmentForRetry(level);
                this.lastSolveReason = retrying ? "obstructed-retrying" : "obstructed";
                return Solution.unreachable(cannon, currentAngles);
            }
            rejectAssignment(level);
            this.lastSolveReason = "obstructed";
            return Solution.unreachable(cannon, currentAngles);
        }
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][Target] controller={} network={} target={} trackAge={} trackingSource={} shellPos={} shellVelocity={} shellGravity={} shellDrag={} shellQuadraticDrag={} cannon={} interceptorSpeed={} interceptorGravity={} interceptorDrag={} muzzle={} logicalPitch={} physicalPitch={} pitchMultiplier={} interceptPos={} clearShot={} targetTicks={} aimTicks={} shotTicks={} timingError={}",
                    this.worldPosition,
                    resolvedNetworkId,
                    this.assignedThreatUuid,
                    trackAgeTicks,
                    liveTarget ? "entity" : "snapshot",
                    shortVec(trackedPosition),
                    shortVec(trackedVelocity),
                    round(threatBallistics.gravity()),
                    round(threatBallistics.drag()),
                    threatBallistics.quadraticDrag(),
                    cannon.kind(),
                    round(interceptorBallistics.speedBlocksPerTick()),
                    round(interceptorBallistics.gravityBlocksPerTickSquared()),
                    round(interceptorBallistics.drag()),
                    shortVec(interceptMuzzle),
                    round(cannon.currentPitchDegrees()),
                    round(cannon.physicalPitchDegrees()),
                    round(cannon.worldToLogicalPitchMultiplier()),
                    shortVec(intercept.position),
                    clearShot,
                    round(intercept.targetTicks),
                    round(intercept.aimTicks),
                    round(intercept.flightTicks),
                    round(intercept.timingError));
        }
        Vec3 delta = intercept.position.subtract(interceptMuzzle);
        Vec3 localAimDirection = RadarWorldPoseResolver.localDirection(
                level,
                this.worldPosition,
                TargetingMath.directionFromAngles(TargetingMath.yawTo(delta), intercept.pitchDegrees));
        float desiredYaw = TargetingMath.yawTo(localAimDirection);
        float desiredPitch = pitchTo(localAimDirection);
        Solution candidateSolution = new Solution(
                true,
                cannon,
                cannon.mountPos(),
                interceptMuzzle,
                intercept.position,
                interceptorBallistics.available(),
                clearShot,
                desiredYaw,
                desiredPitch,
                currentAngles.yawDegrees(),
                currentAngles.pitchDegrees(),
                Mth.wrapDegrees(desiredYaw - currentAngles.yawDegrees()),
                Mth.wrapDegrees(desiredPitch - currentAngles.pitchDegrees()),
                intercept.flightTicks,
                intercept.timingError);
        if (engaging) {
            this.lastSolveReason = onSable ? "engaging-sable" : "engaging-ground";
            this.trackingThreatUuid = this.assignedThreatUuid;
            resetUnsolvedAssignment();
            return candidateSolution;
        }
        if (planned != null && !SableInterceptSearch.earlierReplacement(
                intercept.targetTicks, this.interceptionPlan.remainingTicks(gameTime))) {
            this.lastSolveReason = "planned-sable-no-better-alternative";
            return planned.solution();
        }
        this.interceptionPlan = new InterceptionPlan(
                this.assignedThreatUuid,
                intercept.position,
                gameTime + intercept.targetTicks);
        this.lastSolveReason = onSable
                ? (planned == null ? "plan-acquired-sable" : "plan-replaced-sable")
                : "plan-acquired-ground";
        this.trackingThreatUuid = this.assignedThreatUuid;
        resetUnsolvedAssignment();
        return candidateSolution;
    }

    @Nullable
    private PlannedIntercept solveInterceptionPlan(
            ServerLevel level,
            ServerLevel worldLevel,
            WeaponMount cannon,
            AimAngles currentAngles,
            Vec3 worldMuzzle,
            Vec3 launchMuzzle,
            float worldCurrentYaw,
            float worldCurrentPitch,
            WeaponBallistics interceptorBallistics,
            long gameTime,
            boolean onSable,
            double maximumTicks,
            @Nullable WeaponPitchLimits pitchLimits
    ) {
        InterceptionPlan plan = this.interceptionPlan;
        if (plan == null) {
            return null;
        }
        double remainingTicks = plan.remainingTicks(gameTime);
        if (remainingTicks < MIN_INTERCEPTION_TICKS || remainingTicks > maximumTicks) {
            this.lastSolveReason = "plan-expired";
            return null;
        }

        Vec3 plannedLaunchMuzzle = launchMuzzle;
        Vec3 delta = plan.aimPoint().subtract(plannedLaunchMuzzle);
        BallisticAim aim = BallisticAim.UNREACHABLE;
        float worldDesiredYaw = 0.0F;
        double aimTicks = 0.0D;
        int predictionIterations = onSable ? SABLE_MUZZLE_PREDICTION_ITERATIONS + 1 : 1;
        for (int iteration = 0; iteration < predictionIterations; iteration++) {
            delta = plan.aimPoint().subtract(plannedLaunchMuzzle);
            aim = ballisticAim(delta, interceptorBallistics);
            if (!aim.reachable) {
                this.lastSolveReason = "plan-ballistically-unreachable";
                return null;
            }
            worldDesiredYaw = TargetingMath.yawTo(delta);
            aimTicks = estimateAimTicks(
                    Mth.wrapDegrees(worldDesiredYaw - worldCurrentYaw),
                    Mth.wrapDegrees(aim.pitchDegrees - worldCurrentPitch),
                    maxStepDegreesPerTick(),
                    aimAccelerationDegreesPerTickSquared());
            if (onSable) {
                plannedLaunchMuzzle = launchMuzzle.add(this.worldMuzzleVelocity.scale(aimTicks));
            }
        }
        double signedTimingError = InterceptionPlan.signedTimingError(
                remainingTicks, aimTicks, aim.flightTicks);
        if (InterceptionPlan.deadlineMissed(signedTimingError, MAX_TIMING_ERROR_TICKS)) {
            this.lastSolveReason = "plan-deadline-missed";
            return null;
        }
        if (!hasClearLine(worldLevel, worldMuzzle, plan.aimPoint())) {
            this.lastSolveReason = "plan-obstructed";
            return null;
        }

        Vec3 localAimDirection = RadarWorldPoseResolver.localDirection(
                level,
                this.worldPosition,
                TargetingMath.directionFromAngles(worldDesiredYaw, aim.pitchDegrees));
        float desiredYaw = TargetingMath.yawTo(localAimDirection);
        float desiredPitch = pitchTo(localAimDirection);
        if (onSable && pitchLimits != null && !pitchLimits.contains(desiredPitch)) {
            this.lastSolveReason = "plan-pitch-limit";
            return null;
        }
        this.lastSolveReason = onSable ? "planned-sable" : "planned-ground";
        this.trackingThreatUuid = this.assignedThreatUuid;
        resetUnsolvedAssignment();
        Solution solution = new Solution(
                true,
                cannon,
                cannon.mountPos(),
                plannedLaunchMuzzle,
                plan.aimPoint(),
                interceptorBallistics.available(),
                true,
                desiredYaw,
                desiredPitch,
                currentAngles.yawDegrees(),
                currentAngles.pitchDegrees(),
                Mth.wrapDegrees(desiredYaw - currentAngles.yawDegrees()),
                Mth.wrapDegrees(desiredPitch - currentAngles.pitchDegrees()),
                aim.flightTicks,
                Math.abs(signedTimingError));
        return new PlannedIntercept(solution);
    }

    private Optional<WeaponMount> inspectWeaponMount(ServerLevel level, BlockPos mountPos) {
        long gameTime = level.getGameTime();
        if (mountPos.equals(this.lastMissingWeaponMountPos) && gameTime - this.lastMissingWeaponMountGameTime < 10L) {
            return Optional.empty();
        }
        WeaponBallistics cachedBallistics = cachedAutocannonBallistics(mountPos, gameTime);
        WeaponKind cachedKind = cachedWeaponKind(mountPos, gameTime);
        Optional<WeaponMount> mount = CbcWeaponAdapter.inspectForPreciseTargeting(
                level,
                mountPos,
                cachedBallistics,
                cachedKind);
        if (mount.isEmpty()) {
            this.lastMissingWeaponMountPos = mountPos.immutable();
            this.lastMissingWeaponMountGameTime = gameTime;
            invalidateEstimatedAimAngles();
            if (mountPos.equals(this.cachedAutocannonBallisticsMountPos)) {
                invalidateAutocannonBallisticsCache();
            }
            if (mountPos.equals(this.cachedWeaponKindMountPos)) {
                invalidateWeaponKindCache();
            }
        } else {
            this.lastMissingWeaponMountPos = null;
            this.lastMissingWeaponMountGameTime = Long.MIN_VALUE;
            WeaponMount cannonState = mount.get();
            if (cachedBallistics == null) {
                rememberAutocannonBallistics(cannonState, gameTime);
            }
            if (cachedKind == null) {
                rememberWeaponKind(cannonState, gameTime);
            }
        }
        return mount;
    }

    private void rememberIdleInspectAttempt(long threatRevision, long gameTime) {
        if (this.assignedThreatUuid != null) {
            return;
        }
        this.lastPublishedThreatRevision = threatRevision;
        this.lastControllerSnapshotGameTime = gameTime;
    }

    private SableInterceptSearch.Pose sampleSablePose(ServerLevel level) {
        return new SableInterceptSearch.Pose(
                RadarWorldPoseResolver.worldPosition(level, this.worldPosition),
                RadarWorldPoseResolver.worldDirection(level, this.worldPosition, new Vec3(0, 0, 1)),
                RadarWorldPoseResolver.worldDirection(level, this.worldPosition, new Vec3(0, 1, 0)));
    }

    private static Vec3 projectilePositionAt(Vec3 position, Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics, double ticks) {
        return shellTrajectory(position, velocity, ballistics, ticks).apply(ticks);
    }

    private static DoubleFunction<Vec3> shellTrajectory(Vec3 position, Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics, double maximumTicks) {
        if (!ballistics.quadraticDrag() && LinearDragTrajectory.supported(ballistics.drag())) {
            return ticks -> LinearDragTrajectory.positionAfterTicks(
                    position, velocity, ballistics.gravity(), ballistics.drag(), ticks);
        }
        if (!ballistics.quadraticDrag() && ballistics.drag() <= MIN_LINEAR_DRAG) {
            return ticks -> position.add(velocity.scale(ticks))
                    .add(0.0D, -0.5D * ballistics.gravity() * ticks * ticks, 0.0D);
        }
        // Редкий нелинейный случай: один проход CBC, затем переиспользование позиций.
        List<Vec3> positions = new ArrayList<>();
        positions.add(position);
        Vec3 currentPosition = position;
        Vec3 currentVelocity = velocity;
        for (int tick = 0; tick < Math.ceil(maximumTicks); tick++) {
            Vec3 nextVelocity = InterceptionBallistics.applyBallistics(currentVelocity,
                    ballistics.gravity(), ballistics.drag(), ballistics.quadraticDrag());
            currentPosition = currentPosition.add(currentVelocity.add(nextVelocity).scale(0.5D));
            currentVelocity = nextVelocity;
            positions.add(currentPosition);
        }
        return ticks -> {
            int lower = Math.clamp((int) Math.floor(ticks), 0, positions.size() - 1);
            int upper = Math.min(lower + 1, positions.size() - 1);
            return positions.get(lower).lerp(positions.get(upper), ticks - lower);
        };
    }

    private static Intercept findIntercept(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            boolean preAim,
            boolean earlySablePlan,
            BiPredicate<Vec3, Intercept> candidateAllowed
    ) {
        if (earlySablePlan) {
            DoubleFunction<Vec3> trajectory = shellTrajectory(shellPosition, shellVelocity,
                    shellBallistics, threatReference.maximumTicks());
            Intercept early = SableInterceptSearch.find(MIN_INTERCEPTION_TICKS, threatReference.maximumTicks(),
                    ticks -> evaluateIntercept(muzzle, trajectory.apply(ticks), ticks, interceptorBallistics,
                            currentYaw, currentPitch, maxStep, acceleration),
                    candidate -> candidate.reachable && correctionReserveTicks(candidate) >= 0.0D
                            && candidateAllowed.test(muzzle, candidate),
                    InterceptionControllerBlockEntity::correctionReserveTicks);
            return early == null ? Intercept.UNREACHABLE : early;
        }
        // Линейное сопротивление допускает быстрый аналитический прогноз позиции снаряда.
        Intercept fastIntercept = findLinearDragIntercept(
                muzzle,
                threatReference,
                shellPosition,
                shellVelocity,
                shellBallistics,
                interceptorBallistics,
                currentYaw,
                currentPitch,
                maxStep,
                acceleration,
                preAim);
        if (fastIntercept.reachable) {
            return fastIntercept;
        }

        // Для квадратичного/вырожденного drag сохраняется точная пошаговая симуляция CBC.
        Vec3 position = shellPosition;
        Vec3 velocity = shellVelocity;
        Intercept best = Intercept.UNREACHABLE;
        List<Intercept> window = preAim ? new ArrayList<>() : List.of();
        for (int tick = 1; tick <= threatReference.maximumTicks(); tick++) {
            position = position.add(velocity);
            velocity = InterceptionBallistics.applyBallistics(velocity, shellBallistics.gravity(),
                    shellBallistics.drag(), shellBallistics.quadraticDrag());
            if (tick < MIN_INTERCEPTION_TICKS) {
                continue;
            }
            if (tick != MIN_INTERCEPTION_TICKS
                    && (tick - MIN_INTERCEPTION_TICKS) % INTERCEPT_COARSE_STEP_TICKS != 0) {
                continue;
            }
            if (threatReference.expired(tick)) {
                break;
            }
            Intercept evaluated = evaluateIntercept(
                    muzzle, position, tick, interceptorBallistics,
                    currentYaw, currentPitch, maxStep, acceleration);
            if (betterIntercept(evaluated, best)) {
                best = evaluated;
            }
            if (preAim && evaluated.reachable
                    && evaluated.timingError <= MAX_TIMING_ERROR_TICKS) {
                window.add(evaluated);
            }
            if (!preAim && best.reachable && best.timingError <= 0.35) {
                break;
            }
        }
        if (preAim && !window.isEmpty()) {
            best = selectPreAimIntercept(window);
        }
        if (!best.reachable) {
            return best;
        }

        int refineStart = Math.max(MIN_INTERCEPTION_TICKS,
                (int) Math.floor(best.targetTicks) - INTERCEPT_COARSE_STEP_TICKS);
        int refineEnd = Math.min((int) Math.floor(threatReference.maximumTicks()),
                (int) Math.ceil(best.targetTicks) + INTERCEPT_COARSE_STEP_TICKS);
        position = shellPosition;
        velocity = shellVelocity;
        for (int tick = 1; tick <= refineEnd; tick++) {
            position = position.add(velocity);
            velocity = InterceptionBallistics.applyBallistics(velocity, shellBallistics.gravity(),
                    shellBallistics.drag(), shellBallistics.quadraticDrag());
            if (tick < refineStart) {
                continue;
            }
            if (threatReference.expired(tick)) {
                break;
            }
            Intercept evaluated = evaluateIntercept(
                    muzzle, position, tick, interceptorBallistics,
                    currentYaw, currentPitch, maxStep, acceleration);
            if (betterIntercept(evaluated, best)) {
                best = evaluated;
            }
        }
        return best;
    }

    private static Intercept findLinearDragIntercept(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            boolean preAim
    ) {
        if (shellBallistics.quadraticDrag()
                || shellBallistics.drag() <= MIN_LINEAR_DRAG
                || shellBallistics.drag() >= 1.0) {
            return Intercept.UNREACHABLE;
        }

        // Грубые дешёвые пробы находят смены знака ошибки времени и область её минимума.
        Intercept best = Intercept.UNREACHABLE;
        List<Intercept> window = preAim ? new ArrayList<>() : List.of();
        List<CheapTimingSample> samples = new ArrayList<>();
        List<TimingBracket> signChangeBrackets = new ArrayList<>();
        CheapTimingSample previous = null;
        double pitchHint = Double.NaN;

        int sampleCount = (int) Math.ceil(
                (threatReference.maximumTicks() - MIN_INTERCEPTION_TICKS) / INTERCEPT_FAST_STEP_TICKS);
        for (int sampleIndex = 0; sampleIndex <= sampleCount; sampleIndex++) {
            double tick = Math.min(threatReference.maximumTicks(),
                    MIN_INTERCEPTION_TICKS + sampleIndex * INTERCEPT_FAST_STEP_TICKS);
            CheapTimingSample sample = approximateLinearDragInterceptAt(
                    muzzle,
                    threatReference,
                    shellPosition,
                    shellVelocity,
                    shellBallistics,
                    interceptorBallistics,
                    currentYaw,
                    currentPitch,
                    maxStep,
                    acceleration,
                    tick);
            if (sample.passedReference) {
                break;
            }
            samples.add(sample);
            if (previous != null
                    && Double.isFinite(previous.signedError)
                    && Double.isFinite(sample.signedError)
                    && Math.signum(previous.signedError) != Math.signum(sample.signedError)) {
                signChangeBrackets.add(new TimingBracket(previous.tick, sample.tick));
            }
            previous = sample;
        }

        for (TimingBracket bracket : signChangeBrackets) {
            Intercept root = refineLinearDragInterceptRoot(
                    muzzle,
                    threatReference,
                    shellPosition,
                    shellVelocity,
                    shellBallistics,
                    interceptorBallistics,
                    currentYaw,
                    currentPitch,
                    maxStep,
                    acceleration,
                    bracket.lowTick,
                    bracket.highTick,
                    pitchHint);
            if (betterIntercept(root, best)) {
                best = root;
            }
            if (root.reachable) {
                pitchHint = root.pitchDegrees;
            }
            if (preAim && root.reachable
                    && root.timingError <= MAX_TIMING_ERROR_TICKS) {
                window.add(root);
            }
            if (!preAim && root.reachable && root.timingError <= 0.35) {
                return root;
            }
        }

        Intercept minimum = refineBestLinearDragMinimum(
                muzzle,
                threatReference,
                shellPosition,
                shellVelocity,
                shellBallistics,
                interceptorBallistics,
                currentYaw,
                currentPitch,
                maxStep,
                acceleration,
                samples,
                pitchHint);
        if (betterIntercept(minimum, best)) {
            best = minimum;
        }
        if (preAim && minimum.reachable
                && minimum.timingError <= MAX_TIMING_ERROR_TICKS) {
            window.add(minimum);
        }

        if (preAim && !window.isEmpty()) {
            return selectPreAimIntercept(window);
        }
        return best;
    }

    private static Intercept selectPreAimIntercept(List<Intercept> window) {
        window.sort(java.util.Comparator.comparingDouble(Intercept::targetTicks));
        return window.get((window.size() - 1) / 3);
    }

    private static double correctionReserveTicks(Intercept intercept) {
        return InterceptionPlan.correctionReserveTicks(
                intercept.targetTicks, intercept.aimTicks, intercept.flightTicks);
    }

    private static double currentError(Intercept intercept) {
        return intercept.reachable ? intercept.timingError : Double.MAX_VALUE;
    }

    private static Intercept refineBestLinearDragMinimum(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            List<CheapTimingSample> samples,
            double pitchHint
    ) {
        if (samples.isEmpty()) {
            return Intercept.UNREACHABLE;
        }
        CheapTimingSample bestSample = null;
        for (CheapTimingSample sample : samples) {
            if (!Double.isFinite(sample.cost)) {
                continue;
            }
            if (bestSample == null || sample.cost < bestSample.cost) {
                bestSample = sample;
            }
        }
        if (bestSample == null) {
            return Intercept.UNREACHABLE;
        }
        int index = samples.indexOf(bestSample);
        double low = index > 0
                ? samples.get(index - 1).tick
                : Math.max(MIN_INTERCEPTION_TICKS, bestSample.tick - INTERCEPT_FAST_STEP_TICKS);
        double high = index + 1 < samples.size()
                ? samples.get(index + 1).tick
                : Math.min(threatReference.maximumTicks(), bestSample.tick + INTERCEPT_FAST_STEP_TICKS);
        return minimizeLinearDragIntercept(
                muzzle,
                threatReference,
                shellPosition,
                shellVelocity,
                shellBallistics,
                interceptorBallistics,
                currentYaw,
                currentPitch,
                maxStep,
                acceleration,
                low,
                high,
                pitchHint);
    }

    private static Intercept minimizeLinearDragIntercept(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            double lowTick,
            double highTick,
            double pitchHint
    ) {
        double low = lowTick;
        double high = highTick;
        for (int i = 0; i < INTERCEPT_FAST_MINIMIZE_ITERATIONS; i++) {
            double left = low + (high - low) / 3.0;
            double right = high - (high - low) / 3.0;
            Intercept leftIntercept = evaluateLinearDragInterceptAt(
                    muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                    interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, left, pitchHint);
            if (leftIntercept.reachable) {
                pitchHint = leftIntercept.pitchDegrees;
            }
            Intercept rightIntercept = evaluateLinearDragInterceptAt(
                    muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                    interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, right, pitchHint);
            if (rightIntercept.reachable) {
                pitchHint = rightIntercept.pitchDegrees;
            }
            if (currentError(leftIntercept) <= currentError(rightIntercept)) {
                high = right;
            } else {
                low = left;
            }
        }
        double tick = (low + high) * 0.5;
        return evaluateLinearDragInterceptAt(
                muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, tick, pitchHint);
    }

    private static Intercept refineLinearDragInterceptRoot(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            double lowTick,
            double highTick,
            double pitchHint
    ) {
        // Первые итерации используют секущую внутри bracket, остальные гарантированно сужают его пополам.
        double low = lowTick;
        double high = highTick;
        Intercept lowIntercept = evaluateLinearDragInterceptAt(
                muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, low, pitchHint);
        if (lowIntercept.reachable) {
            pitchHint = lowIntercept.pitchDegrees;
        }
        double lowError = interceptTimingErrorSigned(lowIntercept);
        Intercept best = lowIntercept;
        Intercept highIntercept = evaluateLinearDragInterceptAt(
                muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, high, pitchHint);
        if (betterIntercept(highIntercept, best)) {
            best = highIntercept;
        }
        if (highIntercept.reachable) {
            pitchHint = highIntercept.pitchDegrees;
        }
        double highError = interceptTimingErrorSigned(highIntercept);

        for (int i = 0; i < INTERCEPT_FAST_ROOT_ITERATIONS; i++) {
            double candidateTick = (low + high) * 0.5;
            if (i < INTERCEPT_FAST_SECANT_ITERATIONS
                    && Double.isFinite(lowError)
                    && Double.isFinite(highError)
                    && Math.signum(lowError) != Math.signum(highError)
                    && Math.abs(highError - lowError) > 1.0E-9) {
                double secantTick = low - lowError * (high - low) / (highError - lowError);
                if (Double.isFinite(secantTick) && secantTick > low && secantTick < high) {
                    candidateTick = secantTick;
                }
            }
            Intercept candidate = evaluateLinearDragInterceptAt(
                    muzzle, threatReference, shellPosition, shellVelocity, shellBallistics,
                    interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration,
                    candidateTick, pitchHint);
            if (betterIntercept(candidate, best)) {
                best = candidate;
            }
            if (candidate.reachable) {
                pitchHint = candidate.pitchDegrees;
            }
            double candidateError = interceptTimingErrorSigned(candidate);
            if (!Double.isFinite(candidateError)) {
                high = candidateTick;
                highError = candidateError;
                continue;
            }
            if (Math.abs(candidateError) <= 0.05) {
                return candidate;
            }
            if (Double.isFinite(lowError)
                    && Math.signum(candidateError) == Math.signum(lowError)) {
                low = candidateTick;
                lowError = candidateError;
            } else {
                high = candidateTick;
                highError = candidateError;
            }
        }
        return best;
    }

    private static CheapTimingSample approximateLinearDragInterceptAt(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            double targetTicks
    ) {
        LinearDragTrajectory.TrajectoryState shellState = LinearDragTrajectory.stateAfterTicks(
                shellPosition,
                shellVelocity,
                shellBallistics.gravity(),
                shellBallistics.drag(),
                targetTicks);
        Vec3 position = shellState.position();
        Vec3 velocity = shellState.velocity();
        if (threatReference.expired(targetTicks)) {
            return new CheapTimingSample(targetTicks, Double.NaN, Double.MAX_VALUE, true);
        }

        double speed = interceptorBallistics.speedBlocksPerTick();
        if (speed <= 0.001) {
            return new CheapTimingSample(targetTicks, Double.NaN, Double.MAX_VALUE, false);
        }
        Vec3 delta = position.subtract(muzzle);
        double distance = delta.length();
        double approximateFlightTicks = distance / speed;
        if (interceptorBallistics.hasLifetimeLimit()
                && approximateFlightTicks > interceptorBallistics.lifetimeTicks() + 1.0) {
            return new CheapTimingSample(targetTicks, Double.NaN, Double.MAX_VALUE, false);
        }

        double horizontal = TargetingMath.horizontalDistance(delta);
        float desiredYaw = TargetingMath.yawTo(delta);
        double desiredPitch = Math.toDegrees(Math.atan2(delta.y, horizontal));
        double aimTicks = estimateAimTicks(
                Mth.wrapDegrees(desiredYaw - currentYaw),
                Mth.wrapDegrees(desiredPitch - currentPitch),
                maxStep,
                acceleration);
        double signedError = approximateFlightTicks + aimTicks - targetTicks;
        return new CheapTimingSample(targetTicks, signedError, Math.abs(signedError), false);
    }

    private static Intercept evaluateLinearDragInterceptAt(
            Vec3 muzzle,
            InterceptionWindow threatReference,
            Vec3 shellPosition,
            Vec3 shellVelocity,
            ShellAlarmCbcCompat.Ballistics shellBallistics,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            double targetTicks,
            double pitchHint
    ) {
        LinearDragTrajectory.TrajectoryState shellState = LinearDragTrajectory.stateAfterTicks(
                shellPosition,
                shellVelocity,
                shellBallistics.gravity(),
                shellBallistics.drag(),
                targetTicks);
        Vec3 position = shellState.position();
        Vec3 velocity = shellState.velocity();
        if (threatReference.expired(targetTicks)) {
            return Intercept.PASSED_REFERENCE;
        }
        return evaluateIntercept(
                muzzle, position, targetTicks, interceptorBallistics,
                currentYaw, currentPitch, maxStep, acceleration, pitchHint);
    }

    private static double interceptTimingErrorSigned(Intercept intercept) {
        return intercept.reachable
                ? intercept.flightTicks + intercept.aimTicks - intercept.targetTicks
                : Double.NaN;
    }

    private static Intercept evaluateIntercept(
            Vec3 muzzle,
            Vec3 shellPosition,
            double targetTicks,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration
    ) {
        return evaluateIntercept(
                muzzle,
                shellPosition,
                targetTicks,
                interceptorBallistics,
                currentYaw,
                currentPitch,
                maxStep,
                acceleration,
                Double.NaN);
    }

    private static Intercept evaluateIntercept(
            Vec3 muzzle,
            Vec3 shellPosition,
            double targetTicks,
            WeaponBallistics interceptorBallistics,
            float currentYaw,
            float currentPitch,
            double maxStep,
            double acceleration,
            double pitchHint
    ) {
        Vec3 delta = shellPosition.subtract(muzzle);
        BallisticAim aim = ballisticAim(delta, interceptorBallistics, pitchHint);
        if (!aim.reachable) {
            return Intercept.UNREACHABLE;
        }
        float desiredYaw = TargetingMath.yawTo(delta);
        double aimTicks = estimateAimTicks(
                Mth.wrapDegrees(desiredYaw - currentYaw),
                Mth.wrapDegrees(aim.pitchDegrees - currentPitch),
                maxStep,
                acceleration);
        double timingError = Math.abs(aim.flightTicks + aimTicks - targetTicks);
        return new Intercept(
                true, shellPosition, aim.pitchDegrees, targetTicks, aimTicks,
                aim.flightTicks, timingError);
    }

    private static boolean betterIntercept(Intercept candidate, Intercept current) {
        return candidate.reachable
                && (!current.reachable || candidate.timingError < current.timingError);
    }

    private static BallisticAim ballisticAim(Vec3 delta, WeaponBallistics ballistics) {
        return ballisticAim(delta, ballistics, Double.NaN);
    }

    private static BallisticAim ballisticAim(Vec3 delta, WeaponBallistics ballistics, double pitchHint) {
        InterceptionBallistics.Aim aim = InterceptionBallistics.solveAutocannonLowArc(
                delta,
                ballistics,
                pitchHint,
                INTERCEPT_PITCH_HINT_RANGE_DEGREES);
        return aim.reachable()
                ? new BallisticAim(true, aim.pitchDegrees(), aim.flightTicks())
                : BallisticAim.UNREACHABLE;
    }

    private static double estimateAimTicks(
            double yawError,
            double pitchError,
            double maxStep,
            double acceleration
    ) {
        if (maxStep <= 0.0001 || acceleration <= 0.0001) {
            return Double.MAX_VALUE;
        }
        return AIM_REACTION_TICKS + Math.max(
                estimateAxisAimTicks(Math.abs(yawError), maxStep, acceleration),
                estimateAxisAimTicks(Math.abs(pitchError), maxStep, acceleration));
    }

    private static double estimateAxisAimTicks(double error, double maxStep, double acceleration) {
        double distance = Math.max(0.0, error - AIM_TOLERANCE_DEGREES);
        if (distance <= 0.0) {
            return 0.0;
        }
        double rampTicks = maxStep / acceleration;
        double rampDistance = 0.5 * acceleration * rampTicks * rampTicks;
        if (distance <= 2.0 * rampDistance) {
            return 2.0 * Math.sqrt(distance / acceleration);
        }
        return 2.0 * rampTicks + (distance - 2.0 * rampDistance) / maxStep;
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

    private Vec3 predictedLaunchMuzzle(Vec3 worldMuzzle, long gameTime) {
        if (this.lastWorldMuzzle == null
                || this.lastWorldMuzzleGameTime == Long.MIN_VALUE
                || gameTime <= this.lastWorldMuzzleGameTime
                || gameTime - this.lastWorldMuzzleGameTime > 5L) {
            this.worldMuzzleVelocity = Vec3.ZERO;
        } else {
            double elapsedTicks = gameTime - this.lastWorldMuzzleGameTime;
            this.worldMuzzleVelocity = worldMuzzle.subtract(this.lastWorldMuzzle).scale(1.0D / elapsedTicks);
        }
        this.lastWorldMuzzle = worldMuzzle;
        this.lastWorldMuzzleGameTime = gameTime;
        return worldMuzzle.add(this.worldMuzzleVelocity.scale(CBC_FIRE_SIGNAL_DELAY_TICKS));
    }

    private static ServerLevel authoritativeLevel(ServerLevel level) {
        ServerLevel worldLevel = level.getServer().getLevel(level.dimension());
        return worldLevel == null ? level : worldLevel;
    }

    private static float pitchTo(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(
                direction.y,
                Math.max(0.001D, TargetingMath.horizontalDistance(direction))));
    }

    private AimStep applyAim(ServerLevel level, Solution solution) {
        // Ограниченный по ускорению привод получает feed-forward к текущей угловой ошибке.
        double maxStep = maxStepDegreesPerTick();
        double acceleration = aimAccelerationDegreesPerTickSquared();
        AimFeedForward feedForward = updateAimFeedForward(solution, level.getGameTime(), maxStep);
        float commandYaw = TargetingMath.normalize360((float) (
                solution.desiredYaw + feedForward.yawDegreesPerTick * CBC_FIRE_SIGNAL_DELAY_TICKS));
        float commandPitch = (float) (
                solution.desiredPitch + feedForward.pitchDegreesPerTick * CBC_FIRE_SIGNAL_DELAY_TICKS);
        float commandYawError = Mth.wrapDegrees(commandYaw - solution.currentYaw);
        float commandPitchError = Mth.wrapDegrees(commandPitch - solution.currentPitch);
        double targetYawVelocity = clamp(
                commandYawError * PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK
                        + feedForward.yawDegreesPerTick,
                -maxStep, maxStep);
        double targetPitchVelocity = clamp(
                commandPitchError * PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK
                        + feedForward.pitchDegreesPerTick,
                -maxStep, maxStep);
        this.yawVelocityDegreesPerTick = TargetingMath.approach(
                this.yawVelocityDegreesPerTick, targetYawVelocity, acceleration);
        this.pitchVelocityDegreesPerTick = TargetingMath.approach(
                this.pitchVelocityDegreesPerTick, targetPitchVelocity, acceleration);
        float yawStep = (float) clamp(
                this.yawVelocityDegreesPerTick, -Math.abs(commandYawError), Math.abs(commandYawError));
        float pitchStep = (float) clamp(
                this.pitchVelocityDegreesPerTick, -Math.abs(commandPitchError), Math.abs(commandPitchError));
        float nextYaw = TargetingMath.normalize360(solution.currentYaw + yawStep);
        float nextPitch = solution.currentPitch + pitchStep;
        boolean applied = CbcWeaponAdapter.applyAdjustableMountAngles(
                level, solution.cannonState, nextYaw, nextPitch);
        if (applied) {
            recordEstimatedAimAngles(solution.mountPos, nextYaw, nextPitch);
        } else {
            invalidateEstimatedAimAngles();
        }
        return new AimStep(
                applied,
                Mth.wrapDegrees(commandYaw - nextYaw),
                Mth.wrapDegrees(commandPitch - nextPitch));
    }

    private AimFeedForward updateAimFeedForward(Solution solution, long gameTime, double maxStep) {
        if (this.assignedThreatUuid == null
                || !this.assignedThreatUuid.equals(this.aimRateThreatUuid)
                || this.lastAimRateGameTime == Long.MIN_VALUE
                || gameTime <= this.lastAimRateGameTime
                || gameTime - this.lastAimRateGameTime > 5L) {
            this.aimRateThreatUuid = this.assignedThreatUuid;
            this.lastDesiredYawDegrees = solution.desiredYaw;
            this.lastDesiredPitchDegrees = solution.desiredPitch;
            this.lastAimRateGameTime = gameTime;
            this.yawFeedForwardDegreesPerTick = 0.0D;
            this.pitchFeedForwardDegreesPerTick = 0.0D;
            return AimFeedForward.ZERO;
        }
        double elapsedTicks = gameTime - this.lastAimRateGameTime;
        double rawYawRate = Mth.wrapDegrees(solution.desiredYaw - this.lastDesiredYawDegrees) / elapsedTicks;
        double rawPitchRate = Mth.wrapDegrees(solution.desiredPitch - this.lastDesiredPitchDegrees) / elapsedTicks;
        this.yawFeedForwardDegreesPerTick = lerp(this.yawFeedForwardDegreesPerTick, rawYawRate, 0.65D);
        this.pitchFeedForwardDegreesPerTick = lerp(this.pitchFeedForwardDegreesPerTick, rawPitchRate, 0.65D);
        this.lastDesiredYawDegrees = solution.desiredYaw;
        this.lastDesiredPitchDegrees = solution.desiredPitch;
        this.lastAimRateGameTime = gameTime;
        return new AimFeedForward(
                clamp(this.yawFeedForwardDegreesPerTick, -maxStep, maxStep),
                clamp(this.pitchFeedForwardDegreesPerTick, -maxStep, maxStep));
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

    @javax.annotation.Nullable
    private WeaponBallistics cachedAutocannonBallistics(BlockPos mountPos, long gameTime) {
        if (this.cachedAutocannonBallistics == null || !mountPos.equals(this.cachedAutocannonBallisticsMountPos)) {
            return null;
        }
        if (gameTime - this.lastAutocannonBallisticsCacheGameTime >= MOUNT_CACHE_RESYNC_TICKS) {
            return null;
        }
        return this.cachedAutocannonBallistics;
    }

    private void rememberAutocannonBallistics(WeaponMount cannonState, long gameTime) {
        if (cannonState.kind() != WeaponKind.AUTOCANNON || !cannonState.ballistics().available()) {
            return;
        }
        this.cachedAutocannonBallisticsMountPos = cannonState.mountPos().immutable();
        this.cachedAutocannonBallistics = cannonState.ballistics();
        this.lastAutocannonBallisticsCacheGameTime = gameTime;
    }

    private void invalidateAutocannonBallisticsCache() {
        this.cachedAutocannonBallisticsMountPos = null;
        this.cachedAutocannonBallistics = null;
        this.lastAutocannonBallisticsCacheGameTime = Long.MIN_VALUE;
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

    private static double aimAccelerationDegreesPerTickSquared() {
        return PowerRadarCeeConstants.TARGET_CONTROLLER_ACCELERATION_RPM_PER_SECOND
                * PowerRadarCeeConstants.INTERCEPTION_CONTROLLER_SPEED_MULTIPLIER
                * 360.0 / 60.0 / 20.0 / 20.0;
    }

    private double maxStepDegreesPerTick() {
        if (!validVoltage()) {
            return 0.0;
        }
        double voltage = Math.abs(this.powerVoltageVolts);
        PowerRadarElectricalParameters.DriveVoltageRange voltages =
                PowerRadarElectricalParameters.Voltages.interceptionController();
        double fraction = clamp(
                (voltage - voltages.minimum())
                        / Math.max(PowerRadarElectricalParameters.MIN_SAFE_RESISTANCE_OHMS,
                        voltages.nominal() - voltages.minimum()),
                0.0, 1.0);
        double rpm = PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM
                + (PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_RPM
                - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM) * fraction;
        rpm *= PowerRadarCeeConstants.INTERCEPTION_CONTROLLER_SPEED_MULTIPLIER;
        return rpm * 360.0 / 60.0 / 20.0;
    }

    private boolean validVoltage() {
        double voltage = Math.abs(this.powerVoltageVolts);
        PowerRadarElectricalParameters.DriveVoltageRange voltages =
                PowerRadarElectricalParameters.Voltages.interceptionController();
        return voltage >= voltages.minimum() && voltage <= voltages.maximum();
    }

    private void releaseAssignment(ServerLevel level) {
        if (this.interceptionNetworkId != null) {
            InterceptionCoordinator.unregisterController(
                    level, this.interceptionNetworkId, this.worldPosition);
        }
        this.assignedThreatUuid = null;
        this.trackingThreatUuid = null;
        resetUnsolvedAssignment();
        clearInterceptionPlan();
        clearEngagement();
        this.lastPublishedThreatRevision = Long.MIN_VALUE;
        this.lastControllerSnapshotGameTime = Long.MIN_VALUE;
        resetSableTrackingState();
    }

    private void rejectAssignment(ServerLevel level) {
        if (this.interceptionNetworkId != null && this.assignedThreatUuid != null) {
            InterceptionCoordinator.rejectAssignment(
                    level, this.interceptionNetworkId, this.worldPosition, this.assignedThreatUuid);
        }
        this.assignedThreatUuid = null;
        this.trackingThreatUuid = null;
        resetUnsolvedAssignment();
        clearInterceptionPlan();
        clearEngagement();
        this.lastControllerSnapshotGameTime = Long.MIN_VALUE;
        resetAimFeedForward();
    }

    private boolean retainUnsolvedAssignmentForRetry(ServerLevel level) {
        UUID threatUuid = this.assignedThreatUuid;
        if (threatUuid == null) {
            resetUnsolvedAssignment();
            return false;
        }
        if (!threatUuid.equals(this.unsolvedThreatUuid)) {
            this.unsolvedThreatUuid = threatUuid;
            this.consecutiveUnsolvedTicks = 0;
        }
        this.consecutiveUnsolvedTicks++;
        this.trackingThreatUuid = null;
        resetAimFeedForward();
        boolean alternatives = this.interceptionNetworkId != null
                && InterceptionCoordinator.activeThreatCount(
                        level.getServer(), this.interceptionNetworkId, level.getGameTime()) > 1;
        if (!alternatives && this.consecutiveUnsolvedTicks < UNSOLVED_ASSIGNMENT_RETRY_TICKS) {
            return true;
        }
        rejectAssignment(level);
        return false;
    }

    private void resetUnsolvedAssignment() {
        this.unsolvedThreatUuid = null;
        this.consecutiveUnsolvedTicks = 0;
    }

    private boolean planMatches(@Nullable UUID threatUuid) {
        return this.interceptionPlan != null && this.interceptionPlan.matches(threatUuid);
    }

    private void clearInterceptionPlan() {
        this.interceptionPlan = null;
    }

    private void resetSableTrackingState() {
        this.lastSableSearchPose = null;
        this.lastWorldMuzzle = null;
        this.lastWorldMuzzleGameTime = Long.MIN_VALUE;
        this.worldMuzzleVelocity = Vec3.ZERO;
        resetAimFeedForward();
    }

    private void resetAimFeedForward() {
        this.aimRateThreatUuid = null;
        this.lastAimRateGameTime = Long.MIN_VALUE;
        this.yawFeedForwardDegreesPerTick = 0.0D;
        this.pitchFeedForwardDegreesPerTick = 0.0D;
    }

    private boolean engagementActiveFor(@Nullable UUID threatUuid) {
        return threatUuid != null && threatUuid.equals(this.engagedThreatUuid);
    }

    private void clearEngagement() {
        this.engagedThreatUuid = null;
    }

    private Status statusFor(Solution solution, boolean powered, boolean ready) {
        if (!powered) {
            return Math.abs(this.powerVoltageVolts)
                    > PowerRadarElectricalParameters.Voltages.interceptionController().maximum()
                    ? Status.OVERVOLTAGE : Status.UNDERVOLTAGE;
        }
        if (this.interceptionNetworkId == null) {
            return Status.NO_NETWORK;
        }
        if (this.assignedThreatUuid == null) {
            return Status.NO_THREAT;
        }
        if (!solution.ammunitionAvailable) {
            return Status.NO_AMMUNITION;
        }
        if (!solution.valid) {
            return Status.NO_INTERCEPT;
        }
        if (!solution.clearShot) {
            return Status.OBSTRUCTED;
        }
        return ready ? Status.FIRING : Status.AIMING;
    }

    private void setReadyToFire(ServerLevel level, BlockState state, boolean ready) {
        if (this.readyToFire == ready) {
            return;
        }
        this.readyToFire = ready;
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][Launch] controller={} ready={} network={} target={} voltage={} interceptTicks={}",
                    this.worldPosition,
                    ready,
                    this.interceptionNetworkId,
                    this.assignedThreatUuid,
                    round(this.powerVoltageVolts),
                    round(this.interceptTicks));
        }
        Block block = state.getBlock();
        level.updateNeighborsAt(this.worldPosition, block);
        level.updateNeighbourForOutputSignal(this.worldPosition, block);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(this.worldPosition.relative(direction), block);
        }
    }

    private void syncClient(ServerLevel level, BlockState state) {
        long gameTime = level.getGameTime();
        if (gameTime - this.lastClientSyncGameTime >= 10L) {
            this.lastClientSyncGameTime = gameTime;
            level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        UUID oldInterceptionNetworkId = this.interceptionNetworkId;
        super.read(tag, registries, clientPacket);
        this.interceptionNetworkId = tag.hasUUID("InterceptionNetworkId")
                ? tag.getUUID("InterceptionNetworkId")
                : null;
        this.readyToFire = tag.getBoolean("ReadyToFire");
        this.powerVoltageVolts = tag.getDouble("PowerVoltage");
        this.currentAmps = tag.getDouble("CurrentAmps");
        this.powerWatts = tag.getDouble("PowerWatts");
        this.desiredYawDegrees = tag.getFloat("DesiredYaw");
        this.desiredPitchDegrees = tag.getFloat("DesiredPitch");
        this.currentYawDegrees = tag.getFloat("CurrentYaw");
        this.currentPitchDegrees = tag.getFloat("CurrentPitch");
        this.interceptTicks = tag.getDouble("InterceptTicks");
        try {
            this.status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException exception) {
            this.status = Status.NO_NETWORK;
        }
        if (this.level != null && this.level.isClientSide()) {
            InterceptionNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level,
                    this.worldPosition,
                    oldInterceptionNetworkId,
                    this.interceptionNetworkId);
        }
        invalidateEstimatedAimAngles();
        invalidateAutocannonBallisticsCache();
        invalidateWeaponKindCache();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        // Имена NBT и enum Status являются совместимым форматом мира и update tag.
        super.write(tag, registries, clientPacket);
        tag.putBoolean("ReadyToFire", this.readyToFire);
        tag.putDouble("PowerVoltage", this.powerVoltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("PowerWatts", this.powerWatts);
        tag.putFloat("DesiredYaw", this.desiredYawDegrees);
        tag.putFloat("DesiredPitch", this.desiredPitchDegrees);
        tag.putFloat("CurrentYaw", this.currentYawDegrees);
        tag.putFloat("CurrentPitch", this.currentPitchDegrees);
        tag.putDouble("InterceptTicks", this.interceptTicks);
        tag.putString("Status", this.status.name());
        if (this.interceptionNetworkId != null) {
            tag.putUUID("InterceptionNetworkId", this.interceptionNetworkId);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int firstNewLine = tooltip.size();
        for (PowerRadarTooltipSettings.Line line
                : PowerRadarTooltipSettings.goggles(Target.INTERCEPTION_CONTROLLER)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> PowerRadarTooltipSettings.appendElectricalStatisticsTitle(tooltip);
                case VOLTAGE -> tooltip.add(Component.translatable("power_radar.electrical.voltage",
                        PowerRadarCeeFormatter.voltageComponent(this.powerVoltageVolts)));
                case CURRENT -> tooltip.add(Component.translatable("power_radar.electrical.current",
                        PowerRadarCeeFormatter.currentComponent(this.currentAmps)));
                case POWER -> tooltip.add(Component.translatable("power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(this.powerWatts)));
                case STATUS -> tooltip.add(Component.translatable(
                        "goggles.power_radar.interception_controller.status",
                        Component.translatable(this.status.translationKey)));
                default -> { }
            }
        }
        return PowerRadarTooltipSettings.finishGoggleTooltip(tooltip, firstNewLine);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private void logControllerState(
            ServerLevel level,
            Solution solution,
            AimStep aim,
            boolean powered,
            boolean ready
    ) {
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][Controller] pos={} reason={} networkStatus={} network={} target={} status={} voltage={} current={} power={} powered={} solution={} ammo={} desiredYaw={} currentYaw={} yawError={} desiredPitch={} currentPitch={} pitchError={} applied={} timingError={} interceptTicks={} ready={}",
                    this.worldPosition,
                    this.lastSolveReason,
                    this.lastNetworkStatus,
                    this.interceptionNetworkId,
                    this.assignedThreatUuid,
                    this.status,
                    round(this.powerVoltageVolts),
                    round(this.currentAmps),
                    round(this.powerWatts),
                    powered,
                    solution.valid,
                    solution.ammunitionAvailable,
                    round(solution.desiredYaw),
                    round(solution.currentYaw),
                    round(solution.yawError),
                    round(solution.desiredPitch),
                    round(solution.currentPitch),
                    round(solution.pitchError),
                    aim.applied,
                    round(solution.timingError),
                    round(solution.interceptTicks),
                    ready);
        }
        if (PowerRadarDebugOptions.sableInterceptionDebugLogging()
                && (this.lastSableDebugLogGameTime == Long.MIN_VALUE
                        || level.getGameTime() - this.lastSableDebugLogGameTime >= 5L)
                && RadarWorldPoseResolver.isOnSableStructure(level, this.worldPosition)) {
            this.lastSableDebugLogGameTime = level.getGameTime();
            PowerRadar.LOGGER.info(
                    "[PowerRadar Debug][SableInterception] event=controller-tick gameTime={} controller={} network={} target={} reason={} status={} powered={} solution={} clearShot={} ammo={} desiredYaw={} desiredPitch={} currentYaw={} currentPitch={} yawError={} pitchError={} remainingYaw={} remainingPitch={} applied={} interceptTicks={} timingError={} ready={} muzzle={} aimPoint={}",
                    level.getGameTime(),
                    this.worldPosition,
                    this.interceptionNetworkId,
                    this.assignedThreatUuid,
                    this.lastSolveReason,
                    this.status,
                    powered,
                    solution.valid,
                    solution.clearShot,
                    solution.ammunitionAvailable,
                    round(solution.desiredYaw),
                    round(solution.desiredPitch),
                    round(solution.currentYaw),
                    round(solution.currentPitch),
                    round(solution.yawError),
                    round(solution.pitchError),
                    round(aim.remainingYawError),
                    round(aim.remainingPitchError),
                    aim.applied,
                    round(solution.interceptTicks),
                    round(solution.timingError),
                    ready,
                    shortVec(solution.muzzle),
                    shortVec(solution.aimPoint));
        }
    }

    private static String shortVec(Vec3 vec) {
        return "(" + round(vec.x) + "," + round(vec.y) + "," + round(vec.z) + ")";
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 1000.0) / 1000.0 : value;
    }

    private record BallisticAim(boolean reachable, float pitchDegrees, double flightTicks) {
        private static final BallisticAim UNREACHABLE = new BallisticAim(false, 0.0F, 0.0);
    }

    private record Intercept(
            boolean reachable,
            Vec3 position,
            float pitchDegrees,
            double targetTicks,
            double aimTicks,
            double flightTicks,
            double timingError
    ) {
        private static final Intercept UNREACHABLE =
                new Intercept(false, Vec3.ZERO, 0.0F,
                        0.0, 0.0, 0.0, Double.MAX_VALUE);
        private static final Intercept PASSED_REFERENCE =
                new Intercept(false, Vec3.ZERO, 0.0F,
                        -1.0, 0.0, 0.0, Double.MAX_VALUE);
    }

    private record CheapTimingSample(double tick, double signedError, double cost, boolean passedReference) {
    }

    private record TimingBracket(double lowTick, double highTick) {
    }

    private record AimStep(boolean applied, float remainingYawError, float remainingPitchError) {
        private static final AimStep ZERO = new AimStep(false, 0.0F, 0.0F);
    }

    private record AimAngles(float yawDegrees, float pitchDegrees) {
    }

    private record AimFeedForward(double yawDegreesPerTick, double pitchDegreesPerTick) {
        private static final AimFeedForward ZERO = new AimFeedForward(0.0D, 0.0D);
    }

    private record PlannedIntercept(Solution solution) {
    }

    private record InterceptionWindow(double maximumTicks) {
        private boolean expired(double ticks) {
            return ticks > this.maximumTicks;
        }
    }

    private record Solution(
            boolean valid,
            WeaponMount cannonState,
            BlockPos mountPos,
            Vec3 muzzle,
            Vec3 aimPoint,
            boolean ammunitionAvailable,
            boolean clearShot,
            float desiredYaw,
            float desiredPitch,
            float currentYaw,
            float currentPitch,
            float yawError,
            float pitchError,
            double interceptTicks,
            double timingError
    ) {
        private static Solution invalid() {
            return new Solution(false, null, null, Vec3.ZERO, Vec3.ZERO, false, false,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0, Double.MAX_VALUE);
        }

        private static Solution noAmmo(WeaponMount cannon) {
            return new Solution(false, cannon, cannon.mountPos(), cannon.muzzleOrigin(),
                    cannon.muzzleOrigin(), false, false,
                    0.0F, 0.0F, cannon.currentYawDegrees(), cannon.currentPitchDegrees(),
                    0.0F, 0.0F, 0.0, Double.MAX_VALUE);
        }

        private static Solution hold(WeaponMount cannon, AimAngles currentAngles) {
            return new Solution(false, cannon, cannon.mountPos(), cannon.muzzleOrigin(),
                    cannon.muzzleOrigin(), true, false,
                    currentAngles.yawDegrees(), currentAngles.pitchDegrees(),
                    currentAngles.yawDegrees(), currentAngles.pitchDegrees(),
                    0.0F, 0.0F, 0.0, Double.MAX_VALUE);
        }

        private static Solution unreachable(WeaponMount cannon, AimAngles currentAngles) {
            return new Solution(false, cannon, cannon.mountPos(), cannon.muzzleOrigin(),
                    cannon.muzzleOrigin(), true, false,
                    0.0F, 0.0F, currentAngles.yawDegrees(), currentAngles.pitchDegrees(),
                    0.0F, 0.0F, 0.0, Double.MAX_VALUE);
        }
    }

    private enum Status {
        NO_NETWORK("power_radar.interception.status.no_network"),
        NO_THREAT("power_radar.interception.status.no_threat"),
        UNDERVOLTAGE("power_radar.interception.status.undervoltage"),
        OVERVOLTAGE("power_radar.interception.status.overvoltage"),
        NO_AMMUNITION("power_radar.interception.status.no_ammunition"),
        OBSTRUCTED("power_radar.interception.status.obstructed"),
        NO_INTERCEPT("power_radar.interception.status.no_intercept"),
        AIMING("power_radar.interception.status.aiming"),
        FIRING("power_radar.interception.status.firing");

        private final String translationKey;

        Status(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
