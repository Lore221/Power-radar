package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.MechanicalSirenBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarDisplayBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.EwSystemBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PowerRadar.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarControllerBlockEntity>> RADAR_CONTROLLER =
            BLOCK_ENTITIES.register("radar_controller", () -> BlockEntityType.Builder
                    .of(RadarControllerBlockEntity::new,
                            radarControllerBlocks())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarDisplayBlockEntity>> RADAR_DISPLAY =
            BLOCK_ENTITIES.register("radar_display", () -> BlockEntityType.Builder
                    .of(RadarDisplayBlockEntity::new, ModBlocks.RADAR_DISPLAY.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogicDockBlockEntity>> LOGIC_DOCK =
            BLOCK_ENTITIES.register("logic_dock", () -> BlockEntityType.Builder
                    .of(LogicDockBlockEntity::new, ModBlocks.LOGIC_DOCK.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OnboardComputerBlockEntity>> ONBOARD_COMPUTER =
            SableRadarIntegration.isAeronauticsLoaded()
                    ? BLOCK_ENTITIES.register("onboard_computer", () -> BlockEntityType.Builder
                            .of(OnboardComputerBlockEntity::new, ModBlocks.ONBOARD_COMPUTER.get()).build(null))
                    : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EwSystemBlockEntity>> EW_SYSTEM =
            SableRadarIntegration.isAeronauticsLoaded()
                    ? BLOCK_ENTITIES.register("ew_system", () -> BlockEntityType.Builder
                            .of(EwSystemBlockEntity::new, ModBlocks.EW_SYSTEM.get()).build(null))
                    : null;

    /** Dormant holder for Radar Link; intentionally not registered with the game. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarLinkBlockEntity>> RADAR_LINK =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, PowerRadar.id("radar_link"));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TargetControllerBlockEntity>> TARGET_CONTROLLER =
            CreateBigCannonsIntegration.isLoaded() ? BLOCK_ENTITIES.register("target_controller", () -> BlockEntityType.Builder
                    .of(TargetControllerBlockEntity::new, ModBlocks.TARGET_CONTROLLER.get())
                    .build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalSirenBlockEntity>> MECHANICAL_SIREN =
            BLOCK_ENTITIES.register("mechanical_siren", () -> BlockEntityType.Builder
                    .of(MechanicalSirenBlockEntity::new, ModBlocks.MECHANICAL_SIREN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellAlarmBlockEntity>> SHELL_ALARM =
            CreateBigCannonsIntegration.isLoaded() ? BLOCK_ENTITIES.register("shell_alarm", () -> BlockEntityType.Builder
                    .of(ShellAlarmBlockEntity::new, ModBlocks.SHELL_ALARM.get())
                    .build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptionControllerBlockEntity>> INTERCEPTION_CONTROLLER =
            CreateBigCannonsIntegration.isLoaded() ? BLOCK_ENTITIES.register("interception_controller", () -> BlockEntityType.Builder
                    .of(InterceptionControllerBlockEntity::new, ModBlocks.INTERCEPTION_CONTROLLER.get())
                    .build(null)) : null;

    private ModBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    private static Block[] radarControllerBlocks() {
        if (!SableRadarIntegration.isAeronauticsLoaded()) {
            return new Block[] {
                    ModBlocks.RADAR_CONTROLLER.get(),
                    ModBlocks.AIR_RADAR_CONTROLLER.get()
            };
        }
        return new Block[] {
                ModBlocks.RADAR_CONTROLLER.get(),
                ModBlocks.AIR_RADAR_CONTROLLER.get(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.get(),
                ModBlocks.AIRCRAFT_RADAR.get()
        };
    }
}
