package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.api.radar.RadarDataSource;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.api.weapon.WeaponKind;
import com.limbo2136.powerradar.api.weapon.WeaponMount;
import com.limbo2136.powerradar.block.InterceptionControllerBlock;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.compat.electroenergetics.InterceptionControllerCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.interception.InterceptionBallistics;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.integration.cbc.CbcWeaponAdapter;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.targeting.TargetingMath;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;

public class InterceptionControllerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final int MAX_INTERCEPTION_TICKS = 240;
    private static final int MIN_INTERCEPTION_TICKS = 4;
    private static final int INTERCEPT_COARSE_STEP_TICKS = 3;
    private static final int INTERCEPT_FAST_STEP_TICKS = 5;
    private static final int INTERCEPT_FAST_BISECTION_ITERATIONS = 16;
    private static final int INTERCEPT_FAST_MINIMIZE_ITERATIONS = 12;
    private static final double MIN_LINEAR_DRAG = 1.0E-6;
    private static final double INTERCEPT_PITCH_HINT_RANGE_DEGREES = 10.0;
    private static final double MAX_TIMING_ERROR_TICKS = 3.0;
    private static final double AIM_TOLERANCE_DEGREES = 2.0;
    private static final double AIM_REACTION_TICKS = 1.0;
    private static final long INTERCEPTION_BURST_TICKS = 12L;
    private static final long CONTROLLER_SNAPSHOT_REFRESH_TICKS = 10L;
    private static final long AIM_ANGLE_RESYNC_TICKS = 40L;
    private static final long MOUNT_CACHE_RESYNC_TICKS = 40L;

    private boolean readyToFire;
    private double powerVoltageVolts;
    private double currentAmps;
    private double powerWatts;
    private double yawVelocityDegreesPerTick;
    private double pitchVelocityDegreesPerTick;
    private UUID networkId;
    private UUID assignedThreatUuid;
    private float desiredYawDegrees;
    private float desiredPitchDegrees;
    private float currentYawDegrees;
    private float currentPitchDegrees;
    private double interceptTicks;
    private Status status = Status.NO_NETWORK;
    private RadarLinkConnectionResolver.Status lastLinkStatus = RadarLinkConnectionResolver.Status.NONE;
    private String lastSolveReason = "startup";
    private long lastClientSyncGameTime = Long.MIN_VALUE;
    private long lastPublishedThreatRevision = Long.MIN_VALUE;
    private long lastControllerSnapshotGameTime = Long.MIN_VALUE;
    private UUID trackingThreatUuid;
    private UUID burstThreatUuid;
    private Vec3 burstAimPoint;
    private long burstEndsAtGameTime = Long.MIN_VALUE;
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

    public InterceptionControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERCEPTION_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockState state,
            InterceptionControllerBlockEntity controller
    ) {
        if (level instanceof ServerLevel serverLevel) {
            controller.tickServer(serverLevel, state);
        }
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
        if (nextReady && this.networkId != null && this.assignedThreatUuid != null) {
            if (!burstActiveFor(this.assignedThreatUuid, level.getGameTime())) {
                startBurst(this.assignedThreatUuid, solution.aimPoint, level.getGameTime());
            }
            this.trackingThreatUuid = this.assignedThreatUuid;
            InterceptionCoordinator.registerPendingLaunch(
                    level,
                    this.networkId,
                    this.worldPosition,
                    solution.muzzle,
                    this.assignedThreatUuid);
        }
        setReadyToFire(level, state, nextReady);
        this.status = statusFor(solution, powered, nextReady);
        logControllerState(solution, aim, powered, nextReady);
        setChanged();
        syncClient(level, state);
    }

    private Solution solve(ServerLevel level, BlockState state) {
        if (!ModList.get().isLoaded("createbigcannons")) {
            this.lastSolveReason = "cbc-missing";
            releaseAssignment(level);
            return Solution.invalid();
        }
        RadarLinkConnectionResolver.Resolution link =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(level, this.worldPosition);
        this.lastLinkStatus = link.status();
        if (link.status() != RadarLinkConnectionResolver.Status.SINGLE || link.link().networkId() == null) {
            this.lastSolveReason = "link-" + link.status().name().toLowerCase(java.util.Locale.ROOT);
            releaseAssignment(level);
            this.networkId = null;
            return Solution.invalid();
        }
        UUID resolvedNetworkId = link.link().networkId();
        if (this.networkId != null && !this.networkId.equals(resolvedNetworkId)) {
            releaseAssignment(level);
        }
        this.networkId = resolvedNetworkId;
        RadarNetworkManager.ControllersResolution radarResolution = RadarNetworkManager.get(level.getServer())
                .resolveControllersForConsumer(
                        resolvedNetworkId,
                        GlobalPos.of(level.dimension(), link.link().getBlockPos()));
        if (radarResolution.controllers().isEmpty()) {
            this.lastSolveReason = "radar-offline";
            releaseAssignment(level);
            return Solution.invalid();
        }
        RadarDataSource radar = new CombinedRadarDataSource(radarResolution.controllers());
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
            clearBurst();
            this.lastSolveReason = "idle";
            return Solution.invalid();
        }

        Direction facing = state.getValue(InterceptionControllerBlock.FACING);
        BlockPos mountPos = this.worldPosition.relative(facing);
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
        if (threatRevision != this.lastPublishedThreatRevision
                || snapshotDue) {
            InterceptionCoordinator.publishControllerSnapshot(
                    level,
                    resolvedNetworkId,
                    this.worldPosition,
                    new InterceptionCoordinator.ControllerSnapshot(
                            cannon.muzzleOrigin(),
                            currentAngles.yawDegrees(),
                            currentAngles.pitchDegrees(),
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
            clearBurst();
            this.lastSolveReason = "idle";
            return Solution.hold(cannon, currentAngles);
        }
        if (this.trackingThreatUuid != null
                && !this.trackingThreatUuid.equals(this.assignedThreatUuid)) {
            this.trackingThreatUuid = null;
            clearBurst();
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
        TrackedTargetView track = radar.findTrackedTarget(this.assignedThreatUuid);
        Entity targetEntity = level.getEntity(this.assignedThreatUuid);
        if ((track != null && track.sourceType() != TargetSourceType.CBC_BIG_CANNON_PROJECTILE)
                || targetEntity == null
                || !targetEntity.isAlive()) {
            rejectAssignment(level);
            this.lastSolveReason = "invalid-threat";
            return Solution.invalid();
        }
        long trackAgeTicks = Math.max(0L, level.getGameTime() - threatSnapshot.lastSeenGameTime());
        Vec3 trackedPosition = targetEntity.position();
        Vec3 trackedVelocity = targetEntity.getDeltaMovement();
        Vec3 threatReferencePoint = Vec3.atCenterOf(threatSnapshot.alarmPos());
        ShellAlarmCbcCompat.Ballistics threatBallistics = new ShellAlarmCbcCompat.Ballistics(
                threatSnapshot.gravity(),
                threatSnapshot.drag(),
                threatSnapshot.quadraticDrag());
        if (burstActiveFor(this.assignedThreatUuid, gameTime)) {
            if (hasPassedReference(trackedPosition, trackedVelocity, this.burstAimPoint)) {
                clearBurst();
                rejectAssignment(level);
                this.lastSolveReason = "burst-window-passed";
                return Solution.unreachable(cannon, currentAngles);
            }
            Vec3 delta = this.burstAimPoint.subtract(cannon.muzzleOrigin());
            BallisticAim burstAim = ballisticAim(delta, interceptorBallistics);
            boolean clearShot = burstAim.reachable
                    && hasClearLine(level, cannon.muzzleOrigin(), this.burstAimPoint);
            if (!clearShot) {
                clearBurst();
                rejectAssignment(level);
                this.lastSolveReason = burstAim.reachable ? "burst-obstructed" : "burst-unreachable";
                return Solution.unreachable(cannon, currentAngles);
            }
            float desiredYaw = TargetingMath.yawTo(delta);
            this.lastSolveReason = "burst";
            this.trackingThreatUuid = this.assignedThreatUuid;
            return new Solution(
                    true,
                    cannon,
                    cannon.mountPos(),
                    cannon.muzzleOrigin(),
                    this.burstAimPoint,
                    interceptorBallistics.available(),
                    true,
                    desiredYaw,
                    burstAim.pitchDegrees,
                    currentAngles.yawDegrees(),
                    currentAngles.pitchDegrees(),
                    Mth.wrapDegrees(desiredYaw - currentAngles.yawDegrees()),
                    Mth.wrapDegrees(burstAim.pitchDegrees - currentAngles.pitchDegrees()),
                    burstAim.flightTicks,
                    0.0);
        }
        clearBurst();
        Intercept intercept = findIntercept(
                cannon.muzzleOrigin(),
                threatReferencePoint,
                trackedPosition,
                trackedVelocity,
                threatBallistics,
                interceptorBallistics,
                currentAngles.yawDegrees(),
                currentAngles.pitchDegrees(),
                maxStepDegreesPerTick(),
                aimAccelerationDegreesPerTickSquared(),
                !this.assignedThreatUuid.equals(this.trackingThreatUuid));
        if (!intercept.reachable || intercept.timingError > MAX_TIMING_ERROR_TICKS) {
            rejectAssignment(level);
            this.lastSolveReason = "no-intercept-solution";
            return Solution.unreachable(cannon, currentAngles);
        }
        boolean clearShot = hasClearLine(level, cannon.muzzleOrigin(), intercept.position);
        if (!clearShot) {
            rejectAssignment(level);
            this.lastSolveReason = "obstructed";
        } else {
            this.lastSolveReason = "ok";
            this.trackingThreatUuid = this.assignedThreatUuid;
        }
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][Target] controller={} network={} target={} trackAge={} trackingSource=entity shellPos={} shellVelocity={} shellGravity={} shellDrag={} shellQuadraticDrag={} cannon={} interceptorSpeed={} interceptorGravity={} interceptorDrag={} muzzle={} logicalPitch={} physicalPitch={} pitchMultiplier={} interceptPos={} clearShot={} targetTicks={} aimTicks={} shotTicks={} timingError={}",
                    this.worldPosition,
                    resolvedNetworkId,
                    this.assignedThreatUuid,
                    trackAgeTicks,
                    shortVec(trackedPosition),
                    shortVec(trackedVelocity),
                    round(threatBallistics.gravity()),
                    round(threatBallistics.drag()),
                    threatBallistics.quadraticDrag(),
                    cannon.kind(),
                    round(interceptorBallistics.speedBlocksPerTick()),
                    round(interceptorBallistics.gravityBlocksPerTickSquared()),
                    round(interceptorBallistics.drag()),
                    shortVec(cannon.muzzleOrigin()),
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
        Vec3 delta = intercept.position.subtract(cannon.muzzleOrigin());
        float desiredYaw = TargetingMath.yawTo(delta);
        float desiredPitch = intercept.pitchDegrees;
        return new Solution(
                true,
                cannon,
                cannon.mountPos(),
                cannon.muzzleOrigin(),
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

    private static Intercept findIntercept(
            Vec3 muzzle,
            Vec3 threatReferencePoint,
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
        Intercept fastIntercept = findLinearDragIntercept(
                muzzle,
                threatReferencePoint,
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

        Vec3 position = shellPosition;
        Vec3 velocity = shellVelocity;
        Intercept best = Intercept.UNREACHABLE;
        List<Intercept> window = preAim ? new ArrayList<>() : List.of();
        for (int tick = 1; tick <= MAX_INTERCEPTION_TICKS; tick++) {
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
            if (hasPassedReference(position, velocity, threatReferencePoint)) {
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
            int firstThirdIndex = (window.size() - 1) / 3;
            best = window.get(firstThirdIndex);
        }
        if (!best.reachable) {
            return best;
        }

        int refineStart = Math.max(MIN_INTERCEPTION_TICKS,
                (int) Math.floor(best.targetTicks) - INTERCEPT_COARSE_STEP_TICKS);
        int refineEnd = Math.min(MAX_INTERCEPTION_TICKS,
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
            if (hasPassedReference(position, velocity, threatReferencePoint)) {
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
            Vec3 threatReferencePoint,
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

        Intercept best = Intercept.UNREACHABLE;
        List<Intercept> window = preAim ? new ArrayList<>() : List.of();
        List<CheapTimingSample> samples = new ArrayList<>();
        List<TimingBracket> signChangeBrackets = new ArrayList<>();
        CheapTimingSample previous = null;
        double pitchHint = Double.NaN;

        for (double tick = MIN_INTERCEPTION_TICKS;
                tick <= MAX_INTERCEPTION_TICKS + 0.0001;
                tick += INTERCEPT_FAST_STEP_TICKS) {
            CheapTimingSample sample = approximateLinearDragInterceptAt(
                    muzzle,
                    threatReferencePoint,
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
                    threatReferencePoint,
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
                threatReferencePoint,
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
            window.sort(java.util.Comparator.comparingDouble(Intercept::targetTicks));
            return window.get((window.size() - 1) / 3);
        }
        return best;
    }

    private static double currentError(Intercept intercept) {
        return intercept.reachable ? intercept.timingError : Double.MAX_VALUE;
    }

    private static Intercept refineBestLinearDragMinimum(
            Vec3 muzzle,
            Vec3 threatReferencePoint,
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
                : Math.min(MAX_INTERCEPTION_TICKS, bestSample.tick + INTERCEPT_FAST_STEP_TICKS);
        return minimizeLinearDragIntercept(
                muzzle,
                threatReferencePoint,
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
            Vec3 threatReferencePoint,
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
                    muzzle, threatReferencePoint, shellPosition, shellVelocity, shellBallistics,
                    interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, left, pitchHint);
            if (leftIntercept.reachable) {
                pitchHint = leftIntercept.pitchDegrees;
            }
            Intercept rightIntercept = evaluateLinearDragInterceptAt(
                    muzzle, threatReferencePoint, shellPosition, shellVelocity, shellBallistics,
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
                muzzle, threatReferencePoint, shellPosition, shellVelocity, shellBallistics,
                interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, tick, pitchHint);
    }

    private static Intercept refineLinearDragInterceptRoot(
            Vec3 muzzle,
            Vec3 threatReferencePoint,
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
        Intercept lowIntercept = evaluateLinearDragInterceptAt(
                muzzle, threatReferencePoint, shellPosition, shellVelocity, shellBallistics,
                interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, low, pitchHint);
        if (lowIntercept.reachable) {
            pitchHint = lowIntercept.pitchDegrees;
        }
        double lowError = interceptTimingErrorSigned(lowIntercept);
        Intercept best = lowIntercept;

        for (int i = 0; i < INTERCEPT_FAST_BISECTION_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            Intercept midIntercept = evaluateLinearDragInterceptAt(
                    muzzle, threatReferencePoint, shellPosition, shellVelocity, shellBallistics,
                    interceptorBallistics, currentYaw, currentPitch, maxStep, acceleration, mid, pitchHint);
            if (betterIntercept(midIntercept, best)) {
                best = midIntercept;
            }
            if (midIntercept.reachable) {
                pitchHint = midIntercept.pitchDegrees;
            }
            double midError = interceptTimingErrorSigned(midIntercept);
            if (!Double.isFinite(midError)) {
                high = mid;
                continue;
            }
            if (Math.signum(midError) == Math.signum(lowError)) {
                low = mid;
                lowError = midError;
            } else {
                high = mid;
            }
        }
        return best;
    }

    private static CheapTimingSample approximateLinearDragInterceptAt(
            Vec3 muzzle,
            Vec3 threatReferencePoint,
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
        Vec3 position = linearDragShellPosition(shellPosition, shellVelocity, shellBallistics, targetTicks);
        Vec3 velocity = linearDragShellVelocity(shellVelocity, shellBallistics, targetTicks);
        if (hasPassedReference(position, velocity, threatReferencePoint)) {
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
            Vec3 threatReferencePoint,
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
        return evaluateLinearDragInterceptAt(
                muzzle,
                threatReferencePoint,
                shellPosition,
                shellVelocity,
                shellBallistics,
                interceptorBallistics,
                currentYaw,
                currentPitch,
                maxStep,
                acceleration,
                targetTicks,
                Double.NaN);
    }

    private static Intercept evaluateLinearDragInterceptAt(
            Vec3 muzzle,
            Vec3 threatReferencePoint,
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
        Vec3 position = linearDragShellPosition(shellPosition, shellVelocity, shellBallistics, targetTicks);
        Vec3 velocity = linearDragShellVelocity(shellVelocity, shellBallistics, targetTicks);
        if (hasPassedReference(position, velocity, threatReferencePoint)) {
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

    private static Vec3 linearDragShellPosition(
            Vec3 position,
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double ticks
    ) {
        double drag = ballistics.drag();
        double retention = 1.0 - drag;
        double stepScale = 1.0 - drag * 0.5;
        double velocityGeometricSum = (1.0 - Math.pow(retention, ticks)) / drag;
        double gravityVelocitySum = ballistics.gravity() / drag * (ticks - velocityGeometricSum);
        return new Vec3(
                position.x + velocity.x * stepScale * velocityGeometricSum,
                position.y + stepScale * (velocity.y * velocityGeometricSum - gravityVelocitySum)
                        - ballistics.gravity() * ticks * 0.5,
                position.z + velocity.z * stepScale * velocityGeometricSum);
    }

    private static Vec3 linearDragShellVelocity(
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double ticks
    ) {
        double retentionPower = Math.pow(1.0 - ballistics.drag(), ticks);
        double gravityVelocity = ballistics.gravity() / ballistics.drag() * (1.0 - retentionPower);
        return new Vec3(
                velocity.x * retentionPower,
                velocity.y * retentionPower - gravityVelocity,
                velocity.z * retentionPower);
    }

    private static boolean hasPassedReference(
            Vec3 shellPosition,
            Vec3 shellVelocity,
            Vec3 referencePoint
    ) {
        double deltaX = shellPosition.x - referencePoint.x;
        double deltaZ = shellPosition.z - referencePoint.z;
        return deltaX * shellVelocity.x + deltaZ * shellVelocity.z >= 0.0;
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
        InterceptionBallistics.Aim aim = InterceptionBallistics.solveLowArc(
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

    private AimStep applyAim(ServerLevel level, Solution solution) {
        double maxStep = maxStepDegreesPerTick();
        double acceleration = aimAccelerationDegreesPerTickSquared();
        double targetYawVelocity = clamp(
                solution.yawError * PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK,
                -maxStep, maxStep);
        double targetPitchVelocity = clamp(
                solution.pitchError * PowerRadarCeeConstants.TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK,
                -maxStep, maxStep);
        this.yawVelocityDegreesPerTick = approach(
                this.yawVelocityDegreesPerTick, targetYawVelocity, acceleration);
        this.pitchVelocityDegreesPerTick = approach(
                this.pitchVelocityDegreesPerTick, targetPitchVelocity, acceleration);
        float yawStep = (float) clamp(
                this.yawVelocityDegreesPerTick, -Math.abs(solution.yawError), Math.abs(solution.yawError));
        float pitchStep = (float) clamp(
                this.pitchVelocityDegreesPerTick, -Math.abs(solution.pitchError), Math.abs(solution.pitchError));
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
                Mth.wrapDegrees(solution.desiredYaw - nextYaw),
                Mth.wrapDegrees(solution.desiredPitch - nextPitch));
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
        double fraction = clamp(
                (voltage - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE)
                        / (PowerRadarCeeConstants.TARGET_CONTROLLER_FULL_SPEED_VOLTAGE
                        - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE),
                0.0, 1.0);
        double rpm = PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM
                + (PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_RPM
                - PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_RPM) * fraction;
        rpm *= PowerRadarCeeConstants.INTERCEPTION_CONTROLLER_SPEED_MULTIPLIER;
        return rpm * 360.0 / 60.0 / 20.0;
    }

    private boolean validVoltage() {
        double voltage = Math.abs(this.powerVoltageVolts);
        return voltage >= PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_POWER_VOLTAGE
                && voltage <= PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE;
    }

    private void releaseAssignment(ServerLevel level) {
        if (this.networkId != null) {
            InterceptionCoordinator.unregisterController(
                    level.getServer(), this.networkId, this.worldPosition);
        }
        this.assignedThreatUuid = null;
        this.trackingThreatUuid = null;
        clearBurst();
        this.lastPublishedThreatRevision = Long.MIN_VALUE;
        this.lastControllerSnapshotGameTime = Long.MIN_VALUE;
    }

    private void rejectAssignment(ServerLevel level) {
        if (this.networkId != null && this.assignedThreatUuid != null) {
            InterceptionCoordinator.rejectAssignment(
                    level, this.networkId, this.worldPosition, this.assignedThreatUuid);
        }
        this.assignedThreatUuid = null;
        this.trackingThreatUuid = null;
        clearBurst();
        this.lastControllerSnapshotGameTime = Long.MIN_VALUE;
    }

    private boolean burstActiveFor(UUID threatUuid, long gameTime) {
        return threatUuid != null
                && threatUuid.equals(this.burstThreatUuid)
                && this.burstAimPoint != null
                && gameTime < this.burstEndsAtGameTime;
    }

    private void startBurst(UUID threatUuid, Vec3 aimPoint, long gameTime) {
        this.burstThreatUuid = threatUuid;
        this.burstAimPoint = aimPoint;
        this.burstEndsAtGameTime = gameTime + INTERCEPTION_BURST_TICKS;
    }

    private void clearBurst() {
        this.burstThreatUuid = null;
        this.burstAimPoint = null;
        this.burstEndsAtGameTime = Long.MIN_VALUE;
    }

    private Status statusFor(Solution solution, boolean powered, boolean ready) {
        if (!powered) {
            return Math.abs(this.powerVoltageVolts) > PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_POWER_VOLTAGE
                    ? Status.OVERVOLTAGE : Status.UNDERVOLTAGE;
        }
        if (this.networkId == null) {
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
                    this.networkId,
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        invalidateEstimatedAimAngles();
        invalidateAutocannonBallisticsCache();
        invalidateWeaponKindCache();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.interception_controller")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(this.powerVoltageVolts)));
        tooltip.add(Component.translatable("power_radar.electrical.current",
                PowerRadarCeeFormatter.currentComponent(this.currentAmps)));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(this.powerWatts)));
        tooltip.add(Component.translatable("goggles.power_radar.interception_controller.status",
                Component.translatable(this.status.translationKey)));
        if (this.assignedThreatUuid != null) {
            tooltip.add(Component.translatable(
                    "goggles.power_radar.interception_controller.intercept_time",
                    Math.round(this.interceptTicks)));
        }
        return true;
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

    private static double approach(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        if (current > target) {
            return Math.max(target, current - maxDelta);
        }
        return current;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private void logControllerState(
            Solution solution,
            AimStep aim,
            boolean powered,
            boolean ready
    ) {
        if (!PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][Interception][Controller] pos={} reason={} linkStatus={} network={} target={} status={} voltage={} current={} power={} powered={} solution={} ammo={} desiredYaw={} currentYaw={} yawError={} desiredPitch={} currentPitch={} pitchError={} applied={} timingError={} interceptTicks={} ready={}",
                this.worldPosition,
                this.lastSolveReason,
                this.lastLinkStatus,
                this.networkId,
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
