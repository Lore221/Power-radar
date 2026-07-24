package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Эфемерный серверный координатор угроз, назначений и ожидаемых запусков.
 * Состояние разделено по MinecraftServer и UUID сети, а контроллеры — ещё по измерению и Sable.
 */
public final class InterceptionCoordinator {
    private static final long DEFAULT_THREAT_TTL_TICKS = 20L;
    private static final long THREAT_TTL_SAFETY_MARGIN_TICKS = 10L;
    private static final long MAX_THREAT_TTL_TICKS = 1210L;
    private static final long RESERVATION_TTL_TICKS = 10L;
    private static final long PENDING_LAUNCH_TTL_TICKS = 40L;
    private static final long INTERCEPTOR_ASSIGNMENT_TTL_TICKS = 5L;
    private static final long DESTRUCTION_CHANCE_TTL_TICKS = 1200L;
    private static final long CONTROLLER_SNAPSHOT_TTL_TICKS = 20L;
    private static final long REJECTED_ASSIGNMENT_TTL_TICKS = 8L;
    private static final double PENDING_LAUNCH_BIND_DISTANCE = 20.0;
    private static final double RESERVATION_STEAL_MARGIN_TICKS = 2.0;
    private static final double TARGET_SWITCH_MARGIN_TICKS = 3.0;
    private static final Map<MinecraftServer, ServerState> SERVERS = new WeakHashMap<>();

    private InterceptionCoordinator() {
    }

    public static synchronized void publishThreats(
            ServerLevel level,
            UUID networkId,
            BlockPos alarmPos,
            Set<UUID> threatUuids
    ) {
        List<ThreatSnapshot> snapshots = threatUuids.stream()
                .map(uuid -> new ThreatSnapshot(
                        uuid,
                        level.dimension(),
                        Vec3.ZERO,
                        Vec3.ZERO,
                        level.getGameTime(),
                        0.05,
                        0.0,
                        false,
                        Vec3.atCenterOf(alarmPos),
                        Vec3.ZERO,
                        Vec3.ZERO,
                        level.getGameTime(),
                        null,
                        null))
                .toList();
        publishThreats(level, networkId, alarmPos, snapshots);
    }

    public static synchronized void publishThreats(
            ServerLevel level,
            UUID networkId,
            BlockPos alarmPos,
            List<ThreatSnapshot> threatSnapshots
    ) {
        publishThreats(level, networkId, alarmPos, threatSnapshots, DEFAULT_THREAT_TTL_TICKS);
    }

    public static synchronized void publishThreats(
            ServerLevel level,
            UUID networkId,
            BlockPos alarmPos,
            List<ThreatSnapshot> threatSnapshots,
            long threatTtlTicks
    ) {
        // Публикация корня авторитетна: отсутствующие UUID снимаются немедленно, TTL лишь страхует пропуски.
        long gameTime = level.getGameTime();
        long expiresAt = gameTime + sanitizeThreatTtl(threatTtlTicks);
        NetworkState network = network(level.getServer(), networkId);
        boolean changed = false;
        Set<UUID> publishedThreats = new HashSet<>();
        for (ThreatSnapshot snapshot : threatSnapshots) {
            UUID threatUuid = snapshot.threatUuid();
            publishedThreats.add(threatUuid);
            Threat previous = network.threats.put(threatUuid,
                    new Threat(
                            snapshot.dimension(),
                            snapshot.referencePosition(),
                            snapshot.referenceVelocity(),
                            snapshot.referenceAcceleration(),
                            snapshot.referenceGameTime(),
                            snapshot.position(),
                            snapshot.velocity(),
                            snapshot.lastSeenGameTime(),
                            snapshot.gravity(),
                            snapshot.drag(),
                            snapshot.quadraticDrag(),
                            snapshot.upperCrossing(),
                            snapshot.lowerCrossing(),
                            expiresAt));
            if (previous == null) {
                network.threatRevision++;
                changed = true;
            }
        }
        if (retainOnlyPublishedThreats(network.threats, publishedThreats)) {
            network.threatRevision++;
            changed = true;
        }
        cleanup(network, gameTime);
        if (changed || (!publishedThreats.isEmpty() && network.assignments.isEmpty())) {
            rebuildAssignments(authoritativeLevel(level), network);
        }
    }

