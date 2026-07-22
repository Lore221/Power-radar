package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.item.PowerRadarElectricalBlockItem;
import com.limbo2136.powerradar.item.RadarLinkBlockItem;
import com.limbo2136.powerradar.item.ShellAlarmBlockItem;
import com.limbo2136.powerradar.item.InterceptionFuzeItem;
import com.limbo2136.powerradar.item.InterceptionControllerBlockItem;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.item.NameCardItem;
import com.limbo2136.powerradar.item.OnboardComputerBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerRadar.MOD_ID);

    public static final DeferredItem<BlockItem> RADAR_CONTROLLER = ITEMS.register(
            "radar_controller",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.RADAR_CONTROLLER.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.RADAR_CONTROLLER));
    public static final DeferredItem<BlockItem> AIR_RADAR_CONTROLLER = ITEMS.register(
            "air_radar_controller",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.AIR_RADAR_CONTROLLER.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.RADAR_CONTROLLER));
    public static final DeferredItem<BlockItem> SURFACE_RADAR_CONTROLLER = ITEMS.register(
            "surface_radar_controller",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.SURFACE_RADAR_CONTROLLER.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.RADAR_CONTROLLER));
    public static final DeferredItem<BlockItem> COMPUTING_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.COMPUTING_BLOCK);
    public static final DeferredItem<BlockItem> ONBOARD_COMPUTER = ITEMS.register(
            "onboard_computer", () -> new OnboardComputerBlockItem(ModBlocks.ONBOARD_COMPUTER.get(), new Item.Properties()));
    public static final DeferredItem<NameCardItem> NAME_CARD = ITEMS.register(
            "name_card", () -> new NameCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RadarFilterCardItem> TARGETING_CARD = ITEMS.register(
            "targeting_card", () -> new RadarFilterCardItem(RadarFilterCardItem.Kind.TARGETING,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RadarFilterCardItem> DISPLAY_CARD = ITEMS.register(
            "display_card", () -> new RadarFilterCardItem(RadarFilterCardItem.Kind.DISPLAY,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RadarFilterCardItem> ALLOWLIST_CARD = ITEMS.register(
            "allowlist_card", () -> new RadarFilterCardItem(RadarFilterCardItem.Kind.ALLOWLIST,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> RADAR_PANEL = ITEMS.register(
            "radar_panel",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.RADAR_PANEL.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.BASIC_RADAR_PANEL));
    public static final DeferredItem<BlockItem> OVERVIEW_MODULE = ITEMS.registerSimpleBlockItem(ModBlocks.OVERVIEW_MODULE);
    public static final DeferredItem<BlockItem> RADAR_MONITOR_CONTROLLER = ITEMS.register(
            "radar_monitor_controller",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.RADAR_MONITOR_CONTROLLER.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.MONITOR_CONTROLLER));
    public static final DeferredItem<BlockItem> RADAR_DISPLAY = ITEMS.register(
            "radar_display",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.RADAR_DISPLAY.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.RADAR_DISPLAY));
    public static final DeferredItem<RadarLinkBlockItem> RADAR_LINK = ITEMS.register(
            "radar_link",
            () -> new RadarLinkBlockItem(ModBlocks.RADAR_LINK.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> TARGET_CONTROLLER = ITEMS.register(
            "target_controller",
            () -> new PowerRadarElectricalBlockItem(ModBlocks.TARGET_CONTROLLER.get(), new Item.Properties(),
                    PowerRadarElectricalBlockItem.TooltipKind.TARGET_CONTROLLER));
    public static final DeferredItem<BlockItem> MECHANICAL_SIREN =
            ITEMS.registerSimpleBlockItem(ModBlocks.MECHANICAL_SIREN);
    public static final DeferredItem<ShellAlarmBlockItem> SHELL_ALARM = ITEMS.register(
            "shell_alarm",
            () -> new ShellAlarmBlockItem(ModBlocks.SHELL_ALARM.get(), new Item.Properties()));
    public static final DeferredItem<InterceptionControllerBlockItem> INTERCEPTION_CONTROLLER = ITEMS.register(
            "interception_controller",
            () -> new InterceptionControllerBlockItem(
                    ModBlocks.INTERCEPTION_CONTROLLER.get(),
                    new Item.Properties()));
    public static final DeferredItem<InterceptionFuzeItem> INTERCEPTION_FUZE = ITEMS.register(
            "interception_fuze",
            () -> new InterceptionFuzeItem(new Item.Properties().stacksTo(64)));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
