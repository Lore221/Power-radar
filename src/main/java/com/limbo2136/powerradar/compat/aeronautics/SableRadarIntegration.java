package com.limbo2136.powerradar.compat.aeronautics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class SableRadarIntegration {
    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");
    private static final boolean AERONAUTICS_LOADED = ModList.get().isLoaded("aeronautics");

    private SableRadarIntegration() {
    }

    public static boolean isAeronauticsLoaded() {
        return AERONAUTICS_LOADED && SABLE_LOADED;
    }

    public static boolean canPlaceOnStructure(Level level, BlockPos pos) {
        return isAeronauticsLoaded() && SableStructureScanner.isInsideStructure(level, pos);
    }

    public static List<SableStructureObservation> loadedStructures(ServerLevel level) {
        return SABLE_LOADED ? SableStructureScanner.loadedStructures(level) : List.of();
    }

    public static Optional<SableStructureObservation> loadedStructure(ServerLevel level, UUID structureUuid) {
        return SABLE_LOADED
                ? SableStructureScanner.loadedStructure(level, structureUuid)
                : Optional.empty();
    }

    public static Optional<UUID> containingStructureUuid(ServerLevel level, BlockPos pos) {
        return SABLE_LOADED ? SableStructureScanner.containingStructureUuid(level, pos) : Optional.empty();
    }

    public static void markDetected(ServerLevel level, SableStructureObservation observation, long gameTime) {
        if (SABLE_LOADED) {
            SableStructureScanner.markDetected(level, observation.structureUuid(), gameTime);
        }
    }

    public static void tickSilhouetteCache(MinecraftServer server) {
        if (SABLE_LOADED) {
            SableStructureScanner.tickSilhouetteCache(server);
        }
    }

    public static void clearSilhouetteCache(MinecraftServer server) {
        if (SABLE_LOADED) {
            SableStructureScanner.clearSilhouetteCache(server);
        }
    }

    public static Optional<SableSilhouetteSnapshot> silhouetteSnapshot(
            MinecraftServer server,
            ResourceLocation dimensionId,
            UUID structureUuid
    ) {
        return SABLE_LOADED
                ? SableStructureScanner.silhouetteSnapshot(server, dimensionId, structureUuid)
                : Optional.empty();
    }
}