    static boolean retainOnlyPublishedThreats(Map<UUID, ?> threats, Set<UUID> publishedThreats) {
        return threats.keySet().removeIf(threatUuid -> !publishedThreats.contains(threatUuid));
    }

    public static synchronized void clearPublishedThreats(
            MinecraftServer server,
            UUID networkId,
            long gameTime
    ) {
        ServerState state = SERVERS.get(server);
        if (state == null) {
            return;
        }
        NetworkState network = state.networks.get(networkId);
        if (network == null || network.threats.isEmpty()) {
            return;
        }
        network.threats.clear();
        network.threatRevision++;
        cleanup(network, gameTime);
    }

    public static synchronized void removeNetwork(MinecraftServer server, UUID networkId) {
        ServerState state = SERVERS.get(server);
        if (state == null) {
            return;
        }
        state.networks.remove(networkId);
        state.interceptorTargets.values().removeIf(assignment -> assignment.networkId.equals(networkId));
    }

    public static long threatTtlTicksForScanInterval(long scanIntervalTicks) {
        long safeInterval = Math.max(1L, scanIntervalTicks);
        return sanitizeThreatTtl(safeInterval + THREAT_TTL_SAFETY_MARGIN_TICKS);
    }

    private static long sanitizeThreatTtl(long threatTtlTicks) {
        return Math.clamp(threatTtlTicks, DEFAULT_THREAT_TTL_TICKS, MAX_THREAT_TTL_TICKS);
    }

    public static synchronized long threatRevision(
            MinecraftServer server,
            UUID networkId,
            long gameTime
    ) {
        NetworkState network = network(server, networkId);
        cleanup(network, gameTime);
        return network.threatRevision;
    }

    public static synchronized void publishControllerSnapshot(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos,
            ControllerSnapshot snapshot
    ) {
        long gameTime = level.getGameTime();
        NetworkState network = network(level.getServer(), networkId);
        cleanup(network, gameTime);
        InterceptionControllerKey immutableControllerPos = controllerKey(level, controllerPos);
        ControllerState previous = network.controllers.get(immutableControllerPos);
        network.controllers.put(immutableControllerPos,
                new ControllerState(snapshot, gameTime + CONTROLLER_SNAPSHOT_TTL_TICKS));
        if (previous != null && assignmentEquivalent(previous.snapshot, snapshot)) {
            return;
        }
        rebuildAssignments(authoritativeLevel(level), network);
    }

    @Nullable
    public static synchronized UUID assignedThreat(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos
    ) {
        NetworkState network = network(level.getServer(), networkId);
        cleanup(network, level.getGameTime());
        return network.assignments.get(controllerKey(level, controllerPos));
    }

    @Nullable
    public static synchronized Vec3 threatReferencePoint(
            MinecraftServer server,
            UUID networkId,
            UUID threatUuid,
            long gameTime
    ) {
        NetworkState network = network(server, networkId);
        cleanup(network, gameTime);
        Threat threat = network.threats.get(threatUuid);
        return threat == null ? null : projectedReference(threat, gameTime);
    }

    @Nullable
    public static synchronized ThreatSnapshot threatSnapshot(
            MinecraftServer server,
            UUID networkId,
            UUID threatUuid,
            long gameTime
    ) {
        NetworkState network = network(server, networkId);
        cleanup(network, gameTime);
        Threat threat = network.threats.get(threatUuid);
        return threat == null ? null : snapshot(threatUuid, threat);
    }

    public static synchronized void rejectAssignment(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos,
            @Nullable UUID threatUuid
    ) {
        if (threatUuid == null) {
            return;
        }
        long gameTime = level.getGameTime();
        NetworkState network = network(level.getServer(), networkId);
        InterceptionControllerKey controllerKey = controllerKey(level, controllerPos);
        network.rejections.put(
                new ControllerThreat(controllerKey, threatUuid),
                gameTime + REJECTED_ASSIGNMENT_TTL_TICKS);
        network.assignments.remove(controllerKey);
        rebuildAssignments(authoritativeLevel(level), network);
    }

