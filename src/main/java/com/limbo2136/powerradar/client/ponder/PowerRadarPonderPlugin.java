package com.limbo2136.powerradar.client.ponder;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModItems;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class PowerRadarPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return PowerRadar.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        helper.forComponents(
            ModBlocks.RADAR_CONTROLLER.getId(),
            ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
            ModBlocks.AIR_RADAR_CONTROLLER.getId(),
            ModBlocks.RADAR_PANEL.getId(),
            ModBlocks.OVERVIEW_MODULE.getId()
        )
        .addStoryBoard("radar_basics", RadarScenes::radarBasics);

        helper.forComponents(
            ModBlocks.RADAR_CONTROLLER.getId(),
            ModBlocks.AIR_RADAR_CONTROLLER.getId(),
            ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
            ModBlocks.RADAR_LINK.getId(),
            ModBlocks.RADAR_MONITOR_CONTROLLER.getId(),
            ModBlocks.RADAR_DISPLAY.getId(),
            ModBlocks.RADAR_PANEL.getId(),
            ModBlocks.OVERVIEW_MODULE.getId()
        )
        .addStoryBoard("link_basic", RadarScenes::linkBasic);

        helper.forComponents(
            ModBlocks.RADAR_CONTROLLER.getId(),
            ModBlocks.AIR_RADAR_CONTROLLER.getId(),
            ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
            ModBlocks.LOGIC_DOCK.getId(),
            ModBlocks.TARGET_CONTROLLER.getId(),
            ModBlocks.RADAR_PANEL.getId(),
            ModBlocks.OVERVIEW_MODULE.getId(),
            ModItems.DISPLAY_CARD.getId(),
            ModItems.TARGETING_CARD.getId(),
            ModItems.ALLOWLIST_CARD.getId()
        )
        .addStoryBoard("logic_dock", RadarScenes::logicDockBasic);

        helper.forComponents(
            ModBlocks.TARGET_CONTROLLER.getId(),
            ModBlocks.LOGIC_DOCK.getId(),
            ModBlocks.RADAR_MONITOR_CONTROLLER.getId(),
            ModBlocks.RADAR_DISPLAY.getId(),
            ModItems.TARGETING_CARD.getId()
        )
        .addStoryBoard("targeting", TargetingScenes::targetingBasic);

        helper.forComponents(
            ModBlocks.SHELL_ALARM.getId(),
            ModBlocks.INTERCEPTION_CONTROLLER.getId(),
            ModItems.INTERCEPTION_FUZE.getId()
        )
        .addStoryBoard("interception", DefenceScenes::ShellAlarm);

        helper.forComponents(
            ModBlocks.ONBOARD_COMPUTER.getId()
        )
        .addStoryBoard("onboard", DefenceScenes::OnBoardComputer);

        helper.forComponents(
            ModBlocks.SHELL_ALARM.getId(),
            ModBlocks.INTERCEPTION_CONTROLLER.getId(),
            ModItems.INTERCEPTION_FUZE.getId(),
            ModBlocks.ONBOARD_COMPUTER.getId()
        )
        .addStoryBoard("interception", DefenceScenes::InterceptController);
    }

    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation radars = ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radars");
        helper.registerTag(radars)
            .title("Radars")
            .description("Components involved in target detection and navigation")
            .item(ModBlocks.RADAR_PANEL.get())
            .addToIndex()
            .register();
        helper.addToTag(radars)
            .add(ModBlocks.RADAR_CONTROLLER.getId())
            .add(ModBlocks.AIR_RADAR_CONTROLLER.getId())
            .add(ModBlocks.SURFACE_RADAR_CONTROLLER.getId())
            .add(ModBlocks.LOGIC_DOCK.getId())
            .add(ModBlocks.ONBOARD_COMPUTER.getId())
            .add(ModBlocks.RADAR_PANEL.getId())
            .add(ModBlocks.OVERVIEW_MODULE.getId())
            .add(ModBlocks.RADAR_MONITOR_CONTROLLER.getId())
            .add(ModBlocks.RADAR_DISPLAY.getId())
            .add(ModBlocks.RADAR_LINK.getId())
            .add(ModItems.DISPLAY_CARD.getId())
            .add(ModItems.ALLOWLIST_CARD.getId())
            .add(ResourceLocation.fromNamespaceAndPath("minecraft", "compass"))
            .add(ResourceLocation.fromNamespaceAndPath("minecraft", "clock"))
            .add(ResourceLocation.fromNamespaceAndPath("simulated", "altitude_sensor"))
            .add(ResourceLocation.fromNamespaceAndPath("simulated", "velocity_sensor"))
            .add(ResourceLocation.fromNamespaceAndPath("simulated", "gimbal_sensor"));

        ResourceLocation targeting = ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "targeting");
        helper.registerTag(targeting)
            .title("Target system")
            .description("Components involved in the destruction of targets")
            .item(ModBlocks.TARGET_CONTROLLER.get())
            .addToIndex()
            .register();
        helper.addToTag(targeting)
            .add(ModBlocks.TARGET_CONTROLLER.getId())
            .add(ModBlocks.RADAR_LINK.getId())
            .add(ModItems.TARGETING_CARD.getId());

        ResourceLocation interception = ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "interception");
        helper.registerTag(interception)
            .title("Defense system")
            .description("Components involved in defense")
            .item(ModBlocks.INTERCEPTION_CONTROLLER.get())
            .addToIndex()
            .register();
        helper.addToTag(interception)
            .add(ModBlocks.ONBOARD_COMPUTER.getId())
            .add(ModBlocks.RADAR_LINK.getId())
            .add(ModBlocks.MECHANICAL_SIREN.getId())
            .add(ModBlocks.SHELL_ALARM.getId())
            .add(ModBlocks.INTERCEPTION_CONTROLLER.getId())
            .add(ModItems.INTERCEPTION_FUZE.getId());
    }
}
