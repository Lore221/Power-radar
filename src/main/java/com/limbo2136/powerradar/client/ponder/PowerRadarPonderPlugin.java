package com.limbo2136.powerradar.client.ponder;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModItems;

import java.util.ArrayList;
import java.util.List;
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
        helper.forComponents(radarComponents())
                .addStoryBoard("radar_scene", RadarScenes::radarController);

        helper.forComponents(radarComponentsWithCbc(
                ModBlocks.RADAR_DISPLAY.getId(),
                ModBlocks.LOGIC_DOCK.getId()))
                .addStoryBoard("display_scene", RadarScenes::consumers);

        helper.forComponents(radarComponents(ModBlocks.RADAR_DISPLAY.getId()))
                .addStoryBoard("display_scene", RadarScenes::radarDisplay);

        helper.forComponents(radarComponentsWithCbc(
                ModBlocks.RADAR_DISPLAY.getId(),
                ModBlocks.LOGIC_DOCK.getId(),
                ModItems.DISPLAY_CARD.getId()))
                .addStoryBoard("dock_scene", RadarScenes::logicDock);

        if (CreateBigCannonsIntegration.isLoaded()) {
            helper.forComponents(
                    ModBlocks.RADAR_DISPLAY.getId(),
                    ModBlocks.LOGIC_DOCK.getId(),
                    ModBlocks.TARGET_CONTROLLER.getId(),
                    ModItems.TARGETING_CARD.getId())
                    .addStoryBoard("targeting_scene", AttackScenes::targetController);

            helper.forComponents(radarComponents(
                    ModBlocks.SHELL_ALARM.getId(),
                    ModBlocks.INTERCEPTION_CONTROLLER.getId(),
                    ModItems.INTERCEPTION_FUZE.getId()))
                    .addStoryBoard("alarm_scene", DefenceScenes::shellAlarm);

            helper.forComponents(
                    ModBlocks.SHELL_ALARM.getId(),
                    ModBlocks.INTERCEPTION_CONTROLLER.getId(),
                    ModItems.INTERCEPTION_FUZE.getId())
                    .addStoryBoard("interception_scene", DefenceScenes::interception);
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }

    private static ResourceLocation[] radarComponents(ResourceLocation... extraComponents) {
        List<ResourceLocation> components = new ArrayList<>(List.of(
                ModBlocks.RADAR_CONTROLLER.getId(),
                ModBlocks.AIR_RADAR_CONTROLLER.getId(),
                ModBlocks.RADAR_PANEL.getId(),
                ModBlocks.OVERVIEW_MODULE.getId()));
        if (SableRadarIntegration.isAeronauticsLoaded()) {
            components.add(ModBlocks.SURFACE_RADAR_CONTROLLER.getId());
        }
        components.addAll(List.of(extraComponents));
        return components.toArray(ResourceLocation[]::new);
    }

    private static ResourceLocation[] radarComponentsWithCbc(ResourceLocation... extraComponents) {
        List<ResourceLocation> components = new ArrayList<>(List.of(radarComponents()));
        if (CreateBigCannonsIntegration.isLoaded()) {
            components.add(ModBlocks.TARGET_CONTROLLER.getId());
            components.add(ModItems.TARGETING_CARD.getId());
            components.add(ModItems.ALLOWLIST_CARD.getId());
        }
        components.addAll(List.of(extraComponents));
        return components.toArray(ResourceLocation[]::new);
    }
}