    public static synchronized void unregisterController(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos
    ) {
        NetworkState network = network(level.getServer(), networkId);
        InterceptionControllerKey controllerKey = controllerKey(level, controllerPos);
        network.controllers.remove(controllerKey);
        network.assignments.remove(controllerKey);
        network.rejections.keySet().removeIf(key -> key.controllerPos.equals(controllerKey));
    }

    @Nullable
    public static synchronized UUID claimBestThreat(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos,
            @Nullable UUID currentThreat,
            List<ThreatBid> bids
    ) {
        long gameTime = level.getGameTime();
        NetworkState network = network(level.getServer(), networkId);
        cleanup(network, gameTime);

        // Резервацию можно забрать только при заметно более раннем перехвате; текущая цель имеет гистерезис.
        ThreatBid currentBid = null;
        ThreatBid bestBid = null;
        for (ThreatBid bid : bids) {
            if (!network.threats.containsKey(bid.threatUuid)) {
                continue;
            }
            Threat threat = network.threats.get(bid.threatUuid);
            if (threat.dimension != level.dimension()) {
                continue;
            }
            Reservation reservation = network.reservations.get(bid.threatUuid);
            boolean owned = reservation != null
                    && reservation.controllerPos.equals(controllerKey(level, controllerPos));
            boolean available = reservation == null
                    || reservation.expiresAt < gameTime
                    || owned
                    || bid.engagementTicks + RESERVATION_STEAL_MARGIN_TICKS < reservation.engagementTicks;
            if (!available) {
                continue;
            }
            if (currentThreat != null && currentThreat.equals(bid.threatUuid) && owned) {
                currentBid = bid;
            }
            if (bestBid == null || bid.priorityScore < bestBid.priorityScore) {
                bestBid = bid;
            }
        }

        ThreatBid selected = bestBid;
        if (currentBid != null && (bestBid == null
                || currentBid.priorityScore <= bestBid.priorityScore + TARGET_SWITCH_MARGIN_TICKS)) {
            selected = currentBid;
        }
        if (selected == null) {
            releaseOwnedReservation(network, controllerKey(level, controllerPos), currentThreat);
            return null;
        }
        if (currentThreat != null && !currentThreat.equals(selected.threatUuid)) {
            releaseOwnedReservation(network, controllerKey(level, controllerPos), currentThreat);
        }
        network.reservations.put(selected.threatUuid,
                new Reservation(controllerKey(level, controllerPos), selected.engagementTicks,
                        gameTime + RESERVATION_TTL_TICKS));
        return selected.threatUuid;
    }

    public static synchronized void releaseThreat(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos,
            @Nullable UUID threatUuid
    ) {
        if (threatUuid == null) {
            return;
        }
        NetworkState network = network(level.getServer(), networkId);
        Reservation reservation = network.reservations.get(threatUuid);
        if (reservation != null && reservation.controllerPos.equals(controllerKey(level, controllerPos))) {
            network.reservations.remove(threatUuid);
        }
    }

    public static synchronized void registerPendingLaunch(
            ServerLevel level,
            UUID networkId,
            BlockPos controllerPos,
            Vec3 muzzlePos,
            UUID threatUuid
    ) {
        ServerState server = server(level.getServer());
        cleanupAssignments(server, level.getGameTime());
        NetworkState network = network(level.getServer(), networkId);
        network.pendingLaunches.put(controllerKey(level, controllerPos),
                new PendingLaunch(muzzlePos, threatUuid,
                        level.getGameTime() + PENDING_LAUNCH_TTL_TICKS));
    }

