package com.limbo2136.powerradar.compat.aeronautics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
<<<<<<< HEAD
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
=======
>>>>>>> f5d96b00c884c42dfafa46e4f214952d230a016d
import net.neoforged.fml.ModList;

public final class SableRadarIntegration {
    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");
<<<<<<< HEAD
    private static final boolean AERONAUTICS_LOADED = ModList.get().isLoaded("aeronautics");
=======
>>>>>>> f5d96b00c884c42dfafa46e4f214952d230a016d

    private SableRadarIntegration() {
    }

<<<<<<< HEAD
    public static boolean isAeronauticsLoaded() {
        return AERONAUTICS_LOADED && SABLE_LOADED;
    }

    public static boolean canPlaceOnStructure(Level level, BlockPos pos) {
        return isAeronauticsLoaded() && SableStructureScanner.isInsideStructure(level, pos);
    }

=======
>>>>>>> f5d96b00c884c42dfafa46e4f214952d230a016d
    public static List<SableStructureObservation> loadedStructures(ServerLevel level) {
        return SABLE_LOADED ? SableStructureScanner.loadedStructures(level) : List.of();
    }

    public static Optional<SableStructureObservation> loadedStructure(ServerLevel level, UUID structureUuid) {
        return SABLE_LOADED
                ? SableStructureScanner.loadedStructure(level, structureUuid)
                : Optional.empty();
    }

<<<<<<< HEAD
    public static Optional<UUID> containingStructureUuid(ServerLevel level, BlockPos pos) {
        return SABLE_LOADED ? SableStructureScanner.containingStructureUuid(level, pos) : Optional.empty();
    }

=======
>>>>>>> f5d96b00c884c42dfafa46e4f214952d230a016d
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
