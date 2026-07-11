package com.limbo2136.powerradar;

import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModCreativeTabs;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.registry.ModItems;
import com.limbo2136.powerradar.registry.ModSounds;
import com.limbo2136.powerradar.client.PowerRadarClientConfig;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.create.PowerRadarMovementChecks;
import com.limbo2136.powerradar.compat.create.PowerRadarStressValues;
import com.limbo2136.powerradar.compat.create.display.PowerRadarDisplaySources;
import com.limbo2136.powerradar.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PowerRadar.MOD_ID)
public final class PowerRadar {
    public static final String MOD_ID = "power_radar";
    public static final Logger LOGGER = LoggerFactory.getLogger("PowerRadar");

    public PowerRadar(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, PowerRadarServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, PowerRadarClientConfig.SPEC);
        ModBlocks.register(modEventBus);
        ModSounds.register(modEventBus);
        PowerRadarCeeDeviceTypes.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        PowerRadarDisplaySources.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetwork.register(modEventBus);
        modEventBus.addListener(PowerRadarDisplaySources::setup);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(PowerRadarStressValues::register));
        PowerRadarMovementChecks.register();
    }
}
