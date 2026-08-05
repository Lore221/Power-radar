package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
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

    public static final DeferredHolder<DisplaySource, MonitorTargetCountDisplaySource> MONITOR_TARGET_COUNT =
            DISPLAY_SOURCES.register("detected_radar_tracks", MonitorTargetCountDisplaySource::new);
    public static final DeferredHolder<DisplaySource, SelectedMonitorTargetDisplaySource> SELECTED_TARGET_COORDINATES =
            DISPLAY_SOURCES.register("selected_target_coordinates", () ->
                    new SelectedMonitorTargetDisplaySource(SelectedMonitorTargetDisplaySource.Field.COORDINATES));
    public static final DeferredHolder<DisplaySource, SelectedMonitorTargetDisplaySource> SELECTED_TARGET_TYPE =
            DISPLAY_SOURCES.register("selected_target_type", () ->
                    new SelectedMonitorTargetDisplaySource(SelectedMonitorTargetDisplaySource.Field.TYPE));
    public static final DeferredHolder<DisplaySource, SelectedMonitorTargetDisplaySource> SELECTED_TARGET_SPEED =
            DISPLAY_SOURCES.register("selected_target_speed", () ->
                    new SelectedMonitorTargetDisplaySource(SelectedMonitorTargetDisplaySource.Field.SPEED));
    public static final DeferredHolder<DisplaySource, ShellAlarmDisplaySource> DANGEROUS_SHELLS =
            CreateBigCannonsIntegration.isLoaded()
                    ? DISPLAY_SOURCES.register("shell_alarm_dangerous_shells", ShellAlarmDisplaySource::new)
                    : null;

    private PowerRadarDisplaySources() {
    }

    public static void register(IEventBus eventBus) {
        DISPLAY_SOURCES.register(eventBus);
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK_ENTITY.register(
                    ModBlockEntities.RADAR_MONITOR_CONTROLLER.get(),
                    List.of(
                            MONITOR_TARGET_COUNT.get(),
                            SELECTED_TARGET_COORDINATES.get(),
                            SELECTED_TARGET_TYPE.get(),
                            SELECTED_TARGET_SPEED.get()));
            if (CreateBigCannonsIntegration.isLoaded()) {
                DisplaySource.BY_BLOCK_ENTITY.register(
                        ModBlockEntities.SHELL_ALARM.get(),
                        List.of(DANGEROUS_SHELLS.get()));
            }
        });
    }
}
