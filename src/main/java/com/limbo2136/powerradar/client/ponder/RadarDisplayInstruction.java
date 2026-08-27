package com.limbo2136.powerradar.client.ponder;

import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class RadarDisplayInstruction {
    private final SceneBuilder builder;

    public RadarDisplayInstruction(SceneBuilder builder) {
        this.builder = builder;
    }

    public void showOverviewModuleArea(
            Selection monitor,
            Direction facing,
            float size) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.overview(monitor, facing, size);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showRadarPanelArea(
            Selection monitor,
            Direction facing,
            float size,
            float rotationDegrees) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.radarPanel(
                    monitor, facing, size, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showBlips(
            Selection monitor,
            Direction facing,
            int count,
            RadarTargetCategory category,
            RadarStructureType radarType,
            float areaSize,
            float rotationDegrees) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.blips(
                    monitor, facing, count, category, radarType, areaSize, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showOnboardOverviewModuleArea(
            BlockPos computerPos,
            Direction facing,
            float size) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.onboardOverview(computerPos, facing, size);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showOnboardRadarPanelArea(
            BlockPos computerPos,
            Direction facing,
            float size,
            float rotationDegrees) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.onboardRadarPanel(
                    computerPos, facing, size, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showOnboardBlips(
            BlockPos computerPos,
            Direction facing,
            int count,
            RadarTargetCategory category,
            RadarStructureType radarType,
            float areaSize,
            float rotationDegrees) {
        builder.addInstruction(scene -> {
            RadarDisplayElement element = RadarDisplayElement.onboardBlips(
                    computerPos, facing, count, category, radarType, areaSize, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }
}
