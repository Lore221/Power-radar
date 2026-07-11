package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PowerRadarDisplaySources {
    private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, PowerRadar.MOD_ID);

    public static final DeferredHolder<DisplaySource, DetectedRadarTracksDisplaySource> DETECTED_RADAR_TRACKS =
            DISPLAY_SOURCES.register("detected_radar_tracks", DetectedRadarTracksDisplaySource::new);

    private PowerRadarDisplaySources() {
    }

    public static void register(IEventBus eventBus) {
        DISPLAY_SOURCES.register(eventBus);
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DisplaySource.BY_BLOCK_ENTITY.register(
                ModBlockEntities.RADAR_CONTROLLER.get(),
                List.of(DETECTED_RADAR_TRACKS.get())
        ));
    }
}
