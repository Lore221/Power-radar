package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public final class IncompleteOverviewModuleItemProperties {
    private static final ResourceLocation ASSEMBLY_STAGE = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID,
            "overview_assembly_stage");

    private IncompleteOverviewModuleItemProperties() {
    }

    public static void register() {
        ItemProperties.register(
                ModItems.INCOMPLETE_OVERVIEW_MODULE.get(),
                ASSEMBLY_STAGE,
                (stack, level, entity, seed) -> ModItems.INCOMPLETE_OVERVIEW_MODULE.get().modelStage(stack));
    }
}
