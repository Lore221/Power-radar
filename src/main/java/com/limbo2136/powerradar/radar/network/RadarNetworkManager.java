package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.AbstractRadarMonitorBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.logic.LogicDockPolicySource;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarId;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Единственная точка изменения постоянной топологии и её загруженного runtime-представления.
public class RadarNetworkManager {
    private static final Map<MinecraftServer, RadarNetworkManager> MANAGERS = new WeakHashMap<>();
    private static final long PANEL_LOGIC_DOCK_LEASE_TIMEOUT_TICKS = 40L;

    private final MinecraftServer server;
    private final RadarNetworkSavedData savedData;
    private final Map<UUID, RadarNetworkRuntime> runtimeNetworks = new HashMap<>();
    private final Map<UUID, Set<GlobalPos>> loadedRadarSources = new HashMap<>();
    private final Map<UUID, Set<GlobalPos>> loadedLogicDocks = new HashMap<>();
    private final Map<UUID, LogicDockResolution> logicDockResolutionCache = new HashMap<>();
    private final Map<UUID, LogicDockPolicy> logicDockPolicyCache = new HashMap<>();
    private final Map<UUID, Map<PanelLogicDockKey, PanelLogicDockRegistration>> panelLogicDocks =
            new HashMap<>();

    private RadarNetworkManager(MinecraftServer server) {
        this.server = server;
        this.savedData = RadarNetworkSavedData.get(server);
        // Из сохранения восстанавливаются только постоянные настройки; ссылки и кэши
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
            long gameTime = server.overworld().getGameTime();
            manager.prunePanelLogicDocks(gameTime);
            int trackInterval = Math.max(1, PowerRadarServerConfig.radarScanUpdateIntervalTicks());
            if (Math.floorMod(gameTime, trackInterval) == trackInterval - 1) {
                manager.refreshSelectedTargetTracks(gameTime);
            }
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
        setTargetControllersAllowed(id, false);
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

    public boolean targetControllersAllowed(UUID id) {
        return this.savedData.get(id)
                .map(RadarNetworkRecord::targetControllersAllowed)
                .orElse(true);
    }

    public void setTargetControllersAllowed(UUID id, boolean allowed) {
        RadarNetworkRecord record = ensureNetwork(id);
        if (record.targetControllersAllowed() == allowed) {
            return;
        }
        record.setTargetControllersAllowed(allowed);
        if (PowerRadarDebugOptions.targetSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][TargetSelection] network={} event=target-controllers-changed allowed={}",
                    id,
                    allowed);
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
    }

    public void unloadLink(UUID id, GlobalPos linkPos) {
        RadarNetworkRuntime runtime = this.runtime(id);
        runtime.loadedLinks().remove(linkPos);
        invalidateLogicDockCache(id);
        this.detachMonitorFromLink(id, linkPos);
    }

    public RadarLinkReconcileResult attachControllerFromLink(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        this.addPersistentLink(id, linkPos);
        if (controllerOwnedByAnotherLink(id, linkPos, controllerPos)) {
            return RadarLinkReconcileResult.CONTROLLER_ALREADY_BOUND;
        }
        this.upsertControllerBinding(id, linkPos, controllerPos);
        return RadarLinkReconcileResult.CONTROLLER_ATTACHED;
    }

    public void detachControllerFromLink(UUID id, GlobalPos linkPos) {
        this.removeControllerBinding(id, linkPos);
    }

    public void attachMonitorFromLink(UUID id, GlobalPos linkPos, GlobalPos monitorPos) {
        this.runtime(id).monitorLinkToMonitorPos().put(linkPos, monitorPos);
    }

    /** Регистрирует загруженный Radar Controller как локальный источник снимков сети. */
    public void loadRadarSource(UUID id, GlobalPos sourcePos) {
        ensureNetwork(id);
        this.loadedRadarSources.computeIfAbsent(id, ignored -> new HashSet<>()).add(sourcePos);
        this.runtime(id).invalidateDisplaySnapshots();
    }

    public void unloadRadarSource(UUID id, GlobalPos sourcePos) {
        Set<GlobalPos> sources = this.loadedRadarSources.get(id);
        if (sources == null) {
            return;
        }
        if (sources.remove(sourcePos)) {
            this.runtime(id).invalidateDisplaySnapshots();
        }
        if (sources.isEmpty()) {
            this.loadedRadarSources.remove(id);
        }
    }

