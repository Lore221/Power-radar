package com.limbo2136.powerradar.interception;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Stable runtime identity for an interception controller.
 *
 * <p>Sable sublevels share the parent dimension and may reuse local block coordinates, so a
 * {@code GlobalPos} alone cannot distinguish controllers installed on different structures.</p>
 */
public record InterceptionControllerKey(
        ResourceKey<Level> dimension,
        @Nullable UUID structureUuid,
        BlockPos localPos
) {
    public InterceptionControllerKey {
        localPos = localPos.immutable();
    }
}
