package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import java.util.List;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PowerRadarCeeDeviceTypes {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICE_TYPES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, PowerRadar.MOD_ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<RadarControllerCeeDevice>> RADAR_CONTROLLER =
            register("radar_controller", () -> new SimulatedDeviceType<RadarControllerCeeDevice>(
                    PowerRadar.id("radar_controller"),
                    (type, level, pos, devicesSavedData) -> new RadarControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.RADAR_CONTROLLER.get(), ModBlocks.AIR_RADAR_CONTROLLER.get(),
                            ModBlocks.SURFACE_RADAR_CONTROLLER.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<MonitorControllerCeeDevice>> RADAR_MONITOR_CONTROLLER =
            register("radar_monitor_controller", () -> new SimulatedDeviceType<MonitorControllerCeeDevice>(
                    PowerRadar.id("radar_monitor_controller"),
                    (type, level, pos, devicesSavedData) -> new MonitorControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.RADAR_MONITOR_CONTROLLER.get(), ModBlocks.ONBOARD_COMPUTER.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<LogicDockCeeDevice>> LOGIC_DOCK =
            register("logic_dock", () -> new SimulatedDeviceType<LogicDockCeeDevice>(
                    PowerRadar.id("logic_dock"),
                    (type, level, pos, data) -> new LogicDockCeeDevice(level, pos, data, type),
                    List.of(ModBlocks.LOGIC_DOCK.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<EwSystemCeeDevice>> EW_SYSTEM =
            register("ew_system", () -> new SimulatedDeviceType<EwSystemCeeDevice>(
                    PowerRadar.id("ew_system"),
                    (type, level, pos, data) -> new EwSystemCeeDevice(level, pos, data, type),
                    List.of(ModBlocks.EW_SYSTEM.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<TargetControllerCeeDevice>> TARGET_CONTROLLER =
            CreateBigCannonsIntegration.isLoaded() ? register("target_controller", () -> new SimulatedDeviceType<TargetControllerCeeDevice>(
                    PowerRadar.id("target_controller"),
                    (type, level, pos, devicesSavedData) -> new TargetControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.TARGET_CONTROLLER.get()))) : null;

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ShellAlarmCeeDevice>> SHELL_ALARM =
            CreateBigCannonsIntegration.isLoaded() ? register("shell_alarm", () -> new SimulatedDeviceType<ShellAlarmCeeDevice>(
                    PowerRadar.id("shell_alarm"),
                    (type, level, pos, devicesSavedData) -> new ShellAlarmCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.SHELL_ALARM.get()))) : null;

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<InterceptionControllerCeeDevice>> INTERCEPTION_CONTROLLER =
            CreateBigCannonsIntegration.isLoaded() ? register("interception_controller", () -> new SimulatedDeviceType<InterceptionControllerCeeDevice>(
                    PowerRadar.id("interception_controller"),
                    (type, level, pos, devicesSavedData) ->
                            new InterceptionControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.INTERCEPTION_CONTROLLER.get()))) : null;

    private PowerRadarCeeDeviceTypes() {
    }

    public static void register(IEventBus eventBus) {
        DEVICE_TYPES.register(eventBus);
    }

    private static <T extends com.george_vi.electroenergetics.devices.device.SimulatedDevice> DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<T>> register(
            String name,
            Supplier<SimulatedDeviceType<T>> supplier
    ) {
        return DEVICE_TYPES.register(name, supplier);
    }
}
