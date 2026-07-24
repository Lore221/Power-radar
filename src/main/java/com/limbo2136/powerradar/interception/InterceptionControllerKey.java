package com.limbo2136.powerradar.interception;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Стабильная личность контроллера перехвата во время выполнения.
 *
 * <p>Подуровни Sable разделяют родительское измерение и могут повторять локальные координаты,
 * поэтому одного {@code GlobalPos} недостаточно для контроллеров на разных структурах.</p>
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