    public void loadLogicDock(UUID id, GlobalPos dockPos) {
        ensureNetwork(id);
        this.loadedLogicDocks.computeIfAbsent(id, ignored -> new HashSet<>()).add(dockPos);
        invalidateLogicDockCache(id);
    }

    public void unloadLogicDock(UUID id, GlobalPos dockPos) {
        Set<GlobalPos> docks = this.loadedLogicDocks.get(id);
        if (docks == null) {
            return;
        }
        docks.remove(dockPos);
        if (docks.isEmpty()) {
            this.loadedLogicDocks.remove(id);
        }
        invalidateLogicDockCache(id);
    }

    /** Runtime-регистрация обычного участника без владения SavedData-топологией. */
    public void loadNetworkNode(UUID id, GlobalPos nodePos) {
        ensureNetwork(id);
        this.runtime(id).loadedLinks().add(nodePos);
    }

    public void unloadNetworkNode(UUID id, GlobalPos nodePos) {
        RadarNetworkRuntime runtime = this.runtimeNetworks.get(id);
        if (runtime != null) {
            runtime.loadedLinks().remove(nodePos);
        }
    }

    public boolean isMonitorAttachedAt(UUID id, GlobalPos linkPos, GlobalPos monitorPos) {
        RadarNetworkRuntime runtime = this.runtimeNetworks.get(id);
        return runtime != null && monitorPos.equals(runtime.monitorLinkToMonitorPos().get(linkPos));
    }

    public void detachMonitorFromLink(UUID id, GlobalPos linkPos) {
        this.runtime(id).monitorLinkToMonitorPos().remove(linkPos);
    }

