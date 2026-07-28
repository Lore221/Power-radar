package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.logic.LogicDockPolicySource;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayBuilder;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.SableStructureName;
import com.limbo2136.powerradar.radar.ShellAlarmDisplayZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Единственная точка изменения постоянной топологии и её загруженного runtime-представления.
public class RadarNetworkManager {
    private static final Map<MinecraftServer, RadarNetworkManager> MANAGERS = new WeakHashMap<>();
    private static final TicketType<UUID> RADAR_LINK_TICKET =
            TicketType.create("power_radar:radar_link", UUID::compareTo);
    private static final int RADAR_LINK_TICKET_DISTANCE = 2;
    private static final long PANEL_LOGIC_DOCK_LEASE_TIMEOUT_TICKS = 40L;

    private final MinecraftServer server;
    private final RadarNetworkSavedData savedData;
    private final Map<UUID, RadarNetworkRuntime> runtimeNetworks = new HashMap<>();
    private final Map<UUID, LogicDockResolution> logicDockResolutionCache = new HashMap<>();
    private final Map<UUID, LogicDockPolicy> logicDockPolicyCache = new HashMap<>();
    private final Map<UUID, Map<PanelLogicDockKey, PanelLogicDockRegistration>> panelLogicDocks =
            new HashMap<>();

    private RadarNetworkManager(MinecraftServer server) {
        this.server = server;
        this.savedData = RadarNetworkSavedData.get(server);
        // Из сохранения восстанавливаются только постоянные настройки; ссылки, кэши и tickets
        // заново формируются событиями загрузки мира.
        for (RadarNetworkRecord record : this.savedData.records()) {
            RadarNetworkRuntime runtime = new RadarNetworkRuntime();
            runtime.loadPersistentSettings(record.selectedTargetUuid());
            this.runtimeNetworks.put(record.id(), runtime);
        }
    }

