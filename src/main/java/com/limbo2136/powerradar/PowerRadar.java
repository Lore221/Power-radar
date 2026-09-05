package com.limbo2136.powerradar;

import com.limbo2136.powerradar.advancement.PowerRadarAdvancementTriggers;
import com.limbo2136.powerradar.config.PowerRadarClientConfig;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.panel.PowerRadarPanelAttachmentTypes;
import com.limbo2136.powerradar.compat.create.PowerRadarMovementChecks;
import com.limbo2136.powerradar.compat.create.PowerRadarStressValues;
import com.limbo2136.powerradar.compat.create.display.PowerRadarDisplaySources;
import com.limbo2136.powerradar.network.ModNetwork;
import com.limbo2136.powerradar.radar.OnlinePlayersSnapshotCache;
import com.limbo2136.powerradar.radar.RadarScanCoordinator;
import com.limbo2136.powerradar.compat.aeronautics.SableWarningManager;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.EwSystemManager;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModCreativeTabs;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.registry.ModEntities;
import com.limbo2136.powerradar.registry.ModItems;
import com.limbo2136.powerradar.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PowerRadar.MOD_ID)
public final class PowerRadar {
    public static final String MOD_ID = "power_radar";
    public static final Logger LOGGER = LoggerFactory.getLogger("PowerRadar");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public PowerRadar(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, PowerRadarServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, PowerRadarClientConfig.SPEC);
        ModBlocks.register(modEventBus);
        ModSounds.register(modEventBus);
        PowerRadarCeeDeviceTypes.register(modEventBus);
        PowerRadarPanelAttachmentTypes.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        PowerRadarDisplaySources.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetwork.register(modEventBus);
        PowerRadarAdvancementTriggers.register(modEventBus);
        modEventBus.addListener(PowerRadarDisplaySources::setup);
        modEventBus.addListener(PowerRadar::onCommonSetup);
        PowerRadarMovementChecks.register();
        NeoForge.EVENT_BUS.addListener(PowerRadar::onServerTick);
        NeoForge.EVENT_BUS.addListener(PowerRadar::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PowerRadar::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(PowerRadar::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(PowerRadar::onEntityLeaveLevel);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PowerRadarStressValues::register);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        RadarScanCoordinator.tickServer(event.getServer());
        RadarNetworkManager.tickServer(event.getServer());
        ModNetwork.tickServer(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        RadarScanCoordinator.stopServer(event.getServer());
        SableWarningManager.stopServer(event.getServer());
        EwSystemManager.stopServer(event.getServer());
        OnlinePlayersSnapshotCache.stopServer(event.getServer());
        RadarLinkConnectionResolver.stopServer(event.getServer());
        ModNetwork.stopServer(event.getServer());
        RadarNetworkManager.stopServer(event.getServer());
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        markSableSilhouetteDirty(event);
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        markSableSilhouetteDirty(event);
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            InterceptionCoordinator.logSableInterceptorRemoved(
                    level,
                    event.getEntity().getUUID(),
                    event.getEntity().position(),
                    event.getEntity().getDeltaMovement(),
                    event.getEntity().tickCount);
        }
    }

    private static void markSableSilhouetteDirty(BlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SableRadarIntegration.markSilhouetteDirty(level, event.getPos(), level.getGameTime());
        }
    }
}