    public RadarNetworkStatus networkStatus(UUID id) {
        Set<GlobalPos> sources = this.loadedRadarSources.get(id);
        if (sources == null || sources.isEmpty()) {
            return RadarNetworkStatus.NO_RADAR;
        }
        for (GlobalPos sourcePos : sources) {
            ServerLevel level = this.server.getLevel(sourcePos.dimension());
            if (level != null && LoadedRadarWorldAccess.blockEntity(
                    level, sourcePos.pos()) instanceof RadarControllerBlockEntity controller
                    && id.equals(controller.radarNetworkId())) {
                return RadarNetworkStatus.ACTIVE;
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

    // Порядок координат задаёт детерминированную агрегацию доступных локальных источников.
    public ControllersResolution resolveControllersForConsumer(UUID id, GlobalPos consumerPos) {
        Set<GlobalPos> registeredSources = this.loadedRadarSources.get(id);
        if (registeredSources == null || registeredSources.isEmpty()) {
            return ControllersResolution.empty(RadarNetworkConnectionStatus.NO_RADAR);
        }
        List<GlobalPos> sources = new ArrayList<>(registeredSources);
        sources.sort(Comparator.comparing((GlobalPos pos) -> pos.dimension().location().toString())
                .thenComparingLong(pos -> pos.pos().asLong()));
        ArrayList<RadarControllerBlockEntity> controllers = new ArrayList<>();
        boolean dimensionBlocked = false;
        boolean outOfRange = false;
        boolean offline = false;
        for (GlobalPos sourcePos : sources) {
            if (!sourcePos.dimension().equals(consumerPos.dimension())) {
                dimensionBlocked = true;
                continue;
            }
            if (!isWithinSourceRange(consumerPos, sourcePos)) {
                outOfRange = true;
                continue;
            }
            ServerLevel controllerLevel = this.server.getLevel(sourcePos.dimension());
            if (controllerLevel == null) {
                offline = true;
                continue;
            }
            BlockEntity blockEntity = LoadedRadarWorldAccess.blockEntity(
                    controllerLevel, sourcePos.pos());
            if (blockEntity instanceof RadarControllerBlockEntity controller
                    && id.equals(controller.radarNetworkId())) {
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
        Set<GlobalPos> sources = this.loadedRadarSources.get(id);
        if (sources == null) {
            return Optional.empty();
        }
        for (GlobalPos sourcePos : sources) {
            if (!sourcePos.dimension().equals(monitorLevel.dimension())) {
                continue;
            }
            if (LoadedRadarWorldAccess.blockEntity(
                    monitorLevel, sourcePos.pos()) instanceof RadarControllerBlockEntity controller
                    && id.equals(controller.radarNetworkId())) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> selectedTargetUuid(UUID id) {
        return this.runtime(id).selectedTargetUuid();
    }

    public void setSelectedTargetUuid(UUID id, UUID targetUuid) {
        RadarNetworkRecord record = this.ensureNetwork(id);
        UUID previousTarget = runtime(id).selectedTargetUuid().orElse(null);
        if (!Objects.equals(record.selectedTargetUuid(), targetUuid)) {
            record.setSelectedTargetUuid(targetUuid);
            this.savedData.setDirty();
        }
        this.runtime(id).setSelectedTargetUuid(targetUuid);
        if (PowerRadarDebugOptions.targetSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][TargetSelection] network={} previousTarget={} selectedTarget={} changed={}",
                    id,
                    previousTarget,
                    targetUuid,
                    !Objects.equals(previousTarget, targetUuid));
        }
        if (targetUuid != null) {
            refreshSelectedTargetTrack(record, this.server.overworld().getGameTime());
        }
    }

    public SelectedTargetRuntimeSnapshot selectedTargetSnapshot(UUID id) {
        if (!networkExists(id)) {
            return SelectedTargetRuntimeSnapshot.EMPTY;
        }
        RadarNetworkRuntime runtime = this.runtime(id);
        long gameTime = this.server.overworld().getGameTime();
        if (!runtime.selectedTargetUpdatedAt(gameTime)) {
            refreshSelectedTargetLiveState(runtime, gameTime);
        }
        return runtime.selectedTargetSnapshot();
    }

    /**
     * На новом общем снимке определяет, какие радары действительно подтвердили выбранный UUID.
     * Метод читает только готовые tracks загруженных контроллеров и не создаёт chunk tickets.
     */
    private void refreshSelectedTargetTracks(long gameTime) {
        for (RadarNetworkRecord record : this.savedData.records()) {
            refreshSelectedTargetTrack(record, gameTime);
        }
    }

    private void refreshSelectedTargetTrack(RadarNetworkRecord record, long gameTime) {
        RadarNetworkRuntime runtime = this.runtime(record.id());
        UUID selectedTarget = runtime.selectedTargetUuid().orElse(null);
        if (selectedTarget == null) {
            return;
        }

        ArrayList<RadarControllerBlockEntity> controllers = loadedControllers(record);
        long scanFingerprint = selectedTargetScanFingerprint(controllers);
        if (scanFingerprint == runtime.selectedTargetScanFingerprint()) {
            return;
        }

        SelectedTargetRuntimeSnapshot.TargetView measuredTarget = null;
        Set<RadarId> confirmingRadars = new HashSet<>();
        for (RadarControllerBlockEntity controller : controllers) {
            TrackedTargetView track = controller.findTrackedTarget(selectedTarget);
            if (track == null
                    || controller.lastScanGameTime() <= 0L
                    || track.lastConfirmedAliveGameTime() < controller.lastScanGameTime()) {
                continue;
            }
            confirmingRadars.add(controller.radarId());
            if (measuredTarget == null) {
                measuredTarget = SelectedTargetRuntimeSnapshot.TargetView.measured(track);
            }
        }
        runtime.putSelectedTargetTrack(
                scanFingerprint,
                selectedTarget,
                measuredTarget,
                confirmingRadars,
                gameTime);
    }

    // Живые координаты и проверка isAlive вычисляются максимум один раз за тик на сеть.
    private void refreshSelectedTargetLiveState(RadarNetworkRuntime runtime, long gameTime) {
        RadarNetworkRuntime.SelectedTargetTrackSelection selection = runtime.selectedTargetTrack();
        if (!selection.confirmed()
                || selection.targetUuid() == null
                || selection.measuredTarget() == null) {
            return;
        }

        SelectedTargetRuntimeSnapshot.TargetView measured = selection.measuredTarget();
        ServerLevel targetLevel = this.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, measured.dimensionId()));
        if (targetLevel == null) {
            runtime.putLiveSelectedTarget(
                    SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE,
                    selection.targetUuid(),
                    selection.confirmingRadars(),
                    gameTime,
                    null);
            return;
        }

        if (measured.sourceType() == TargetSourceType.STRUCTURE) {
            SableStructureObservation observation = SableRadarIntegration
                    .loadedStructure(targetLevel, selection.targetUuid())
                    .orElse(null);
            if (observation == null) {
                runtime.putLiveSelectedTarget(
                        SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE,
                        selection.targetUuid(),
                        selection.confirmingRadars(),
                        gameTime,
                        null);
                return;
            }
            double height = Math.max(0.1D, observation.worldBounds().getYsize());
            Vec3 targetingBase = observation.geometricCenter().subtract(0.0D, height * 0.5D, 0.0D);
            runtime.putLiveSelectedTarget(
                    SelectedTargetRuntimeSnapshot.Status.LIVE,
                    selection.targetUuid(),
                    selection.confirmingRadars(),
                    gameTime,
                    SelectedTargetRuntimeSnapshot.TargetView.liveStructure(
                            measured,
                            targetingBase,
                            observation.velocity(),
                            gameTime,
                            height));
            return;
        }

        Entity entity = targetLevel.getEntity(selection.targetUuid());
        if (entity == null || !entity.isAlive()) {
            runtime.putLiveSelectedTarget(
                    SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE,
                    selection.targetUuid(),
                    selection.confirmingRadars(),
                    gameTime,
                    null);
            return;
        }
        runtime.putLiveSelectedTarget(
                SelectedTargetRuntimeSnapshot.Status.LIVE,
                selection.targetUuid(),
                selection.confirmingRadars(),
                gameTime,
                SelectedTargetRuntimeSnapshot.TargetView.liveEntity(
                        measured,
                        entity.getId(),
                        entity.level().dimension().location(),
                        entity.position(),
                        entity.getDeltaMovement(),
                        gameTime,
                        entity.getBbHeight(),
                        Math.max(measured.approximateSize(),
                                Math.max(entity.getBbWidth(), entity.getBbHeight()))));
    }

    // Загруженные источники сортируются, чтобы несколько потребителей выбирали одинаковый первый track.
    private ArrayList<RadarControllerBlockEntity> loadedControllers(RadarNetworkRecord record) {
        ArrayList<RadarControllerBlockEntity> controllers = new ArrayList<>();
        List<GlobalPos> sources = new ArrayList<>(
                this.loadedRadarSources.getOrDefault(record.id(), Set.of()));
        sources.sort(Comparator.comparing((GlobalPos pos) -> pos.dimension().location().toString())
                .thenComparingLong(pos -> pos.pos().asLong()));
        for (GlobalPos sourcePos : sources) {
            ServerLevel level = this.server.getLevel(sourcePos.dimension());
            if (level == null) {
                continue;
            }
            if (LoadedRadarWorldAccess.blockEntity(
                    level, sourcePos.pos()) instanceof RadarControllerBlockEntity controller
                    && record.id().equals(controller.radarNetworkId())) {
                controllers.add(controller);
            }
        }
        return controllers;
    }

    private static long selectedTargetScanFingerprint(List<RadarControllerBlockEntity> controllers) {
        long fingerprint = 17L;
        for (RadarControllerBlockEntity controller : controllers) {
            if (controller.getLevel() != null) {
                fingerprint = 31L * fingerprint
                        + controller.getLevel().dimension().location().hashCode();
            }
            fingerprint = 31L * fingerprint + controller.getBlockPos().asLong();
            fingerprint = 31L * fingerprint + controller.lastScanGameTime();
        }
        return fingerprint;
    }

    public int autotargetFilterMask(UUID id) {
        return cachedLogicDockPolicy(id).targetingMask();
    }

    public int displayFilterMask(UUID id) {
        return cachedLogicDockPolicy(id).displayMask();
    }

    public int displayFilterMaskForController(GlobalPos controllerPos) {
        ServerLevel level = this.server.getLevel(controllerPos.dimension());
        if (level == null || !(LoadedRadarWorldAccess.blockEntity(
                level, controllerPos.pos()) instanceof RadarControllerBlockEntity controller)) {
            return RadarDetectionFilters.DEFAULT_MASK;
        }
        UUID networkId = controller.radarNetworkId();
        return networkId == null ? RadarDetectionFilters.DEFAULT_MASK : displayFilterMask(networkId);
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
        RadarNetworkRuntime.DisplaySnapshotCacheEntry cached = runtime.displaySnapshot(revision);
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
                    displayMask,
                    targetingMask,
                    runtime.selectedTargetUuid().orElse(null),
                    onlinePlayerNames,
                    allowlistedPlayers,
                    allowlistedSables);
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
                    || !(LoadedRadarWorldAccess.blockEntity(
                            level, linkPos.pos()) instanceof ShellAlarmBlockEntity alarm)
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
            return containsIgnoringCase(policy.playerNames(), name);
        }
        String structureName = SableStructureName.normalize(name);
        return structureName != null && containsIgnoringCase(policy.sableNames(), structureName);
    }

    private static boolean containsIgnoringCase(List<String> values, String candidate) {
        for (String value : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
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

    /** Сбрасывает политику прямой сети Logic Dock и оставшихся панельных подключений. */
    public void invalidateLogicDockCachesAt(ServerLevel level, BlockPos dockPos) {
        Set<UUID> affectedNetworks = new HashSet<>();
        if (LoadedRadarWorldAccess.blockEntity(level, dockPos) instanceof LogicDockBlockEntity dock
                && dock.radarNetworkId() != null) {
            affectedNetworks.add(dock.radarNetworkId());
        }
        this.logicDockResolutionCache.forEach((networkId, resolution) -> {
            if (resolution.active() instanceof LogicDockBlockEntity dock
                    && dock.getLevel() == level
                    && dock.getBlockPos().equals(dockPos)) {
                affectedNetworks.add(networkId);
            }
        });
        affectedNetworks.forEach(this::invalidateLogicDockCache);
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

    // Ровно один загруженный прямой или панельный Logic Dock становится авторитетным.
    public LogicDockResolution resolveLogicDock(UUID id) {
        List<LogicDockPolicySource> docks = new ArrayList<>();
        List<GlobalPos> directDockPositions = new ArrayList<>(
                this.loadedLogicDocks.getOrDefault(id, Set.of()));
        directDockPositions.sort(Comparator.comparing((GlobalPos pos) -> pos.dimension().location().toString())
                .thenComparingLong(pos -> pos.pos().asLong()));
        for (GlobalPos dockPos : directDockPositions) {
            ServerLevel level = this.server.getLevel(dockPos.dimension());
            if (level != null && LoadedRadarWorldAccess.blockEntity(
                    level, dockPos.pos()) instanceof LogicDockBlockEntity dock
                    && id.equals(dock.radarNetworkId())) {
                docks.add(dock);
            }
        }
        Map<PanelLogicDockKey, PanelLogicDockRegistration> panelRegistrations =
                this.panelLogicDocks.get(id);
        if (panelRegistrations != null) {
            for (PanelLogicDockRegistration registration : panelRegistrations.values()) {
                LogicDockPolicySource source = registration.source;
                if (source.isAvailableForNetwork(id) && !docks.contains(source)) {
                    docks.add(source);
                }
            }
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
        if (!this.savedData.removeIfNoLinks(id)) {
            return;
        }
        this.runtimeNetworks.remove(id);
        this.logicDockResolutionCache.remove(id);
        this.logicDockPolicyCache.remove(id);
        this.panelLogicDocks.remove(id);
    }

    // Первый сохранённый Link остаётся владельцем радара, пока не будет разрушен или отвёрнут.
    // Это не позволяет нескольким периодическим reconcile перетягивать контроллер между сетями.
    private boolean controllerOwnedByAnotherLink(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        for (RadarNetworkRecord network : this.savedData.records()) {
            for (RadarControllerEndpointBinding binding : network.controllerBindings()) {
                if (!binding.controllerPos().equals(controllerPos)) {
                    continue;
                }
                return !network.id().equals(id) || !binding.radarLinkPos().equals(linkPos);
            }
        }
        return false;
    }

    // Обновляет только привязку текущего Link; чужое владение проверено до вызова.
    private boolean upsertControllerBinding(UUID id, GlobalPos linkPos, GlobalPos controllerPos) {
        RadarNetworkRecord record = this.ensureNetwork(id);
        RadarControllerEndpointBinding newBinding = new RadarControllerEndpointBinding(linkPos, controllerPos);
        RadarControllerEndpointBinding previous = null;
        for (RadarControllerEndpointBinding binding : record.controllerBindings()) {
            if (binding.radarLinkPos().equals(linkPos)) {
                previous = binding;
                break;
            }
        }
        if (newBinding.equals(previous)) {
            return false;
        }
        if (previous != null) {
            record.controllerBindings().remove(previous);
        }
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

    private boolean isWithinSourceRange(GlobalPos consumerPos, GlobalPos sourcePos) {
        if (!consumerPos.dimension().equals(sourcePos.dimension())) {
            return false;
        }
        ServerLevel level = this.server.getLevel(consumerPos.dimension());
        if (level == null) {
            return false;
        }
        // UUID задаёт членство, а доступность конкретного snapshot — мировое расстояние до источника.
        Vec3 consumerWorldPos = RadarWorldPoseResolver.worldPosition(
                level, consumerPos.pos());
        Vec3 radarWorldPos = RadarWorldPoseResolver.worldPosition(level, sourcePos.pos());
        long max = RadarConstants.radarNetworkSourceRangeBlocks();
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

}