    public static RadarNetworkManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, RadarNetworkManager::new);
    }

    public static void tickServer(MinecraftServer server) {
        RadarNetworkManager manager = MANAGERS.get(server);
        if (manager != null) {
            manager.prunePanelLogicDocks(server.overworld().getGameTime());
        }
    }

    public static void stopServer(MinecraftServer server) {
        MANAGERS.remove(server);
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

    /**
     * Возвращает устойчивый порядок создания сети в SavedData.
     * LinkedHashMap сохраняет этот порядок между сохранением и загрузкой мира.
     */
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
        invalidateLogicDockCache(id);
        this.savedData.setDirty();
    }

    // Постоянное членство переживает выгрузку чанка; loadedLinks отражает только текущий runtime.
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
        invalidateLogicDockCache(id);
        this.reconcileConsumerLeases(id);
    }

    public void unloadLink(UUID id, GlobalPos linkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        runtime.loadedLinks().remove(linkPos);
        invalidateLogicDockCache(id);
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

    // Порядок bindings задаёт детерминированный порядок агрегации и выбор основного радара.
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
        return cachedLogicDockPolicy(id).targetingMask();
    }

    public int displayFilterMask(UUID id) {
        return cachedLogicDockPolicy(id).displayMask();
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

    // Собирает общий серверный DTO один раз, затем добавляет контекст конкретного монитора.
    // Базовый снимок переиспользуется только при политике по умолчанию: карточная политика требует
    // полного набора проверенных invalidation-событий перед расширением этого кэша.
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
        LogicDockPolicy policy = cachedLogicDockPolicy(id);
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
                    || !(level.getBlockEntity(linkPos.pos()) instanceof ShellAlarmBlockEntity alarm)
                    || alarm.electricalState() != PowerRadarCeeState.POWERED) {
                continue;
            }
            // Наземный и Sable-режимы публикуют прямоугольный X/Z-след фактической защищаемой зоны.
            AABB bounds = alarm.displayProtectionBounds(level);
            if (bounds == null) {
                continue;
            }
            Vec3 center = bounds.getCenter();
            zones.add(new ShellAlarmDisplayZone(
                    linkPos.dimension().location(), center.x, center.y, center.z,
                    (int) Math.ceil(bounds.getXsize()),
                    (int) Math.ceil(bounds.getYsize()),
                    (int) Math.ceil(bounds.getZsize())));
        }
        return List.copyOf(zones);
    }

    // Ревизия локальна для runtime-кэша и не является частью wire- или save-контракта.
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
        return cachedLogicDockPolicy(id).allowlistedPlayers();
    }

    public boolean hasForcedAutotargetEntries(UUID id) {
        LogicDockPolicy policy = cachedLogicDockPolicy(id);
        return policy.powered() && !policy.allowlistIsWhitelist()
                && (!policy.playerNames().isEmpty() || !policy.sableNames().isEmpty());
    }

    // targetUuid сохранён в публичном контракте для вызывающего кода; текущие карточки доступа
    // сопоставляют игроков и Sable только по регистронезависимому отображаемому имени.
    public boolean isAutotargetExcluded(UUID id, UUID targetUuid, String name, boolean sable) {
        LogicDockPolicy policy = cachedLogicDockPolicy(id);
        return policy.powered() && policy.allowlistIsWhitelist()
                && matchesAllowlist(policy, name, sable);
    }

    public boolean isAutotargetForced(UUID id, UUID targetUuid, String name, boolean sable) {
        LogicDockPolicy policy = cachedLogicDockPolicy(id);
        return policy.powered() && !policy.allowlistIsWhitelist()
                && matchesAllowlist(policy, name, sable);
    }

    private static boolean matchesAllowlist(LogicDockPolicy policy, String name, boolean sable) {
        if (!sable) {
            return policy.playerNames().stream().anyMatch(name::equalsIgnoreCase);
        }
        String structureName = SableStructureName.normalize(name);
        return structureName != null
                && policy.sableNames().stream().anyMatch(structureName::equalsIgnoreCase);
    }

    private LogicDockResolution cachedLogicDockResolution(UUID id) {
        return this.logicDockResolutionCache.computeIfAbsent(id, this::resolveLogicDock);
    }

    private LogicDockPolicy cachedLogicDockPolicy(UUID id) {
        return this.logicDockPolicyCache.computeIfAbsent(id, ignored -> {
            LogicDockResolution resolution = cachedLogicDockResolution(id);
            LogicDockPolicySource dock = resolution.active();
            if (dock == null || !dock.isElectricallyOperational()) {
                return LogicDockPolicy.EMPTY;
            }
            return new LogicDockPolicy(true, true, dock.targetingMask(), dock.displayMask(),
                    dock.allowlistIsWhitelist(), dock.allowlistPlayerNames(),
                    dock.allowlistSableNames(),
                    dock.allowlistedPlayers(), dock.allowlistedSableNames());
        });
    }

    public void invalidateLogicDockCache(UUID id) {
        if (id == null) {
            return;
        }
        this.logicDockResolutionCache.remove(id);
        this.logicDockPolicyCache.remove(id);
        this.runtime(id).markSettingsChanged();
    }

    /** Панельный Logic Dock обновляет короткую runtime-lease каждый электрический тик. */
    public void touchPanelLogicDock(
            UUID id,
            GlobalPos panelPos,
            int slotIndex,
            LogicDockPolicySource source,
            long gameTime
    ) {
        if (!networkExists(id)) {
            return;
        }
        PanelLogicDockKey key = new PanelLogicDockKey(panelPos, slotIndex);
        Map<PanelLogicDockKey, PanelLogicDockRegistration> registrations =
                this.panelLogicDocks.computeIfAbsent(id, ignored -> new HashMap<>());
        PanelLogicDockRegistration previous = registrations.get(key);
        if (previous != null && previous.source == source) {
            previous.lastSeenGameTime = gameTime;
            return;
        }
        registrations.put(key, new PanelLogicDockRegistration(source, gameTime));
        invalidateLogicDockCache(id);
    }

    public void releasePanelLogicDock(
            UUID id,
            GlobalPos panelPos,
            int slotIndex,
            LogicDockPolicySource source
    ) {
        Map<PanelLogicDockKey, PanelLogicDockRegistration> registrations = this.panelLogicDocks.get(id);
        if (registrations == null) {
            return;
        }
        PanelLogicDockKey key = new PanelLogicDockKey(panelPos, slotIndex);
        PanelLogicDockRegistration registration = registrations.get(key);
        if (registration == null || registration.source != source) {
            return;
        }
        registrations.remove(key);
        if (registrations.isEmpty()) {
            this.panelLogicDocks.remove(id);
        }
        invalidateLogicDockCache(id);
    }

    // Ровно один загруженный и корректно подключённый источник становится авторитетным.
    // Обычные блоки обнаруживаются через Link, панельные — через короткие runtime-leases.
    public LogicDockResolution resolveLogicDock(UUID id) {
        if (!controlConsumersAllowed(id)) {
            return new LogicDockResolution(null, false);
        }
        List<LogicDockPolicySource> docks = new ArrayList<>();
        this.runtime(id).loadedLinks().stream()
                .sorted(Comparator.comparing((GlobalPos pos) -> pos.dimension().location().toString())
                        .thenComparingLong(pos -> pos.pos().asLong()))
                .forEach(linkPos -> {
                    ServerLevel level = this.server.getLevel(linkPos.dimension());
                    if (level == null || !level.isLoaded(linkPos.pos())
                            || !(level.getBlockEntity(linkPos.pos()) instanceof RadarLinkBlockEntity link)
                            || link.endpointRole() != RadarLinkEndpointRole.LOGIC_DOCK
                            || link.endpointPos() == null
                            || !link.endpointPos().dimension().equals(linkPos.dimension())
                            || !level.isLoaded(link.endpointPos().pos())
                            || !(level.getBlockEntity(link.endpointPos().pos()) instanceof LogicDockBlockEntity dock)) {
                        return;
                    }
                    if (!docks.contains(dock)) {
                        docks.add(dock);
                    }
                });
        Map<PanelLogicDockKey, PanelLogicDockRegistration> panelRegistrations =
                this.panelLogicDocks.get(id);
        if (panelRegistrations != null) {
            panelRegistrations.values().stream()
                    .map(registration -> registration.source)
                    .filter(source -> source.isAvailableForNetwork(id))
                    .filter(source -> !docks.contains(source))
                    .forEach(docks::add);
        }
        return new LogicDockResolution(docks.size() == 1 ? docks.get(0) : null, docks.size() > 1);
    }

    public record LogicDockResolution(LogicDockPolicySource active, boolean conflict) {
    }

    private record LogicDockPolicy(boolean present, boolean powered, int targetingMask, int displayMask,
                                   boolean allowlistIsWhitelist, List<String> playerNames,
                                   List<String> sableNames,
                                   List<String> allowlistedPlayers, List<String> allowlistedSables) {
        private static final LogicDockPolicy EMPTY = new LogicDockPolicy(false, false, 0,
                RadarDetectionFilters.DEFAULT_MASK, true, List.of(), List.of(), List.of(), List.of());

        private LogicDockPolicy {
            playerNames = List.copyOf(playerNames);
            sableNames = List.copyOf(sableNames);
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
        this.logicDockResolutionCache.remove(id);
        this.logicDockPolicyCache.remove(id);
        this.panelLogicDocks.remove(id);
    }

    // Контроллер принадлежит максимум одной сети: новая привязка удаляет устаревшие записи остальных.
    private boolean upsertControllerBinding(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        RadarNetworkRecord record = this.ensureNetwork(id);
        for (RadarNetworkRecord other : this.savedData.records()) {
            if (!other.id().equals(id) && other.controllerBindings().removeIf(binding -> binding.controllerPos().equals(controllerPos))) {
                invalidateLogicDockCache(other.id());
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

    // Сначала сохраняем пригодные leases, затем применяем releases: это не даёт промежуточной
    // очистке tickets зависеть от порядка обхода нескольких мониторов.
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

    // В runtime хранится один ticket-anchor на сеть; смена выбранного radar link атомарно заменяет регион.
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
        // Сохранённые позиции локальны для мира/Sable, но дальность Link измеряется в мировом пространстве.
        Vec3 consumerWorldPos = RadarWorldPoseResolver.worldPosition(
                level, consumerLinkPos.pos());
        Vec3 radarWorldPos = RadarWorldPoseResolver.worldPosition(
                level, radarLinkPos.pos());
        long max = RadarConstants.radarLinkMaxConnectionDistanceBlocks();
        return consumerWorldPos.distanceToSqr(radarWorldPos) <= (double) max * max;
    }

    // Выгруженный щиток перестаёт подтверждать lease и удаляется без ссылки на события выгрузки CEE.
    private void prunePanelLogicDocks(long gameTime) {
        ArrayList<UUID> changedNetworks = new ArrayList<>();
        var networkIterator = this.panelLogicDocks.entrySet().iterator();
        while (networkIterator.hasNext()) {
            Map.Entry<UUID, Map<PanelLogicDockKey, PanelLogicDockRegistration>> network =
                    networkIterator.next();
            boolean removed = network.getValue().entrySet().removeIf(entry -> {
                PanelLogicDockRegistration registration = entry.getValue();
                return gameTime - registration.lastSeenGameTime > PANEL_LOGIC_DOCK_LEASE_TIMEOUT_TICKS
                        || !registration.source.isAvailableForNetwork(network.getKey());
            });
            if (removed) {
                changedNetworks.add(network.getKey());
            }
            if (network.getValue().isEmpty()) {
                networkIterator.remove();
            }
        }
        changedNetworks.forEach(this::invalidateLogicDockCache);
    }

    private RadarNetworkRuntime runtime(UUID id) {
        return this.runtimeNetworks.computeIfAbsent(id, ignored -> {
            RadarNetworkRuntime runtime = new RadarNetworkRuntime();
            this.savedData.get(id).ifPresent(record -> runtime.loadPersistentSettings(record.selectedTargetUuid()));
            return runtime;
        });
    }

    private record PanelLogicDockKey(GlobalPos panelPos, int slotIndex) {
    }

    private static final class PanelLogicDockRegistration {
        private final LogicDockPolicySource source;
        private long lastSeenGameTime;

        private PanelLogicDockRegistration(LogicDockPolicySource source, long lastSeenGameTime) {
            this.source = source;
            this.lastSeenGameTime = lastSeenGameTime;
        }
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