    @Nullable
    public static synchronized UUID bindInterceptor(ServerLevel level, UUID interceptorUuid, Vec3 position) {
        ServerState server = server(level.getServer());
        long gameTime = level.getGameTime();
        cleanupAssignments(server, gameTime);
        InterceptorAssignment existing = server.interceptorTargets.get(interceptorUuid);
        if (existing != null) {
            server.interceptorTargets.put(interceptorUuid,
                    new InterceptorAssignment(existing.networkId, existing.targetUuid,
                            gameTime + INTERCEPTOR_ASSIGNMENT_TTL_TICKS));
            return existing.targetUuid;
        }
        PendingCandidate best = null;
        for (Map.Entry<UUID, NetworkState> networkEntry : server.networks.entrySet()) {
            NetworkState network = networkEntry.getValue();
            cleanup(network, gameTime);
            for (Map.Entry<InterceptionControllerKey, PendingLaunch> entry : network.pendingLaunches.entrySet()) {
                PendingLaunch launch = entry.getValue();
                if (entry.getKey().dimension() != level.dimension() || launch.expiresAt < gameTime) {
                    continue;
                }
                double distance = launch.muzzlePos.distanceToSqr(position);
                if (distance > PENDING_LAUNCH_BIND_DISTANCE * PENDING_LAUNCH_BIND_DISTANCE) {
                    continue;
                }
                if (best == null || distance < best.distanceSqr) {
                    best = new PendingCandidate(networkEntry.getKey(), launch, distance);
                }
            }
        }
        if (best == null) {
            return null;
        }
        server.interceptorTargets.put(interceptorUuid,
                new InterceptorAssignment(best.networkId, best.launch.threatUuid,
                        gameTime + INTERCEPTOR_ASSIGNMENT_TTL_TICKS));
        return best.launch.threatUuid;
    }

    @Nullable
    public static synchronized UUID interceptorTarget(ServerLevel level, UUID interceptorUuid) {
        ServerState server = server(level.getServer());
        long gameTime = level.getGameTime();
        cleanupAssignments(server, gameTime);
        InterceptorAssignment assignment = server.interceptorTargets.get(interceptorUuid);
        if (assignment == null) {
            return null;
        }
        server.interceptorTargets.put(interceptorUuid,
                new InterceptorAssignment(assignment.networkId, assignment.targetUuid,
                        gameTime + INTERCEPTOR_ASSIGNMENT_TTL_TICKS));
        return assignment.targetUuid;
    }

    public static synchronized void clearInterceptor(MinecraftServer server, UUID interceptorUuid) {
        server(server).interceptorTargets.remove(interceptorUuid);
    }

