package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PowerRadar.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POWER_RADAR_TAB = CREATIVE_TABS.register(
            "power_radar_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.power_radar.power_radar_tab"))
                    .icon(() -> new ItemStack(ModItems.RADAR_CONTROLLER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RADAR_CONTROLLER.get());
                        output.accept(ModItems.AIR_RADAR_CONTROLLER.get());
                        output.accept(ModItems.SURFACE_RADAR_CONTROLLER.get());
                        output.accept(ModItems.COMPUTING_BLOCK.get());
                        if (SableRadarIntegration.isAeronauticsLoaded()) {
                            output.accept(ModItems.ONBOARD_COMPUTER.get());
                        }
                        output.accept(ModItems.TARGETING_CARD.get());
                        output.accept(ModItems.DISPLAY_CARD.get());
                        output.accept(ModItems.ALLOWLIST_CARD.get());
                        output.accept(ModItems.RADAR_PANEL.get());
                        output.accept(ModItems.OVERVIEW_MODULE.get());
                        output.accept(ModItems.RADAR_MONITOR_CONTROLLER.get());
                        output.accept(ModItems.RADAR_DISPLAY.get());
                        output.accept(ModItems.RADAR_LINK.get());
                        output.accept(ModItems.TARGET_CONTROLLER.get());
                        output.accept(ModItems.MECHANICAL_SIREN.get());
                        output.accept(ModItems.SHELL_ALARM.get());
                        output.accept(ModItems.INTERCEPTION_CONTROLLER.get());
                        output.accept(ModItems.INTERCEPTION_FUZE.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
