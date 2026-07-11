package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.MechanicalSirenBlockEntity;
import com.limbo2136.powerradar.block.entity.OverviewModuleBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PowerRadar.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarControllerBlockEntity>> RADAR_CONTROLLER =
            BLOCK_ENTITIES.register("radar_controller", () -> BlockEntityType.Builder
                    .of(RadarControllerBlockEntity::new, ModBlocks.RADAR_CONTROLLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarMonitorControllerBlockEntity>> RADAR_MONITOR_CONTROLLER =
            BLOCK_ENTITIES.register("radar_monitor_controller", () -> BlockEntityType.Builder
                    .of(RadarMonitorControllerBlockEntity::new, ModBlocks.RADAR_MONITOR_CONTROLLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverviewModuleBlockEntity>> OVERVIEW_MODULE =
            BLOCK_ENTITIES.register("overview_module", () -> BlockEntityType.Builder
                    .of(OverviewModuleBlockEntity::new, ModBlocks.OVERVIEW_MODULE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarLinkBlockEntity>> RADAR_LINK =
            BLOCK_ENTITIES.register("radar_link", () -> BlockEntityType.Builder
                    .of(RadarLinkBlockEntity::new, ModBlocks.RADAR_LINK.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TargetControllerBlockEntity>> TARGET_CONTROLLER =
            BLOCK_ENTITIES.register("target_controller", () -> BlockEntityType.Builder
                    .of(TargetControllerBlockEntity::new, ModBlocks.TARGET_CONTROLLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalSirenBlockEntity>> MECHANICAL_SIREN =
            BLOCK_ENTITIES.register("mechanical_siren", () -> BlockEntityType.Builder
                    .of(MechanicalSirenBlockEntity::new, ModBlocks.MECHANICAL_SIREN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellAlarmBlockEntity>> SHELL_ALARM =
            BLOCK_ENTITIES.register("shell_alarm", () -> BlockEntityType.Builder
                    .of(ShellAlarmBlockEntity::new, ModBlocks.SHELL_ALARM.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptionControllerBlockEntity>> INTERCEPTION_CONTROLLER =
            BLOCK_ENTITIES.register("interception_controller", () -> BlockEntityType.Builder
                    .of(InterceptionControllerBlockEntity::new, ModBlocks.INTERCEPTION_CONTROLLER.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
