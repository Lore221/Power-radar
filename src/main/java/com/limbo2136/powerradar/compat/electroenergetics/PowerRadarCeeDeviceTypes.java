package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PowerRadarCeeDeviceTypes {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICE_TYPES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, PowerRadar.MOD_ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<RadarControllerCeeDevice>> RADAR_CONTROLLER =
            register("radar_controller", () -> new SimulatedDeviceType<RadarControllerCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_controller"),
                    (type, level, pos, devicesSavedData) -> new RadarControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.RADAR_CONTROLLER.get(), ModBlocks.AIR_RADAR_CONTROLLER.get(),
                            ModBlocks.SURFACE_RADAR_CONTROLLER.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<MonitorControllerCeeDevice>> RADAR_MONITOR_CONTROLLER =
            register("radar_monitor_controller", () -> new SimulatedDeviceType<MonitorControllerCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_controller"),
                    (type, level, pos, devicesSavedData) -> new MonitorControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.RADAR_MONITOR_CONTROLLER.get(), ModBlocks.ONBOARD_COMPUTER.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ComputingBlockCeeDevice>> COMPUTING_BLOCK =
            register("computing_block", () -> new SimulatedDeviceType<ComputingBlockCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "computing_block"),
                    (type, level, pos, data) -> new ComputingBlockCeeDevice(level, pos, data, type),
                    List.of(ModBlocks.COMPUTING_BLOCK.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<TargetControllerCeeDevice>> TARGET_CONTROLLER =
            register("target_controller", () -> new SimulatedDeviceType<TargetControllerCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "target_controller"),
                    (type, level, pos, devicesSavedData) -> new TargetControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.TARGET_CONTROLLER.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ShellAlarmCeeDevice>> SHELL_ALARM =
            register("shell_alarm", () -> new SimulatedDeviceType<ShellAlarmCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "shell_alarm"),
                    (type, level, pos, devicesSavedData) -> new ShellAlarmCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.SHELL_ALARM.get())));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<InterceptionControllerCeeDevice>> INTERCEPTION_CONTROLLER =
            register("interception_controller", () -> new SimulatedDeviceType<InterceptionControllerCeeDevice>(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "interception_controller"),
                    (type, level, pos, devicesSavedData) ->
                            new InterceptionControllerCeeDevice(level, pos, devicesSavedData, type),
                    List.of(ModBlocks.INTERCEPTION_CONTROLLER.get())));

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
