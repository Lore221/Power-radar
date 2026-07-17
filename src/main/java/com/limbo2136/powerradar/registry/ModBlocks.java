package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.MechanicalSirenBlock;
import com.limbo2136.powerradar.block.OverviewModuleBlock;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.limbo2136.powerradar.block.RadarDisplayBlock;
import com.limbo2136.powerradar.block.RadarMonitorControllerBlock;
import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.block.ShellAlarmBlock;
import com.limbo2136.powerradar.block.TargetControllerBlock;
import com.limbo2136.powerradar.block.InterceptionControllerBlock;
import com.limbo2136.powerradar.block.AirRadarControllerBlock;
import com.limbo2136.powerradar.block.SurfaceRadarControllerBlock;
import com.limbo2136.powerradar.block.ComputingBlock;
import com.limbo2136.powerradar.block.OnboardComputerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PowerRadar.MOD_ID);

    public static final DeferredBlock<RadarControllerBlock> RADAR_CONTROLLER = BLOCKS.registerBlock(
            "radar_controller",
            RadarControllerBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<AirRadarControllerBlock> AIR_RADAR_CONTROLLER = BLOCKS.registerBlock(
            "air_radar_controller",
            AirRadarControllerBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<SurfaceRadarControllerBlock> SURFACE_RADAR_CONTROLLER = BLOCKS.registerBlock(
            "surface_radar_controller",
            SurfaceRadarControllerBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<ComputingBlock> COMPUTING_BLOCK = BLOCKS.registerBlock(
            "computing_block",
            ComputingBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<OnboardComputerBlock> ONBOARD_COMPUTER = BLOCKS.registerBlock(
            "onboard_computer", OnboardComputerBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion());

    public static final DeferredBlock<RadarPanelBlock> RADAR_PANEL = BLOCKS.registerBlock(
            "radar_panel",
            RadarPanelBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).noOcclusion()
    );

    public static final DeferredBlock<OverviewModuleBlock> OVERVIEW_MODULE = BLOCKS.registerBlock(
            "overview_module",
            OverviewModuleBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).noOcclusion()
    );

    public static final DeferredBlock<RadarMonitorControllerBlock> RADAR_MONITOR_CONTROLLER = BLOCKS.registerBlock(
            "radar_monitor_controller",
            RadarMonitorControllerBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F, 4.0F)
    );

    public static final DeferredBlock<RadarDisplayBlock> RADAR_DISPLAY = BLOCKS.registerBlock(
            "radar_display",
            RadarDisplayBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).noOcclusion()
    );

    public static final DeferredBlock<RadarLinkBlock> RADAR_LINK = BLOCKS.registerBlock(
            "radar_link",
            RadarLinkBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F, 4.0F)
    );

    public static final DeferredBlock<TargetControllerBlock> TARGET_CONTROLLER = BLOCKS.registerBlock(
            "target_controller",
            TargetControllerBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<MechanicalSirenBlock> MECHANICAL_SIREN = BLOCKS.registerBlock(
            "mechanical_siren",
            MechanicalSirenBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()
    );

    public static final DeferredBlock<ShellAlarmBlock> SHELL_ALARM = BLOCKS.registerBlock(
            "shell_alarm",
            ShellAlarmBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
    );

    public static final DeferredBlock<InterceptionControllerBlock> INTERCEPTION_CONTROLLER =
            BLOCKS.registerBlock(
                    "interception_controller",
                    InterceptionControllerBlock::new,
                    BlockBehaviour.Properties.of().strength(3.0F, 6.0F)
            );

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
