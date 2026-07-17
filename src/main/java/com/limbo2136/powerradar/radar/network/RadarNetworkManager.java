package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.block.entity.ComputingBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayBuilder;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.ShellAlarmDisplayZone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadarNetworkManager {
    private static final Map<MinecraftServer, RadarNetworkManager> MANAGERS = new WeakHashMap<>();
    private static final TicketType<UUID> RADAR_LINK_TICKET =
            TicketType.create("power_radar:radar_link", UUID::compareTo);
    private static final int RADAR_LINK_TICKET_DISTANCE = 2;

    private final MinecraftServer server;
    private final RadarNetworkSavedData savedData;
    private final Map<UUID, RadarNetworkRuntime> runtimeNetworks = new HashMap<>();
    private final Map<UUID, ComputingResolution> computingResolutionCache = new HashMap<>();
    private final Map<UUID, ComputingPolicy> computingPolicyCache = new HashMap<>();

    private RadarNetworkManager(MinecraftServer server) {
        this.server = server;
        this.savedData = RadarNetworkSavedData.get(server);
        for (RadarNetworkRecord record : this.savedData.records()) {
            RadarNetworkRuntime runtime = new RadarNetworkRuntime();
            runtime.loadPersistentSettings(record.selectedTargetUuid());
            this.runtimeNetworks.put(record.id(), runtime);
        }
    }

    public static RadarNetworkManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, RadarNetworkManager::new);
    }

    public UUID createNetwork() {
        UUID id = UUID.randomUUID();
        this.ensureNetwork(id);
        return id;
    }

    public UUID createOnboardNetwork() {
        UUID id = createNetwork();
        setControlConsumersAllowed(id, false);
        return id;
    }

    public boolean networkExists(UUID id) {
        return this.savedData.get(id).isPresent();
    }

    public RadarNetworkRecord ensureNetwork(UUID id) {
        RadarNetworkRecord record = this.savedData.ensure(id);
        this.runtimeNetworks.computeIfAbsent(id, ignored -> new RadarNetworkRuntime());
        return record;
    }

    public boolean controlConsumersAllowed(UUID id) {
        return this.savedData.get(id)
                .map(RadarNetworkRecord::controlConsumersAllowed)
                .orElse(true);
    }

    public void setControlConsumersAllowed(UUID id, boolean allowed) {
        RadarNetworkRecord record = ensureNetwork(id);
        if (record.controlConsumersAllowed() == allowed) {
            return;
        }
        record.setControlConsumersAllowed(allowed);
        if (!allowed) {
            record.setSelectedTargetUuid(null);
            runtime(id).setSelectedTargetUuid(null);
        }
        invalidateComputingCache(id);
        this.savedData.setDirty();
    }

    public void addPersistentLink(UUID id, GlobalPos linkPos) {
        RadarNetworkRecord record = this.ensureNetwork(id);
        if (record.linkNodes().add(linkPos)) {
            this.savedData.setDirty();
        }
    }

    public void removePersistentLink(UUID id, GlobalPos linkPos) {
        this.savedData.get(id).ifPresent(record -> {
            this.removeControllerBinding(id, linkPos);
            if (record.linkNodes().remove(linkPos)) {
                this.savedData.setDirty();
            }
            this.unloadLink(id, linkPos);
            this.cleanupEmptyNetwork(id);
        });
    }

    public void loadLink(UUID id, GlobalPos linkPos) {
        this.addPersistentLink(id, linkPos);
        this.runtime(id).loadedLinks().add(linkPos);
        invalidateComputingCache(id);
        this.reconcileConsumerLeases(id);
    }

    public void unloadLink(UUID id, GlobalPos linkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        runtime.loadedLinks().remove(linkPos);
        invalidateComputingCache(id);
        this.detachMonitorFromLink(id, linkPos);
        this.reconcileConsumerLeases(id);
    }

    public RadarLinkReconcileResult attachControllerFromLink(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        this.addPersistentLink(id, linkPos);
        this.upsertControllerBinding(id, linkPos, controllerPos);
        this.reconcileConsumerLeases(id);
        return RadarLinkReconcileResult.CONTROLLER_ATTACHED;
    }

    public void detachControllerFromLink(UUID id, GlobalPos linkPos) {
        this.removeControllerBinding(id, linkPos);
        this.reconcileConsumerLeases(id);
    }

    public void attachMonitorFromLink(UUID id, GlobalPos linkPos, GlobalPos monitorPos) {
        this.runtime(id).monitorLinkToMonitorPos().put(linkPos, monitorPos);
        this.reconcileConsumerLease(id, linkPos);
    }

    public void detachMonitorFromLink(UUID id, GlobalPos linkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        if (runtime.monitorLinkToMonitorPos().remove(linkPos) != null) {
            this.releaseConsumerLease(id, linkPos);
        }
    }

    public void reconcileMonitorConsumerLease(UUID id, GlobalPos linkPos) {
        this.reconcileConsumerLease(id, linkPos);
    }

    public void releaseMonitorConsumerLease(UUID id, GlobalPos linkPos) {
        this.releaseConsumerLease(id, linkPos);
    }

    public RadarNetworkStatus networkStatus(UUID id) {
        Optional<RadarNetworkRecord> record = this.savedData.get(id);
        if (record.isEmpty() || record.get().controllerBindings().isEmpty()) {
            return RadarNetworkStatus.NO_RADAR;
        }
        for (RadarControllerEndpointBinding binding : record.get().controllerBindings()) {
            if (this.runtime(id).loadedLinks().contains(binding.radarLinkPos())) {
                ServerLevel level = this.server.getLevel(binding.controllerPos().dimension());
                if (level != null
                        && level.isLoaded(binding.controllerPos().pos())
                        && level.getBlockEntity(binding.controllerPos().pos()) instanceof RadarControllerBlockEntity) {
                    return RadarNetworkStatus.ACTIVE;
                }
            }
        }
        return RadarNetworkStatus.CONTROLLER_OFFLINE;
    }

    public ControllerResolution resolveActiveControllerForConsumer(UUID id, GlobalPos consumerLinkPos) {
        ControllersResolution resolution = resolveControllersForConsumer(id, consumerLinkPos);
        return resolution.controllers().isEmpty()
                ? ControllerResolution.empty(resolution.status())
                : new ControllerResolution(resolution.status(), Optional.of(resolution.controllers().get(0)));
    }

    public ControllersResolution resolveControllersForConsumer(UUID id, GlobalPos consumerLinkPos) {
        Optional<RadarNetworkRecord> record = this.savedData.get(id);
        if (record.isEmpty() || record.get().controllerBindings().isEmpty()) {
            return ControllersResolution.empty(RadarNetworkConnectionStatus.NO_RADAR);
        }
        ArrayList<RadarControllerBlockEntity> controllers = new ArrayList<>();
        boolean dimensionBlocked = false;
        boolean outOfRange = false;
        boolean offline = false;
        for (RadarControllerEndpointBinding binding : record.get().controllerBindings()) {
            if (!binding.radarLinkPos().dimension().equals(consumerLinkPos.dimension())) {
                dimensionBlocked = true;
                continue;
            }
            if (!isWithinLinkRange(consumerLinkPos, binding.radarLinkPos())) {
                outOfRange = true;
                continue;
            }
            ServerLevel controllerLevel = this.server.getLevel(binding.controllerPos().dimension());
            if (controllerLevel == null || !controllerLevel.isLoaded(binding.controllerPos().pos())) {
                offline = true;
                continue;
            }
            BlockEntity blockEntity = controllerLevel.getBlockEntity(binding.controllerPos().pos());
            if (blockEntity instanceof RadarControllerBlockEntity controller) {
                controllers.add(controller);
            } else {
                offline = true;
            }
        }
        if (!controllers.isEmpty()) {
            return new ControllersResolution(RadarNetworkConnectionStatus.CONNECTED, List.copyOf(controllers));
        }
        if (offline) {
            return ControllersResolution.empty(RadarNetworkConnectionStatus.CONTROLLER_OFFLINE);
        }
        if (outOfRange) {
            return ControllersResolution.empty(RadarNetworkConnectionStatus.OUT_OF_RANGE);
        }
        if (dimensionBlocked) {
            return ControllersResolution.empty(RadarNetworkConnectionStatus.CROSS_DIMENSION_BLOCKED);
        }
        return ControllersResolution.empty(RadarNetworkConnectionStatus.NO_RADAR);
    }

    public Optional<RadarControllerBlockEntity> resolveActiveController(UUID id, ServerLevel monitorLevel) {
        Optional<RadarNetworkRecord> record = this.savedData.get(id);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        for (RadarControllerEndpointBinding binding : record.get().controllerBindings()) {
            if (!binding.controllerPos().dimension().equals(monitorLevel.dimension())) {
                continue;
            }
            if (monitorLevel.isLoaded(binding.controllerPos().pos())
                    && monitorLevel.getBlockEntity(binding.controllerPos().pos()) instanceof RadarControllerBlockEntity controller) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> selectedTargetUuid(UUID id) {
        return this.runtime(id).selectedTargetUuid();
    }

    public void setSelectedTargetUuid(UUID id, UUID targetUuid) {
        if (targetUuid != null && !controlConsumersAllowed(id)) {
            return;
        }
        RadarNetworkRecord record = this.ensureNetwork(id);
        if (!Objects.equals(record.selectedTargetUuid(), targetUuid)) {
            record.setSelectedTargetUuid(targetUuid);
            this.savedData.setDirty();
        }
        this.runtime(id).setSelectedTargetUuid(targetUuid);
    }

    public int autotargetFilterMask(UUID id) {
        return cachedComputingPolicy(id).targetingMask();
    }

    public int displayFilterMask(UUID id) {
        return cachedComputingPolicy(id).displayMask();
    }

    public int displayFilterMaskForController(GlobalPos controllerPos) {
        int mask = RadarDetectionFilters.DEFAULT_MASK;
        boolean bound = false;
        for (RadarNetworkRecord record : this.savedData.records()) {
            boolean matches = record.controllerBindings().stream()
                    .anyMatch(binding -> binding.controllerPos().equals(controllerPos));
            if (matches) {
                bound = true;
                mask &= displayFilterMask(record.id());
            }
        }
        return bound ? mask : RadarDetectionFilters.DEFAULT_MASK;
    }

    public long settingsRevision(UUID id) {
        return this.runtime(id).settingsRevision();
    }

    public RadarMonitorDisplayData displayDataForConsumer(
            UUID id,
            GlobalPos consumerLinkPos,
            BlockPos monitorPos,
            Direction monitorFacing,
            List<RadarControllerBlockEntity> controllers,
            long serverGameTime,
            PowerRadarCeeState monitorElectricalState,
            double monitorVoltageVolts,
            double monitorResistanceOhms,
            int monitorDisplayCount,
            int monitorScreenSize,
            boolean monitorRendererEnabled,
            int onlinePlayersHash,
            List<String> onlinePlayerNames
    ) {
        RadarNetworkRuntime runtime = this.runtime(id);
        ComputingPolicy policy = cachedComputingPolicy(id);
        int displayMask = policy.displayMask();
        int targetingMask = policy.targetingMask();
        List<String> allowlistedPlayers = policy.allowlistedPlayers();
        List<String> allowlistedSables = policy.allowlistedSables();
        long revision = displaySnapshotRevision(runtime, controllers, onlinePlayersHash);
        RadarNetworkRuntime.DisplaySnapshotCacheEntry cached = runtime.displaySnapshot();
        RadarMonitorDisplayData baseData;
        if (!policy.present() && cached != null && cached.revision() == revision) {
            baseData = cached.data();
        } else {
            baseData = RadarMonitorDisplayBuilder.fromControllers(
                    BlockPos.ZERO,
                    Direction.NORTH,
                    controllers,
                    serverGameTime,
                    PowerRadarCeeState.POWERED,
                    0.0,
                    0.0,
                    0,
                    0,
                    true,
                    targetingMask,
                    runtime.selectedTargetUuid().orElse(null),
                    onlinePlayerNames,
                    allowlistedPlayers,
                    allowlistedSables);
            baseData = baseData.withTargets(baseData.targets().stream()
                    .filter(target -> RadarDetectionFilters.enabled(displayMask, target.category()))
                    .toList());
            if (!policy.present()) {
                runtime.putDisplaySnapshot(revision, baseData);
            }
        }
        baseData = baseData.withShellAlarmZones(shellAlarmZones(runtime));
        return baseData.withMonitorContext(
                monitorPos,
                monitorFacing,
                monitorElectricalState,
                monitorVoltageVolts,
                monitorResistanceOhms,
                monitorDisplayCount,
                monitorScreenSize,
                monitorRendererEnabled);
    }

    private List<ShellAlarmDisplayZone> shellAlarmZones(RadarNetworkRuntime runtime) {
        ArrayList<ShellAlarmDisplayZone> zones = new ArrayList<>();
        for (GlobalPos linkPos : runtime.loadedLinks()) {
            ServerLevel level = this.server.getLevel(linkPos.dimension());
            if (level == null
                    || !(level.getBlockEntity(linkPos.pos()) instanceof ShellAlarmBlockEntity alarm)) {
                continue;
            }
            BlockPos pos = linkPos.pos();
            zones.add(new ShellAlarmDisplayZone(
                    linkPos.dimension().location(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    alarm.protectionWidthBlocks(),
                    alarm.protectionHeightBlocks(),
                    alarm.protectionDepthBlocks()));
        }
        return List.copyOf(zones);
    }

    private static long displaySnapshotRevision(
            RadarNetworkRuntime runtime,
            List<RadarControllerBlockEntity> controllers,
            int onlinePlayersHash
    ) {
        long revision = 17L;
        revision = 31L * revision + runtime.settingsRevision();
        revision = 31L * revision + onlinePlayersHash;
        for (RadarControllerBlockEntity controller : controllers) {
            if (controller == null || controller.isRemoved()) {
                continue;
            }
            revision = 31L * revision + controller.getBlockPos().asLong();
            revision = 31L * revision + controller.displayRevision();
            revision = 31L * revision + controller.lastScanGameTime();
        }
        return revision;
    }

    public List<String> whitelistedPlayerNames(UUID id) {
        return cachedComputingPolicy(id).allowlistedPlayers();
    }

    public boolean hasForcedAutotargetEntries(UUID id) {
        ComputingPolicy policy = cachedComputingPolicy(id);
        return policy.powered() && !policy.allowlistIsWhitelist()
                && (!policy.playerNames().isEmpty() || !policy.sableUuids().isEmpty()
                || !policy.unresolvedSableNames().isEmpty());
    }

    public boolean isAutotargetExcluded(UUID id, UUID targetUuid, String name, boolean sable) {
        ComputingPolicy policy = cachedComputingPolicy(id);
        return policy.powered() && policy.allowlistIsWhitelist()
                && matchesAllowlist(policy, targetUuid, name, sable);
    }

    public boolean isAutotargetForced(UUID id, UUID targetUuid, String name, boolean sable) {
        ComputingPolicy policy = cachedComputingPolicy(id);
        return policy.powered() && !policy.allowlistIsWhitelist()
                && matchesAllowlist(policy, targetUuid, name, sable);
    }

    private static boolean matchesAllowlist(ComputingPolicy policy, UUID targetUuid, String name, boolean sable) {
        if (!sable) return policy.playerNames().stream().anyMatch(name::equalsIgnoreCase);
        return targetUuid != null && policy.sableUuids().contains(targetUuid)
                || policy.unresolvedSableNames().stream().anyMatch(name::equalsIgnoreCase);
    }

    private ComputingResolution cachedComputingResolution(UUID id) {
        return this.computingResolutionCache.computeIfAbsent(id, this::resolveComputingBlock);
    }

    private ComputingPolicy cachedComputingPolicy(UUID id) {
        return this.computingPolicyCache.computeIfAbsent(id, ignored -> {
            ComputingResolution resolution = cachedComputingResolution(id);
            ComputingBlockEntity computer = resolution.active();
            if (computer == null || !computer.isElectricallyOperational()) return ComputingPolicy.EMPTY;
            return new ComputingPolicy(true, true, computer.targetingMask(), computer.displayMask(),
                    computer.allowlistIsWhitelist(), computer.allowlistPlayerNames(),
                    computer.allowlistSableUuids(), computer.unresolvedSableNames(),
                    computer.allowlistedPlayers(), computer.allowlistedSableNames());
        });
    }

    public void invalidateComputingCache(UUID id) {
        if (id == null) return;
        this.computingResolutionCache.remove(id);
        this.computingPolicyCache.remove(id);
        this.runtime(id).markSettingsChanged();
    }

    public ComputingResolution resolveComputingBlock(UUID id) {
        if (!controlConsumersAllowed(id)) {
            return new ComputingResolution(null, false);
        }
        List<ComputingBlockEntity> computers = new ArrayList<>();
        this.runtime(id).loadedLinks().stream()
                .sorted(java.util.Comparator.comparing((GlobalPos pos) -> pos.dimension().location().toString())
                        .thenComparingLong(pos -> pos.pos().asLong()))
                .forEach(linkPos -> {
                    ServerLevel level = this.server.getLevel(linkPos.dimension());
                    if (level == null || !level.isLoaded(linkPos.pos())
                            || !(level.getBlockEntity(linkPos.pos()) instanceof RadarLinkBlockEntity link)
                            || link.endpointRole() != RadarLinkEndpointRole.COMPUTING_BLOCK
                            || link.endpointPos() == null
                            || !link.endpointPos().dimension().equals(linkPos.dimension())
                            || !level.isLoaded(link.endpointPos().pos())
                            || !(level.getBlockEntity(link.endpointPos().pos()) instanceof ComputingBlockEntity computer)) {
                        return;
                    }
                    if (!computers.contains(computer)) {
                        computers.add(computer);
                    }
                });
        return new ComputingResolution(computers.size() == 1 ? computers.get(0) : null, computers.size() > 1);
    }

    public record ComputingResolution(ComputingBlockEntity active, boolean conflict) {
    }

    private record ComputingPolicy(boolean present, boolean powered, int targetingMask, int displayMask,
                                   boolean allowlistIsWhitelist, List<String> playerNames,
                                   List<UUID> sableUuids, List<String> unresolvedSableNames,
                                   List<String> allowlistedPlayers, List<String> allowlistedSables) {
        private static final ComputingPolicy EMPTY = new ComputingPolicy(false, false, 0,
                RadarDetectionFilters.DEFAULT_MASK, true, List.of(), List.of(), List.of(), List.of(), List.of());

        private ComputingPolicy {
            playerNames = List.copyOf(playerNames);
            sableUuids = List.copyOf(sableUuids);
            unresolvedSableNames = List.copyOf(unresolvedSableNames);
            allowlistedPlayers = List.copyOf(allowlistedPlayers);
            allowlistedSables = List.copyOf(allowlistedSables);
        }
    }

    public void cleanupEmptyNetwork(UUID id) {
        if (this.savedData.get(id).filter(record -> record.linkNodes().isEmpty()).isEmpty()) {
            return;
        }
        this.removeAllTickets(id);
        if (!this.savedData.removeIfNoLinks(id)) {
            return;
        }
        this.runtimeNetworks.remove(id);
        this.computingResolutionCache.remove(id);
        this.computingPolicyCache.remove(id);
    }

    private boolean upsertControllerBinding(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        RadarNetworkRecord record = this.ensureNetwork(id);
        for (RadarNetworkRecord other : this.savedData.records()) {
            if (!other.id().equals(id) && other.controllerBindings().removeIf(binding -> binding.controllerPos().equals(controllerPos))) {
                invalidateComputingCache(other.id());
                this.savedData.setDirty();
            }
        }
        RadarControllerEndpointBinding newBinding = new RadarControllerEndpointBinding(linkPos, controllerPos);
        Optional<RadarControllerEndpointBinding> previous = record.controllerBindings().stream()
                .filter(binding -> binding.radarLinkPos().equals(linkPos))
                .findFirst();
        if (previous.filter(newBinding::equals).isPresent()) {
            return false;
        }
        previous.ifPresent(record.controllerBindings()::remove);
        record.controllerBindings().add(newBinding);
        this.savedData.setDirty();
        this.runtime(id).invalidateDisplaySnapshots();
        return true;
    }

    private boolean removeControllerBinding(UUID id, GlobalPos linkPos) {
        Optional<RadarNetworkRecord> record = this.savedData.get(id);
        if (record.isEmpty()) {
            return false;
        }
        boolean removed = record.get().controllerBindings().removeIf(binding -> binding.radarLinkPos().equals(linkPos));
        if (removed) {
            this.savedData.setDirty();
            this.runtime(id).invalidateDisplaySnapshots();
        }
        return removed;
    }

    private void reconcileConsumerLeases(UUID id) {
        RadarNetworkRuntime runtime = this.runtime(id);
        List<LeaseDecision> releases = new ArrayList<>();
        for (GlobalPos linkPos : runtime.monitorLinkToMonitorPos().keySet()) {
            LeaseDecision decision = this.evaluateConsumerLease(id, linkPos);
            if (decision.action() == LeaseAction.RELEASE) {
                releases.add(decision);
                continue;
            }
            this.applyConsumerLeaseDecision(id, decision);
        }
        for (LeaseDecision decision : releases) {
            this.applyConsumerLeaseDecision(id, decision);
        }
        if (runtime.monitorLinkToMonitorPos().isEmpty()) {
            this.removeAllTickets(id);
        }
    }

    private void reconcileConsumerLease(UUID id, GlobalPos consumerLinkPos) {
        this.applyConsumerLeaseDecision(id, this.evaluateConsumerLease(id, consumerLinkPos));
    }

    private LeaseDecision evaluateConsumerLease(UUID id, GlobalPos consumerLinkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        Optional<RadarNetworkRecord> record = this.savedData.get(id);
        if (!RadarConstants.radarLinkForceLoadEnabled()) {
            return LeaseDecision.release(id, consumerLinkPos, "force-load-disabled");
        }
        if (record.isEmpty()) {
            return LeaseDecision.release(id, consumerLinkPos, "network-missing");
        }
        GlobalPos monitorPos = runtime.monitorLinkToMonitorPos().get(consumerLinkPos);
        if (monitorPos == null) {
            return LeaseDecision.release(id, consumerLinkPos, "monitor-link-missing");
        }
        if (!runtime.loadedLinks().contains(consumerLinkPos)) {
            return LeaseDecision.release(id, consumerLinkPos, "consumer-link-unloaded");
        }
        if (!isMonitorLeaseEligible(monitorPos)) {
            return LeaseDecision.release(id, consumerLinkPos, "monitor-ineligible");
        }
        RadarControllerEndpointBinding eligibleBinding = null;
        for (RadarControllerEndpointBinding binding : record.get().controllerBindings()) {
            if (!binding.radarLinkPos().dimension().equals(consumerLinkPos.dimension())) {
                continue;
            }
            if (!isWithinLinkRange(consumerLinkPos, binding.radarLinkPos())) {
                continue;
            }
            eligibleBinding = binding;
            break;
        }
        if (eligibleBinding == null) {
            return LeaseDecision.release(id, consumerLinkPos, "no-eligible-radar");
        }
        ServerLevel radarLevel = this.server.getLevel(eligibleBinding.radarLinkPos().dimension());
        if (radarLevel != null && RadarWorldPoseResolver.isOnSableStructure(
                radarLevel, eligibleBinding.radarLinkPos().pos())) {
            return LeaseDecision.release(id, consumerLinkPos, "sable-sublevel-manages-chunks");
        }
        return LeaseDecision.keep(id, consumerLinkPos, eligibleBinding.radarLinkPos());
    }

    private void applyConsumerLeaseDecision(UUID id, LeaseDecision decision) {
        if (decision.action() == LeaseAction.RELEASE) {
            logLeaseDecision(decision);
            this.releaseConsumerLease(id, decision.consumerLinkPos());
            return;
        }
        RadarNetworkRuntime runtime = this.runtime(id);
        runtime.chunkLoadState().activeConsumerLeaseLinks().add(decision.consumerLinkPos());
        logLeaseDecision(decision);
        this.applyTicketsIfNeeded(id, decision.radarLinkPos());
    }

    private static void logLeaseDecision(LeaseDecision decision) {
        if (!PowerRadarDebugOptions.radarLinkLeaseLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][RadarLink] lease network={} consumerLink={} action={} reason={} radarLink={}",
                decision.networkId(),
                decision.consumerLinkPos(),
                decision.action(),
                decision.reason(),
                decision.radarLinkPos()
        );
    }

    private boolean isMonitorLeaseEligible(GlobalPos monitorPos) {
        if (monitorPos == null) {
            return false;
        }
        ServerLevel level = this.server.getLevel(monitorPos.dimension());
        if (level == null || !level.isLoaded(monitorPos.pos())) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(monitorPos.pos());
        return blockEntity instanceof RadarMonitorControllerBlockEntity monitor
                && monitor.canHoldConsumerLease();
    }

    private void releaseConsumerLease(UUID id, GlobalPos consumerLinkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        runtime.chunkLoadState().activeConsumerLeaseLinks().remove(consumerLinkPos);
        if (runtime.chunkLoadState().activeConsumerLeaseLinks().isEmpty()) {
            this.removeAllTickets(id);
        }
    }

    private void applyTicketsIfNeeded(UUID id, GlobalPos radarLinkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        RadarNetworkChunkLoadState state = runtime.chunkLoadState();
        ServerLevel level = this.server.getLevel(radarLinkPos.dimension());
        if (level != null && RadarWorldPoseResolver.isOnSableStructure(level, radarLinkPos.pos())) {
            this.removeAllTickets(id);
            return;
        }
        if (state.radarLinkPos().filter(radarLinkPos::equals).isPresent() && state.ticketsApplied()) {
            return;
        }
        this.removeAllTickets(id);
        if (level == null || runtime.chunkLoadState().activeConsumerLeaseLinks().isEmpty()) {
            return;
        }
        for (ChunkPos chunkPos : chunkRegion(radarLinkPos)) {
            if (state.appliedChunks().add(chunkPos)) {
                level.getChunkSource().addRegionTicket(RADAR_LINK_TICKET, chunkPos, RADAR_LINK_TICKET_DISTANCE, id, true);
            }
        }
        state.setRadarLinkPos(radarLinkPos);
    }

    private void removeAllTickets(UUID id) {
        RadarNetworkRuntime runtime = this.runtime(id);
        RadarNetworkChunkLoadState state = runtime.chunkLoadState();
        Optional<GlobalPos> radarLinkPos = state.radarLinkPos();
        if (radarLinkPos.isEmpty() || !state.ticketsApplied()) {
            return;
        }
        ServerLevel level = this.server.getLevel(radarLinkPos.get().dimension());
        if (level != null) {
            for (ChunkPos chunkPos : state.appliedChunks()) {
                level.getChunkSource().removeRegionTicket(RADAR_LINK_TICKET, chunkPos, RADAR_LINK_TICKET_DISTANCE, id, true);
            }
        }
        state.clearAppliedTickets();
    }

    private static Set<ChunkPos> chunkRegion(GlobalPos radarLinkPos) {
        ChunkPos center = new ChunkPos(radarLinkPos.pos());
        Set<ChunkPos> chunks = new HashSet<>();
        int radius = RadarConstants.radarLinkForceLoadRadiusChunks();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        return chunks;
    }

    private boolean isWithinLinkRange(GlobalPos consumerLinkPos, GlobalPos radarLinkPos) {
        if (!consumerLinkPos.dimension().equals(radarLinkPos.dimension())) {
            return false;
        }
        ServerLevel level = this.server.getLevel(consumerLinkPos.dimension());
        if (level == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 consumerWorldPos = RadarWorldPoseResolver.worldPosition(
                level, consumerLinkPos.pos());
        net.minecraft.world.phys.Vec3 radarWorldPos = RadarWorldPoseResolver.worldPosition(
                level, radarLinkPos.pos());
        long max = RadarConstants.radarLinkMaxConnectionDistanceBlocks();
        return consumerWorldPos.distanceToSqr(radarWorldPos) <= (double) max * max;
    }

    private RadarNetworkRuntime runtime(UUID id) {
        return this.runtimeNetworks.computeIfAbsent(id, ignored -> {
            RadarNetworkRuntime runtime = new RadarNetworkRuntime();
            this.savedData.get(id).ifPresent(record -> runtime.loadPersistentSettings(record.selectedTargetUuid()));
            return runtime;
        });
    }

    public record ControllerResolution(
            RadarNetworkConnectionStatus status,
            Optional<RadarControllerBlockEntity> controller
    ) {
        public static ControllerResolution empty(RadarNetworkConnectionStatus status) {
            return new ControllerResolution(status, Optional.empty());
        }
    }

    public record ControllersResolution(
            RadarNetworkConnectionStatus status,
            List<RadarControllerBlockEntity> controllers
    ) {
        public static ControllersResolution empty(RadarNetworkConnectionStatus status) {
            return new ControllersResolution(status, List.of());
        }
    }

    private enum LeaseAction {
        KEEP,
        RELEASE
    }

    private record LeaseDecision(
            UUID networkId,
            GlobalPos consumerLinkPos,
            LeaseAction action,
            String reason,
            GlobalPos radarLinkPos
    ) {
        private static LeaseDecision keep(UUID networkId, GlobalPos consumerLinkPos, GlobalPos radarLinkPos) {
            return new LeaseDecision(networkId, consumerLinkPos, LeaseAction.KEEP, "eligible", radarLinkPos);
        }

        private static LeaseDecision release(UUID networkId, GlobalPos consumerLinkPos, String reason) {
            return new LeaseDecision(networkId, consumerLinkPos, LeaseAction.RELEASE, reason, null);
        }
    }
}
