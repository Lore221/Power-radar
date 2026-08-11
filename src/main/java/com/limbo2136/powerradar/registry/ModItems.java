package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.item.PowerRadarElectricalBlockItem;
import com.limbo2136.powerradar.item.PowerRadarDescriptionBlockItem;
import com.limbo2136.powerradar.item.RadarLinkBlockItem;
import com.limbo2136.powerradar.item.ShellAlarmBlockItem;
import com.limbo2136.powerradar.item.InterceptionFuzeItem;
import com.limbo2136.powerradar.item.InterceptionControllerBlockItem;
import com.limbo2136.powerradar.item.IncompleteOverviewModuleItem;
import com.limbo2136.powerradar.item.LinkerItem;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.item.OnboardComputerBlockItem;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerRadar.MOD_ID);

    public static final DeferredItem<BlockItem> RADAR_CONTROLLER = registerElectricalBlock(
            "radar_controller", ModBlocks.RADAR_CONTROLLER, Target.RADAR_CONTROLLER);
    public static final DeferredItem<BlockItem> AIR_RADAR_CONTROLLER = registerElectricalBlock(
            "air_radar_controller", ModBlocks.AIR_RADAR_CONTROLLER, Target.AIR_RADAR_CONTROLLER);
    public static final DeferredItem<BlockItem> SURFACE_RADAR_CONTROLLER = registerElectricalBlock(
            "surface_radar_controller", ModBlocks.SURFACE_RADAR_CONTROLLER, Target.SURFACE_RADAR_CONTROLLER);
    public static final DeferredItem<BlockItem> LOGIC_DOCK = registerElectricalBlock(
            "logic_dock", ModBlocks.LOGIC_DOCK, Target.LOGIC_DOCK);
    public static final DeferredItem<BlockItem> ONBOARD_COMPUTER = ITEMS.register(
            "onboard_computer",
            () -> new OnboardComputerBlockItem(ModBlocks.ONBOARD_COMPUTER.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> EW_SYSTEM = registerElectricalBlock(
            "ew_system", ModBlocks.EW_SYSTEM, Target.EW_SYSTEM);
    public static final DeferredItem<RadarFilterCardItem> TARGETING_CARD = registerCbcItem(() ->
            ITEMS.register(
                    "targeting_card",
                    () -> new RadarFilterCardItem(
                            RadarFilterCardItem.Kind.TARGETING, new Item.Properties().stacksTo(1))));
    public static final DeferredItem<RadarFilterCardItem> DISPLAY_CARD = ITEMS.register(
            "display_card",
            () -> new RadarFilterCardItem(
                    RadarFilterCardItem.Kind.DISPLAY, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RadarFilterCardItem> ALLOWLIST_CARD = registerCbcItem(() ->
            ITEMS.register(
                    "allowlist_card",
                    () -> new RadarFilterCardItem(
                            RadarFilterCardItem.Kind.ALLOWLIST, new Item.Properties().stacksTo(1))));
    public static final DeferredItem<BlockItem> RADAR_PANEL = registerElectricalBlock(
            "radar_panel", ModBlocks.RADAR_PANEL, Target.PHASED_ARRAY_PANEL);
    public static final DeferredItem<BlockItem> OVERVIEW_MODULE = registerElectricalBlock(
            "overview_module", ModBlocks.OVERVIEW_MODULE, Target.OVERVIEW_MODULE);
    public static final DeferredItem<IncompleteOverviewModuleItem> INCOMPLETE_OVERVIEW_MODULE = ITEMS.register(
            "incomplete_overview_module", () -> new IncompleteOverviewModuleItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> RADAR_MONITOR_CONTROLLER = registerElectricalBlock(
            "radar_monitor_controller", ModBlocks.RADAR_MONITOR_CONTROLLER, Target.MONITOR_CONTROLLER);
    public static final DeferredItem<BlockItem> RADAR_DISPLAY = registerElectricalBlock(
            "radar_display", ModBlocks.RADAR_DISPLAY, Target.RADAR_DISPLAY);
    public static final DeferredItem<RadarLinkBlockItem> RADAR_LINK = ITEMS.register(
            "radar_link", () -> new RadarLinkBlockItem(ModBlocks.RADAR_LINK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TARGET_CONTROLLER = registerCbcItem(() ->
            ITEMS.register(
                    "target_controller",
                    () -> new PowerRadarElectricalBlockItem(
                            ModBlocks.TARGET_CONTROLLER.get(), new Item.Properties(), Target.TARGET_CONTROLLER)));
    public static final DeferredItem<BlockItem> MECHANICAL_SIREN = ITEMS.register(
            "mechanical_siren",
            () -> new PowerRadarDescriptionBlockItem(
                    ModBlocks.MECHANICAL_SIREN.get(), new Item.Properties(), Target.MECHANICAL_SIREN));
    public static final DeferredItem<ShellAlarmBlockItem> SHELL_ALARM = registerCbcItem(() ->
            ITEMS.register(
                    "shell_alarm",
                    () -> new ShellAlarmBlockItem(ModBlocks.SHELL_ALARM.get(), new Item.Properties())));
    public static final DeferredItem<InterceptionControllerBlockItem> INTERCEPTION_CONTROLLER = registerCbcItem(() ->
            ITEMS.register(
                    "interception_controller",
                    () -> new InterceptionControllerBlockItem(
                            ModBlocks.INTERCEPTION_CONTROLLER.get(), new Item.Properties())));
    public static final DeferredItem<InterceptionFuzeItem> INTERCEPTION_FUZE = registerCbcItem(() ->
            ITEMS.register(
                    "interception_fuze",
                    () -> new InterceptionFuzeItem(new Item.Properties().stacksTo(64))));
    public static final DeferredItem<LinkerItem> LINKER = ITEMS.register(
            "linker", () -> new LinkerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MICROWAVE_EMITTER = ITEMS.register(
            "microwave_emitter", () -> new Item(new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    private static DeferredItem<BlockItem> registerElectricalBlock(
            String name,
            Supplier<? extends Block> block,
            Target tooltipTarget
    ) {
        return ITEMS.register(name, () -> new PowerRadarElectricalBlockItem(
                block.get(), new Item.Properties(), tooltipTarget));
    }

    @Nullable
    private static <T extends Item> DeferredItem<T> registerCbcItem(
            Supplier<DeferredItem<T>> registration
    ) {
        return CreateBigCannonsIntegration.isLoaded() ? registration.get() : null;
    }
}
