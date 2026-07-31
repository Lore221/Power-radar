package com.limbo2136.powerradar.client.compass;

import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

public final class RadarCompassItemProperties {
    private RadarCompassItemProperties() {
    }

    public static void register() {
        CompassItemPropertyFunction vanillaCompass = new CompassItemPropertyFunction((level, stack, entity) -> {
            LodestoneTracker lodestone = stack.get(DataComponents.LODESTONE_TRACKER);
            return lodestone != null ? lodestone.target().orElse(null) : CompassItem.getSpawnPosition(level);
        });
        CompassItemPropertyFunction radarCompass = new CompassItemPropertyFunction((level, stack, entity) -> {
            UUID networkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            RadarCompassClientCache.Target target = networkId == null
                    ? null
                    : RadarCompassClientCache.target(networkId, level);
            if (target == null || !target.dimensionId().equals(level.dimension().location())) {
                return null;
            }
            ResourceKey<net.minecraft.world.level.Level> dimension =
                    ResourceKey.create(Registries.DIMENSION, target.dimensionId());
            return GlobalPos.of(dimension, BlockPos.containing(target.position()));
        });

        // Null-цель внутри CompassItemPropertyFunction включает то же хаотическое
        // вращение, что и у намагниченного компаса с потерянным магнетитом.
        ItemProperties.register(
                Items.COMPASS,
                ResourceLocation.withDefaultNamespace("angle"),
                (stack, level, entity, seed) -> {
                    if (!stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())) {
                        return vanillaCompass.unclampedCall(stack, level, entity, seed);
                    }
                    if (level == null) {
                        return 0.0F;
                    }
                    return radarCompass.unclampedCall(stack, level, entity, seed);
                });
    }
}