    public static synchronized List<ThreatSnapshot> interceptorThreatSnapshots(
            ServerLevel level,
            UUID interceptorUuid
    ) {
        ServerState server = server(level.getServer());
        long gameTime = level.getGameTime();
        cleanupAssignments(server, gameTime);
        InterceptorAssignment assignment = server.interceptorTargets.get(interceptorUuid);
        if (assignment == null) {
            return List.of();
        }
        NetworkState network = server.networks.get(assignment.networkId);
        if (network == null) {
            return List.of();
        }
        cleanup(network, gameTime);
        List<ThreatSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<UUID, Threat> entry : network.threats.entrySet()) {
            Threat threat = entry.getValue();
            if (threat.dimension != level.dimension()) {
                continue;
            }
            snapshots.add(snapshot(entry.getKey(), threat));
        }
        return snapshots;
    }

    public static synchronized DestructionRoll rollDestruction(
            ServerLevel level,
            UUID threatUuid,
            double startingProbability
    ) {
        ServerState state = server(level.getServer());
        long gameTime = level.getGameTime();
        state.destructionChances.entrySet().removeIf(
                entry -> entry.getValue().lastAttemptAt + DESTRUCTION_CHANCE_TTL_TICKS < gameTime);

        double configuredStart = Math.max(0.001, Math.min(1.0, startingProbability));
        DestructionChance existing = state.destructionChances.get(threatUuid);
        int failedAttempts = existing == null ? 0 : existing.failedAttempts;
        double probability = Math.min(1.0, configuredStart * (failedAttempts + 1));
        double roll = level.random.nextDouble();
        boolean destroyed = roll < probability;
        double nextProbability = destroyed
                ? configuredStart
                : Math.min(1.0, configuredStart * (failedAttempts + 2));

        if (destroyed) {
            state.destructionChances.remove(threatUuid);
        } else {
            state.destructionChances.put(
                    threatUuid, new DestructionChance(failedAttempts + 1, gameTime));
        }
        return new DestructionRoll(probability, roll, nextProbability, destroyed);
    }

    public static synchronized void resolveThreat(MinecraftServer server, UUID threatUuid) {
        ServerState state = server(server);
        for (NetworkState network : state.networks.values()) {
            network.threats.remove(threatUuid);
            network.reservations.remove(threatUuid);
            network.pendingLaunches.values().removeIf(launch -> launch.threatUuid.equals(threatUuid));
        }
        state.interceptorTargets.values().removeIf(assignment -> assignment.targetUuid.equals(threatUuid));
        state.destructionChances.remove(threatUuid);
    }

    public static synchronized Set<UUID> activeThreats(MinecraftServer server, UUID networkId, long gameTime) {
        NetworkState network = network(server, networkId);
        cleanup(network, gameTime);
        return Set.copyOf(network.threats.keySet());
    }

    private static void releaseOwnedReservation(
            NetworkState network,
            InterceptionControllerKey controllerPos,
            @Nullable UUID threatUuid
    ) {
        if (threatUuid == null) {
            return;
        }
        Reservation reservation = network.reservations.get(threatUuid);
        if (reservation != null && reservation.controllerPos.equals(controllerPos)) {
            network.reservations.remove(threatUuid);
        }
    }

    private static ThreatSnapshot snapshot(UUID threatUuid, Threat threat) {
        return new ThreatSnapshot(
                threatUuid,
                threat.dimension,
                threat.position,
                threat.velocity,
                threat.lastSeenGameTime,
                threat.gravity,
                threat.drag,
                threat.quadraticDrag,
                threat.referencePosition,
                threat.referenceVelocity,
                threat.referenceAcceleration,
                threat.referenceGameTime,
                threat.upperCrossing,
                threat.lowerCrossing);
    }

    private static void cleanup(NetworkState network, long gameTime) {
        // Все ссылки на исчезнувшие угрозы удаляются в том же проходе, чтобы не оставлять сиротские аренды.
        boolean threatsRemoved = network.threats.entrySet().removeIf(
                entry -> entry.getValue().expiresAt < gameTime);
        if (threatsRemoved) {
            network.threatRevision++;
        }
        network.reservations.entrySet().removeIf(entry ->
                entry.getValue().expiresAt < gameTime || !network.threats.containsKey(entry.getKey()));
        network.pendingLaunches.entrySet().removeIf(entry ->
                entry.getValue().expiresAt < gameTime || !network.threats.containsKey(entry.getValue().threatUuid));
        network.controllers.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
        network.rejections.entrySet().removeIf(entry -> entry.getValue() < gameTime
                || !network.threats.containsKey(entry.getKey().threatUuid));
        network.assignments.entrySet().removeIf(entry ->
                !network.controllers.containsKey(entry.getKey())
                        || !network.threats.containsKey(entry.getValue()));
    }

    private static void rebuildAssignments(ServerLevel level, NetworkState network) {
        long gameTime = level.getGameTime();
        cleanup(network, gameTime);
        network.assignments.keySet().removeIf(controllerPos -> controllerPos.dimension() == level.dimension());
        if (network.controllers.isEmpty() || network.threats.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Threat>> threats = network.threats.entrySet().stream()
                .filter(entry -> entry.getValue().dimension == level.dimension())
                .sorted(Comparator.comparingDouble(entry -> threatUrgency(level, entry.getKey(), entry.getValue())))
                .toList();
        if (threats.isEmpty()) {
            return;
        }
        List<InterceptionControllerKey> availableControllers = new ArrayList<>();
        for (Map.Entry<InterceptionControllerKey, ControllerState> entry : network.controllers.entrySet()) {
            if (entry.getKey().dimension() == level.dimension() && entry.getValue().snapshot.available) {
                availableControllers.add(entry.getKey());
            }
        }
        if (availableControllers.isEmpty()) {
            return;
        }

        // Угрозы сначала образуют слоты по срочности, затем каждый слот получает самый дешёвый контроллер.
        int controllerCount = availableControllers.size();
        List<UUID> slots = new ArrayList<>(controllerCount);
        for (int index = 0; index < controllerCount; index++) {
            slots.add(threats.get(index % threats.size()).getKey());
        }

        Set<InterceptionControllerKey> assignedControllers = new HashSet<>();
        for (UUID threatUuid : slots) {
            InterceptionControllerKey bestController = null;
            double bestCost = Double.MAX_VALUE;
            for (InterceptionControllerKey controllerPos : availableControllers) {
                if (assignedControllers.contains(controllerPos)
                        || network.rejections.containsKey(new ControllerThreat(controllerPos, threatUuid))) {
                    continue;
                }
                Threat threat = network.threats.get(threatUuid);
                if (threat == null) {
                    continue;
                }
                ControllerSnapshot snapshot = network.controllers.get(controllerPos).snapshot;
                double cost = roughEngagementCost(level, snapshot, threatUuid);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestController = controllerPos;
                }
            }
            if (bestController != null && Double.isFinite(bestCost)) {
                network.assignments.put(bestController, threatUuid);
                assignedControllers.add(bestController);
            }
        }
    }

    private static double threatUrgency(ServerLevel level, UUID threatUuid, Threat threat) {
        net.minecraft.world.entity.Entity entity = level.getEntity(threatUuid);
        if (entity == null || !entity.isAlive()) {
            return Double.MAX_VALUE;
        }
        double elapsedTicks = Math.max(0L, level.getGameTime() - threat.referenceGameTime);
        Vec3 referenceVelocity = threat.referenceVelocity.add(
                threat.referenceAcceleration.scale(elapsedTicks));
        double speed = Math.max(0.05, entity.getDeltaMovement().subtract(referenceVelocity).length());
        return entity.position().distanceTo(projectedReference(threat, level.getGameTime())) / speed;
    }

    private static double roughEngagementCost(
            ServerLevel level,
            ControllerSnapshot snapshot,
            UUID threatUuid
    ) {
        net.minecraft.world.entity.Entity entity = level.getEntity(threatUuid);
        if (entity == null || !entity.isAlive() || snapshot.maxStepDegreesPerTick <= 0.0001
                || snapshot.interceptorSpeedBlocksPerTick <= 0.0001) {
            return Double.MAX_VALUE;
        }
        Vec3 delta = entity.position().subtract(snapshot.muzzle);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float desiredYaw = normalize360((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) + 270.0));
        float desiredPitch = (float) Math.toDegrees(Math.atan2(delta.y, horizontal));
        double yawError = Math.abs(net.minecraft.util.Mth.wrapDegrees(desiredYaw - snapshot.currentYaw));
        double pitchError = Math.abs(net.minecraft.util.Mth.wrapDegrees(desiredPitch - snapshot.currentPitch));
        double turnTicks = Math.max(yawError, pitchError) / snapshot.maxStepDegreesPerTick;
        double flightTicks = delta.length() / snapshot.interceptorSpeedBlocksPerTick;
        return turnTicks + flightTicks * 0.05;
    }

    private static boolean assignmentEquivalent(ControllerSnapshot previous, ControllerSnapshot next) {
        if (previous.available != next.available) {
            return false;
        }
        if (previous.muzzle.distanceToSqr(next.muzzle) > 0.0625) {
            return false;
        }
        if (Math.abs(previous.maxStepDegreesPerTick - next.maxStepDegreesPerTick) > 0.001) {
            return false;
        }
        if (Math.abs(previous.interceptorSpeedBlocksPerTick - next.interceptorSpeedBlocksPerTick) > 0.001) {
            return false;
        }
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(previous.currentYaw - next.currentYaw)) < 5.0
                && Math.abs(net.minecraft.util.Mth.wrapDegrees(previous.currentPitch - next.currentPitch)) < 5.0;
    }

    private static float normalize360(float degrees) {
        float wrapped = net.minecraft.util.Mth.wrapDegrees(degrees);
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private static InterceptionControllerKey controllerKey(ServerLevel level, BlockPos controllerPos) {
        UUID structureUuid = SableRadarIntegration.containingStructureUuid(level, controllerPos).orElse(null);
        return controllerKey(level.dimension(), structureUuid, controllerPos);
    }

    static InterceptionControllerKey controllerKey(
            ResourceKey<Level> dimension,
            @Nullable UUID structureUuid,
            BlockPos controllerPos
    ) {
        return new InterceptionControllerKey(dimension, structureUuid, controllerPos);
    }

    private static ServerLevel authoritativeLevel(ServerLevel level) {
        ServerLevel worldLevel = level.getServer().getLevel(level.dimension());
        return worldLevel == null ? level : worldLevel;
    }

    private static Vec3 projectedReference(Threat threat, long gameTime) {
        double elapsedTicks = Math.max(0L, gameTime - threat.referenceGameTime);
        return threat.referencePosition
                .add(threat.referenceVelocity.scale(elapsedTicks))
                .add(threat.referenceAcceleration.scale(0.5D * elapsedTicks * elapsedTicks));
    }

    private static void cleanupAssignments(ServerState server, long gameTime) {
        server.interceptorTargets.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
    }

    private static ServerState server(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static NetworkState network(MinecraftServer server, UUID networkId) {
        return server(server).networks.computeIfAbsent(networkId, ignored -> new NetworkState());
    }

    private static final class ServerState {
        private final Map<UUID, NetworkState> networks = new HashMap<>();
        private final Map<UUID, InterceptorAssignment> interceptorTargets = new HashMap<>();
        private final Map<UUID, DestructionChance> destructionChances = new HashMap<>();
    }

    private static final class NetworkState {
        private final Map<UUID, Threat> threats = new HashMap<>();
        private final Map<UUID, Reservation> reservations = new HashMap<>();
        private final Map<InterceptionControllerKey, PendingLaunch> pendingLaunches = new HashMap<>();
        private final Map<InterceptionControllerKey, ControllerState> controllers = new HashMap<>();
        private final Map<InterceptionControllerKey, UUID> assignments = new HashMap<>();
        private final Map<ControllerThreat, Long> rejections = new HashMap<>();
        private long threatRevision;
    }

    private record Threat(
            ResourceKey<Level> dimension,
            Vec3 referencePosition,
            Vec3 referenceVelocity,
            Vec3 referenceAcceleration,
            long referenceGameTime,
            Vec3 position,
            Vec3 velocity,
            long lastSeenGameTime,
            double gravity,
            double drag,
            boolean quadraticDrag,
            @Nullable Vec3 upperCrossing,
            @Nullable Vec3 lowerCrossing,
            long expiresAt
    ) {
    }

    private record Reservation(InterceptionControllerKey controllerPos, double engagementTicks, long expiresAt) {
    }

    private record InterceptorAssignment(UUID networkId, UUID targetUuid, long expiresAt) {
    }

    private record DestructionChance(int failedAttempts, long lastAttemptAt) {
    }

    public record DestructionRoll(
            double probability,
            double roll,
            double nextProbability,
            boolean destroyed
    ) {
    }

    public record ThreatBid(
            UUID threatUuid,
            double priorityScore,
            double engagementTicks
    ) {
    }

    public record ControllerSnapshot(
            Vec3 muzzle,
            float currentYaw,
            float currentPitch,
            double maxStepDegreesPerTick,
            double interceptorSpeedBlocksPerTick,
            boolean available
    ) {
    }

    public record ThreatSnapshot(
            UUID threatUuid,
            ResourceKey<Level> dimension,
            Vec3 position,
            Vec3 velocity,
            long lastSeenGameTime,
            double gravity,
            double drag,
            boolean quadraticDrag,
            Vec3 referencePosition,
            Vec3 referenceVelocity,
            Vec3 referenceAcceleration,
            long referenceGameTime,
            @Nullable Vec3 upperCrossing,
            @Nullable Vec3 lowerCrossing
    ) {
        public Vec3 referencePositionAt(long gameTime) {
            double elapsedTicks = Math.max(0L, gameTime - referenceGameTime);
            return referencePosition
                    .add(referenceVelocity.scale(elapsedTicks))
                    .add(referenceAcceleration.scale(0.5D * elapsedTicks * elapsedTicks));
        }
    }

    private record ControllerState(ControllerSnapshot snapshot, long expiresAt) {
    }

    private record ControllerThreat(InterceptionControllerKey controllerPos, UUID threatUuid) {
    }

    private record PendingLaunch(
            Vec3 muzzlePos,
            UUID threatUuid,
            long expiresAt
    ) {
    }

    private record PendingCandidate(
            UUID networkId,
            PendingLaunch launch,
            double distanceSqr
    ) {
    }
}
