package com.limbo2136.powerradar.compat.aeronautics;

import com.limbo2136.powerradar.radar.RadarId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Серверное краткоживущее состояние предупреждений для Sable-структур. */
public final class SableWarningManager {
    // Скан публикуется после тиков block entity, поэтому +1 сохраняет ровно пять наблюдаемых тиков сигнала.
    private static final int COVERAGE_ENTRY_SIGNAL_TICKS = 6;
    private static final int TARGETING_SIGNAL_TICKS = 5;
    private static final int TARGETING_SILENCE_TICKS = 5;
    private static final int TARGETING_TTL_TICKS = 2;
    private static final Map<MinecraftServer, State> SERVERS = new WeakHashMap<>();

    private SableWarningManager() {
    }

    public static synchronized void replaceRadarCoverage(
            ServerLevel level,
            RadarId radarId,
            Set<UUID> detectedStructures,
            long gameTime
    ) {
        State state = state(level.getServer());
        Set<UUID> next = new HashSet<>(detectedStructures);
        SableRadarIntegration.containingStructureUuid(level, radarId.controllerPos()).ifPresent(next::remove);
        Set<UUID> previous = state.coverageByRadar.put(radarId, next);
        if (previous != null) {
            for (UUID structureUuid : next) {
                if (!previous.contains(structureUuid)) {
                    state.coverageEntryUntil.merge(
                            structureUuid,
                            gameTime + COVERAGE_ENTRY_SIGNAL_TICKS,
                            Math::max);
                }
            }
        }
        cleanup(state, gameTime);
    }

    public static synchronized void markTargeted(
            MinecraftServer server,
            UUID structureUuid,
            long gameTime
    ) {
        State state = state(server);
        TargetingWindow current = state.targeting.get(structureUuid);
        if (current == null || current.expiresAt <= gameTime) {
            state.targeting.put(
                    structureUuid,
                    new TargetingWindow(gameTime, gameTime + TARGETING_TTL_TICKS));
        } else {
            state.targeting.put(
                    structureUuid,
                    new TargetingWindow(current.startedAt, gameTime + TARGETING_TTL_TICKS));
        }
        cleanup(state, gameTime);
    }

    public static synchronized WarningState warningState(
            MinecraftServer server,
            UUID structureUuid,
            long gameTime,
            boolean dangerousProjectile
    ) {
        if (dangerousProjectile) {
            return WarningState.DANGEROUS_PROJECTILE;
        }
        State state = SERVERS.get(server);
        if (state == null) {
            return WarningState.NONE;
        }
        cleanup(state, gameTime);
        TargetingWindow targeting = state.targeting.get(structureUuid);
        if (targeting != null && targeting.expiresAt > gameTime) {
            int period = TARGETING_SIGNAL_TICKS + TARGETING_SILENCE_TICKS;
            return Math.floorMod(gameTime - targeting.startedAt, period) < TARGETING_SIGNAL_TICKS
                    ? WarningState.TARGETED
                    : WarningState.NONE;
        }
        return state.coverageEntryUntil.getOrDefault(structureUuid, Long.MIN_VALUE) > gameTime
                ? WarningState.RADAR_COVERAGE_ENTRY
                : WarningState.NONE;
    }

    public static synchronized void stopServer(MinecraftServer server) {
        SERVERS.remove(server);
    }

    private static State state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new State());
    }

    private static void cleanup(State state, long gameTime) {
        state.coverageEntryUntil.values().removeIf(expiresAt -> expiresAt <= gameTime);
        state.targeting.values().removeIf(window -> window.expiresAt <= gameTime);
    }

    public enum WarningState {
        NONE,
        RADAR_COVERAGE_ENTRY,
        TARGETED,
        DANGEROUS_PROJECTILE;

        public boolean signalActive() {
            return this != NONE;
        }
    }

    private static final class State {
        private final Map<RadarId, Set<UUID>> coverageByRadar = new HashMap<>();
        private final Map<UUID, Long> coverageEntryUntil = new HashMap<>();
        private final Map<UUID, TargetingWindow> targeting = new HashMap<>();
    }

    private record TargetingWindow(long startedAt, long expiresAt) {
    }
}
