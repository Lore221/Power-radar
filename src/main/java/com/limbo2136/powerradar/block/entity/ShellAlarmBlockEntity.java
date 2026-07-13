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
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import com.limbo2136.powerradar.radar.network.CombinedRadarDataSource;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
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
    private final Map<UUID, Evaluation> evaluations = new HashMap<>();
    private ShellAlarmSideBehaviour protectionSide;
    private boolean networkConnected;
    private boolean alarmActive;
    private int trackedShellCount;
    private RadarDataSource lastProcessedRadar;
    private long lastProcessedRadarScanGameTime = Long.MIN_VALUE;
    private long lastStatusLogGameTime = Long.MIN_VALUE;
    private String lastStatusLog = "";
    private PowerRadarCeeSnapshot electrical = PowerRadarCeeSnapshot.EMPTY;

    public ShellAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHELL_ALARM.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        CenteredSideValueBoxTransform transform = new CenteredSideValueBoxTransform(
                (state, direction) -> direction == state.getValue(com.limbo2136.powerradar.block.ShellAlarmBlock.FACING)) {
            @Override
            public float getScale() {
                return 0.75F;
            }
        };
        this.protectionSide = new ShellAlarmSideBehaviour(
                Component.translatable("message.power_radar.shell_alarm.side"), this, transform);
        this.protectionSide.between(0, sideStepCount() - 1);
        this.protectionSide.value = sideToIndex(PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_SIDE_BLOCKS);
        this.protectionSide.withCallback(ignored -> {
            this.evaluations.clear();
            this.lastProcessedRadar = null;
            this.lastProcessedRadarScanGameTime = Long.MIN_VALUE;
            setChanged();
        });
        behaviours.add(this.protectionSide);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  ShellAlarmBlockEntity alarm) {
        if (level instanceof ServerLevel serverLevel) {
            alarm.tick();
            alarm.tickServer(serverLevel, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        boolean powered = this.electrical.electricalState() == PowerRadarCeeState.POWERED;
        RadarLinkConnectionResolver.Resolution link =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(level, this.worldPosition);
        this.networkConnected = false;
        Set<UUID> present = new HashSet<>();
        int shellCount = 0;
        long gameTime = level.getGameTime();
        UUID connectedNetworkId = null;
        RadarDataSource controller = null;

        if (powered && link.status() == RadarLinkConnectionResolver.Status.SINGLE
                && link.link().networkId() != null) {
            RadarNetworkManager.ControllersResolution resolution = RadarNetworkManager.get(level.getServer())
                    .resolveControllersForConsumer(link.link().networkId(),
                            GlobalPos.of(level.dimension(), link.link().getBlockPos()));
            controller = resolution.controllers().isEmpty() ? null : new CombinedRadarDataSource(resolution.controllers());
            this.networkConnected = controller != null;
            connectedNetworkId = link.link().networkId();
        }
        logStatus(level, powered, link, connectedNetworkId, controller);

        if (controller == null) {
            this.lastProcessedRadar = null;
            this.lastProcessedRadarScanGameTime = Long.MIN_VALUE;
            clearInactiveState(level, state);
            return;
        }
        long radarScanGameTime = controller.lastScanGameTime();
        long previousRadarScanGameTime = this.lastProcessedRadarScanGameTime;
        if (radarScanGameTime == previousRadarScanGameTime) {
            return;
        }
        this.lastProcessedRadar = controller;
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
            Evaluation evaluation = this.evaluations.get(uuid);
            if (evaluation == null) {
                ThreatEvaluation threatEvaluation = trajectoryThreatens(level, track);
                this.evaluations.put(uuid, new Evaluation(
                        gameTime,
                        track.lastSeenGameTime(),
                        false,
                        threatEvaluation.dangerous(),
                        threatEvaluation.ballistics(),
                        threatEvaluation.upperCrossing(),
                        threatEvaluation.lowerCrossing()));
            } else if (!evaluation.refined()
                    && track.lastSeenGameTime() > evaluation.trackGameTime()) {
                ThreatEvaluation threatEvaluation = trajectoryThreatens(level, track);
                this.evaluations.put(uuid, new Evaluation(
                        evaluation.firstEvaluationGameTime(),
                        track.lastSeenGameTime(),
                        true,
                        threatEvaluation.dangerous(),
                        threatEvaluation.ballistics(),
                        threatEvaluation.upperCrossing(),
                        threatEvaluation.lowerCrossing()));
            }
        });
        shellCount = count[0];

        Iterator<Map.Entry<UUID, Evaluation>> iterator = this.evaluations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!present.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
        this.trackedShellCount = shellCount;
        if (powered && this.networkConnected && connectedNetworkId != null) {
            Set<UUID> dangerousShells = new HashSet<>();
            List<ThreatSnapshot> threatSnapshots = new ArrayList<>();
            for (Map.Entry<UUID, Evaluation> entry : this.evaluations.entrySet()) {
                if (entry.getValue().dangerous()) {
                    UUID threatUuid = entry.getKey();
                    dangerousShells.add(threatUuid);
                    TrackedTargetView track = radarController.findTrackedTarget(threatUuid);
                    if (track != null && track.targetUuid() != null) {
                        Evaluation evaluation = entry.getValue();
                        threatSnapshots.add(new ThreatSnapshot(
                                threatUuid,
                                level.dimension(),
                                track.position(),
                                track.velocity(),
                                track.lastSeenGameTime(),
                                evaluation.ballistics().gravity(),
                                evaluation.ballistics().drag(),
                                evaluation.ballistics().quadraticDrag(),
                                this.worldPosition.immutable(),
                                evaluation.upperCrossing(),
                                evaluation.lowerCrossing()));
                    }
                }
            }
            InterceptionCoordinator.publishThreats(
                    level,
                    connectedNetworkId,
                    this.worldPosition,
                    threatSnapshots,
                    InterceptionCoordinator.threatTtlTicksForScanInterval(
                            radarScanIntervalTicks(radarScanGameTime, previousRadarScanGameTime)));
            if (PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
                PowerRadar.LOGGER.info(
                        "[PowerRadar BugReport][ShellAlarm][Scan] alarm={} network={} scanTick={} side={} trackedShells={} dangerousShells={}",
                        this.worldPosition,
                        connectedNetworkId,
                        radarScanGameTime,
                        protectionSideBlocks(),
                        shellCount,
                        dangerousShells.size());
            }
        }
        boolean nextActive = powered && this.networkConnected
                && this.evaluations.values().stream().anyMatch(Evaluation::dangerous);
        if (nextActive != this.alarmActive) {
            this.alarmActive = nextActive;
            notifyRedstone(level, state);
        }
        setChanged();
    }

    private void logStatus(
            ServerLevel level,
            boolean powered,
            RadarLinkConnectionResolver.Resolution link,
            UUID connectedNetworkId,
            RadarDataSource controller
    ) {
        if (!PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
            return;
        }
        UUID linkNetworkId = link.link() == null ? null : link.link().networkId();
        String status = powered + "|" + this.electrical.electricalState()
                + "|" + link.status() + "|" + linkNetworkId + "|" + (controller != null);
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
                link.status(),
                linkNetworkId,
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
        Vec3 position = track.position();
        Vec3 velocity = track.velocity();
        ShellAlarmCbcCompat.Ballistics fallbackBallistics = ShellAlarmCbcCompat.ballistics(null);
        if (!track.hasVelocity() || velocity.lengthSqr() < 1.0E-6) {
            return trajectoryResult(track, position, velocity, null, null,
                    "no-velocity", false, fallbackBallistics);
        }
        Entity entity = track.targetUuid() == null ? null : level.getEntity(track.targetUuid());
        ShellAlarmCbcCompat.Ballistics ballistics = entity == null
                ? fallbackBallistics
                : ShellAlarmCbcCompat.ballistics(entity);
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        double halfSide = protectionSideBlocks() * 0.5;
        double minX = center.x - halfSide;
        double maxX = center.x + halfSide;
        double minZ = center.z - halfSide;
        double maxZ = center.z + halfSide;
        double lowerPlaneY = center.y - PowerRadarCeeConstants.SHELL_ALARM_VERTICAL_MARGIN_BLOCKS;
        double upperPlaneY = center.y + PowerRadarCeeConstants.SHELL_ALARM_VERTICAL_MARGIN_BLOCKS;
        AABB protectedSquare = new AABB(minX, 0.0, minZ, maxX, 1.0, maxZ);

        ThreatEvaluation analytic = analyticTrajectoryThreatens(
                track,
                position,
                velocity,
                ballistics,
                upperPlaneY,
                lowerPlaneY,
                protectedSquare);
        if (analytic != null) {
            return analytic;
        }

        return simulatedTrajectoryThreatens(
                track,
                position,
                velocity,
                ballistics,
                center,
                halfSide,
                upperPlaneY,
                lowerPlaneY,
                protectedSquare);
    }

    private static long radarScanIntervalTicks(long scanGameTime, long previousScanGameTime) {
        if (previousScanGameTime == Long.MIN_VALUE || scanGameTime <= previousScanGameTime) {
            return RadarConstants.radarScanUpdateIntervalTicks();
        }
        return scanGameTime - previousScanGameTime;
    }

    private ThreatEvaluation analyticTrajectoryThreatens(
            TrackedTargetView track,
            Vec3 position,
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double upperPlaneY,
            double lowerPlaneY,
            AABB protectedSquare
    ) {
        if (ballistics.quadraticDrag() || !LinearDragTrajectory.supported(ballistics.drag())) {
            return null;
        }
        Double upperTick = descendingPlaneCrossingTick(position, velocity, ballistics, upperPlaneY);
        if (upperTick == null) {
            return null;
        }
        Double lowerTick = descendingPlaneCrossingTick(position, velocity, ballistics, lowerPlaneY);
        if (lowerTick == null || lowerTick < upperTick) {
            return null;
        }
        Vec3 upperCrossing = linearDragPositionAfterTicks(position, velocity, ballistics, upperTick);
        Vec3 lowerCrossing = linearDragPositionAfterTicks(position, velocity, ballistics, lowerTick);
        AABB trajectoryBounds = horizontalBounds(upperCrossing, lowerCrossing);
        boolean dangerous = horizontalIntersects(trajectoryBounds, protectedSquare);
        return trajectoryResult(
                track,
                lowerCrossing,
                linearDragVelocityAfterTicks(velocity, ballistics, lowerTick),
                upperCrossing,
                lowerCrossing,
                dangerous ? "analytic-protected-zone-hit" : "analytic-protected-zone-miss",
                dangerous,
                ballistics);
    }

    private ThreatEvaluation simulatedTrajectoryThreatens(
            TrackedTargetView track,
            Vec3 position,
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            Vec3 center,
            double halfSide,
            double upperPlaneY,
            double lowerPlaneY,
            AABB protectedSquare
    ) {
        Vec3 upperCrossing = null;

        for (int tick = 0; tick < PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS; tick++) {
            int substeps = Mth.clamp((int) Math.ceil(velocity.length() / 4.0), 1, 16);
            double dt = 1.0 / substeps;
            double dragFactor = dragFactor(ballistics, velocity.length(), dt);
            for (int step = 0; step < substeps; step++) {
                Vec3 previousPosition = position;
                position = position.add(velocity.scale(dt));

                Vec3 crossedUpper = descendingPlaneCrossing(previousPosition, position, upperPlaneY);
                if (crossedUpper != null) {
                    upperCrossing = crossedUpper;
                }
                    Vec3 lowerCrossing = descendingPlaneCrossing(previousPosition, position, lowerPlaneY);
                if (lowerCrossing != null) {
                    if (upperCrossing == null) {
                        return trajectoryResult(track, position, velocity, null, lowerCrossing,
                                "lower-crossing-without-upper", false, ballistics);
                    }
                    AABB trajectoryBounds = horizontalBounds(upperCrossing, lowerCrossing);
                    boolean dangerous = horizontalIntersects(trajectoryBounds, protectedSquare);
                    return trajectoryResult(track, position, velocity, upperCrossing, lowerCrossing,
                            dangerous ? "protected-zone-hit" : "protected-zone-miss", dangerous, ballistics);
                }
                velocity = velocity.scale(dragFactor)
                        .add(0.0, -ballistics.gravity() * dt, 0.0);
            }
            if (position.y < lowerPlaneY && velocity.y <= 0.0) {
                return trajectoryResult(track, position, velocity, upperCrossing, null,
                        "passed-below-protected-layer", false, ballistics);
            }
            double maxUsefulDistance = halfSide + 2048.0;
            if (position.distanceToSqr(center) > maxUsefulDistance * maxUsefulDistance
                    && position.subtract(center).dot(velocity) > 0.0) {
                return trajectoryResult(track, position, velocity, upperCrossing, null,
                        "moving-away-outside-limit", false, ballistics);
            }
        }
        return trajectoryResult(track, position, velocity, upperCrossing, null,
                "simulation-limit", false, ballistics);
    }

    private ThreatEvaluation trajectoryResult(
            TrackedTargetView track,
            Vec3 simulatedPosition,
            Vec3 simulatedVelocity,
            Vec3 upperCrossing,
            Vec3 lowerCrossing,
            String reason,
            boolean dangerous,
            ShellAlarmCbcCompat.Ballistics ballistics
    ) {
        if (PowerRadarDebugOptions.shellAlarmBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][ShellAlarm][Trajectory] alarm={} target={} entityType={} source={} trackTick={} trackPos={} trackVelocity={} simulatedPos={} simulatedVelocity={} upperCrossing={} lowerCrossing={} side={} verticalMargin={} result={} dangerous={}",
                    this.worldPosition,
                    track.targetUuid(),
                    track.entityTypeId(),
                    track.sourceType(),
                    track.lastSeenGameTime(),
                    shortVec(track.position()),
                    shortVec(track.velocity()),
                    shortVec(simulatedPosition),
                    shortVec(simulatedVelocity),
                    upperCrossing == null ? "none" : shortVec(upperCrossing),
                    lowerCrossing == null ? "none" : shortVec(lowerCrossing),
                    protectionSideBlocks(),
                    PowerRadarCeeConstants.SHELL_ALARM_VERTICAL_MARGIN_BLOCKS,
                    reason,
                    dangerous);
        }
        return new ThreatEvaluation(dangerous, ballistics, upperCrossing, lowerCrossing);
    }

    private static Double descendingPlaneCrossingTick(
            Vec3 position,
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double planeY
    ) {
        return LinearDragTrajectory.descendingPlaneCrossingTicks(
                position.y,
                velocity.y,
                ballistics.gravity(),
                ballistics.drag(),
                planeY,
                PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS);
    }

    private static Vec3 linearDragPositionAfterTicks(
            Vec3 position,
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double ticks
    ) {
        return LinearDragTrajectory.positionAfterTicks(
                position, velocity, ballistics.gravity(), ballistics.drag(), ticks);
    }

    private static Vec3 linearDragVelocityAfterTicks(
            Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double ticks
    ) {
        return LinearDragTrajectory.velocityAfterTicks(
                velocity, ballistics.gravity(), ballistics.drag(), ticks);
    }

    private static String shortVec(Vec3 vec) {
        return "(" + round(vec.x) + "," + round(vec.y) + "," + round(vec.z) + ")";
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 1000.0) / 1000.0 : value;
    }

    private static Vec3 descendingPlaneCrossing(Vec3 start, Vec3 end, double planeY) {
        if (start.y < planeY || end.y > planeY || end.y >= start.y) {
            return null;
        }
        double verticalDelta = end.y - start.y;
        if (Math.abs(verticalDelta) < 1.0E-9) {
            return null;
        }
        double t = (planeY - start.y) / verticalDelta;
        return start.add(end.subtract(start).scale(Mth.clamp(t, 0.0, 1.0)));
    }

    private static AABB horizontalBounds(Vec3 first, Vec3 second) {
        double minX = Math.min(first.x, second.x);
        double maxX = Math.max(first.x, second.x);
        double minZ = Math.min(first.z, second.z);
        double maxZ = Math.max(first.z, second.z);
        return new AABB(minX, 0.0, minZ, maxX, 1.0, maxZ);
    }

    private static boolean horizontalIntersects(AABB first, AABB second) {
        return first.maxX >= second.minX && first.minX <= second.maxX
                && first.maxZ >= second.minZ && first.minZ <= second.maxZ;
    }

    private static double dragFactor(ShellAlarmCbcCompat.Ballistics ballistics, double speed, double dt) {
        double drag = ballistics.quadraticDrag() ? ballistics.drag() * speed : ballistics.drag();
        return Math.max(0.0, 1.0 - drag * dt);
    }

    private void notifyRedstone(ServerLevel level, BlockState state) {
        Block block = state.getBlock();
        level.updateNeighborsAt(this.worldPosition, block);
        level.updateNeighbourForOutputSignal(this.worldPosition, block);
    }

    public int protectionSideBlocks() {
        int index = this.protectionSide == null
                ? sideToIndex(PowerRadarCeeConstants.SHELL_ALARM_DEFAULT_SIDE_BLOCKS)
                : this.protectionSide.getValue();
        return indexToSide(index);
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
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ProtectionRadius") && this.protectionSide != null) {
            this.protectionSide.value = sideToIndex(tag.getInt("ProtectionRadius"));
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
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm.side",
                        protectionSideBlocks(), protectionSideBlocks())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm.height",
                        PowerRadarCeeConstants.SHELL_ALARM_VERTICAL_MARGIN_BLOCKS)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("goggles.power_radar.shell_alarm.shells", this.trackedShellCount)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(this.alarmActive
                        ? "goggles.power_radar.shell_alarm.danger"
                        : "goggles.power_radar.shell_alarm.clear")
                .withStyle(this.alarmActive ? ChatFormatting.RED : ChatFormatting.GREEN));
        return true;
    }

    private record Evaluation(
            long firstEvaluationGameTime,
            long trackGameTime,
            boolean refined,
            boolean dangerous,
            ShellAlarmCbcCompat.Ballistics ballistics,
            Vec3 upperCrossing,
            Vec3 lowerCrossing
    ) {
    }

    private record ThreatEvaluation(
            boolean dangerous,
            ShellAlarmCbcCompat.Ballistics ballistics,
            Vec3 upperCrossing,
            Vec3 lowerCrossing
    ) {
    }

    private static int sideStepCount() {
        return (PowerRadarCeeConstants.SHELL_ALARM_MAX_SIDE_BLOCKS
                - PowerRadarCeeConstants.SHELL_ALARM_MIN_SIDE_BLOCKS)
                / PowerRadarCeeConstants.SHELL_ALARM_SIDE_STEP_BLOCKS + 1;
    }

    private static int sideToIndex(int side) {
        int clamped = Mth.clamp(side, PowerRadarCeeConstants.SHELL_ALARM_MIN_SIDE_BLOCKS,
                PowerRadarCeeConstants.SHELL_ALARM_MAX_SIDE_BLOCKS);
        return Math.round((float) (clamped - PowerRadarCeeConstants.SHELL_ALARM_MIN_SIDE_BLOCKS)
                / PowerRadarCeeConstants.SHELL_ALARM_SIDE_STEP_BLOCKS);
    }

    private static int indexToSide(int index) {
        int clamped = Mth.clamp(index, 0, sideStepCount() - 1);
        return PowerRadarCeeConstants.SHELL_ALARM_MIN_SIDE_BLOCKS
                + clamped * PowerRadarCeeConstants.SHELL_ALARM_SIDE_STEP_BLOCKS;
    }

    private static class ShellAlarmSideBehaviour extends ScrollValueBehaviour {
        private ShellAlarmSideBehaviour(Component label, SmartBlockEntity blockEntity,
                                        CenteredSideValueBoxTransform transform) {
            super(label, blockEntity, transform);
            withFormatter(value -> Integer.toString(indexToSide(value)));
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(
                    this.label,
                    sideStepCount() - 1,
                    4,
                    ImmutableList.of(Component.translatable("message.power_radar.shell_alarm.side_row")),
                    new ValueSettingsFormatter(settings ->
                            Component.translatable("power_radar.unit.blocks", indexToSide(settings.value()))));
        }
    }
}
