package com.limbo2136.powerradar.compat.aeronautics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Серверный runtime-индекс запитанных РЭБ-систем, установленных на Sable. */
public final class EwSystemManager {
    private static final Map<MinecraftServer, RuntimeState> STATES = new WeakHashMap<>();

    private EwSystemManager() {
    }

    public static void register(
            MinecraftServer server,
            ResourceLocation dimensionId,
            UUID structureUuid,
            UUID moduleToken
    ) {
        state(server).register(new StructureKey(dimensionId, structureUuid), moduleToken);
    }

    public static void unregister(
            MinecraftServer server,
            ResourceLocation dimensionId,
            UUID structureUuid,
            UUID moduleToken
    ) {
        RuntimeState state = STATES.get(server);
        if (state != null) {
            state.unregister(new StructureKey(dimensionId, structureUuid), moduleToken);
        }
    }

    public static boolean suppressesSilhouette(
            MinecraftServer server,
            ResourceLocation dimensionId,
            UUID structureUuid
    ) {
        RuntimeState state = STATES.get(server);
        return state != null && state.contains(new StructureKey(dimensionId, structureUuid));
    }

    public static void stopServer(MinecraftServer server) {
        STATES.remove(server);
    }

    private static RuntimeState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new RuntimeState());
    }

    private record StructureKey(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private static final class RuntimeState {
        private final Map<StructureKey, Set<UUID>> modulesByStructure = new HashMap<>();

        private void register(StructureKey structure, UUID moduleToken) {
            this.modulesByStructure.computeIfAbsent(structure, ignored -> new HashSet<>()).add(moduleToken);
        }

        private void unregister(StructureKey structure, UUID moduleToken) {
            Set<UUID> modules = this.modulesByStructure.get(structure);
            if (modules == null) {
                return;
            }
            modules.remove(moduleToken);
            if (modules.isEmpty()) {
                this.modulesByStructure.remove(structure);
            }
        }

        private boolean contains(StructureKey structure) {
            Set<UUID> modules = this.modulesByStructure.get(structure);
            return modules != null && !modules.isEmpty();
        }
    }
}
