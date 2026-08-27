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
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId())
                .addStoryBoard("radar_scene", RadarScenes::radarController);

        helper.forComponents(
                ModBlocks.RADAR_CONTROLLER.getId(),
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId(),
                ModBlocks.RADAR_DISPLAY.getId(),
                ModBlocks.LOGIC_DOCK.getId(),
                ModBlocks.TARGET_CONTROLLER.getId())
                .addStoryBoard("display_scene", RadarScenes::consumers);

        helper.forComponents(
                ModBlocks.RADAR_CONTROLLER.getId(),
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId(),
                ModBlocks.RADAR_DISPLAY.getId())
                .addStoryBoard("display_scene", RadarScenes::radarDisplay);

        helper.forComponents(
                ModBlocks.RADAR_CONTROLLER.getId(),
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId(),
                ModBlocks.RADAR_DISPLAY.getId(),
                ModBlocks.LOGIC_DOCK.getId(),
                ModBlocks.TARGET_CONTROLLER.getId(),
                ModItems.ALLOWLIST_CARD.getId(),
                ModItems.DISPLAY_CARD.getId(),
                ModItems.TARGETING_CARD.getId())
                .addStoryBoard("dock_scene", RadarScenes::logicDock);

        helper.forComponents(
                ModBlocks.RADAR_DISPLAY.getId(),
                ModBlocks.LOGIC_DOCK.getId(),
                ModBlocks.TARGET_CONTROLLER.getId(),
                ModItems.TARGETING_CARD.getId())
                .addStoryBoard("targeting_scene", AttackScenes::targetController);

        helper.forComponents(
                ModBlocks.RADAR_CONTROLLER.getId(),
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.SURFACE_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId(),
                ModBlocks.SHELL_ALARM.getId(),
                ModBlocks.INTERCEPTION_CONTROLLER.getId(),
                ModItems.INTERCEPTION_FUZE.getId())
                .addStoryBoard("alarm_scene", DefenceScenes::shellAlarm);

        helper.forComponents(
                ModBlocks.SHELL_ALARM.getId(),
                ModBlocks.INTERCEPTION_CONTROLLER.getId(),
                ModItems.INTERCEPTION_FUZE.getId())
                .addStoryBoard("interception_scene", DefenceScenes::interception);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }
}
