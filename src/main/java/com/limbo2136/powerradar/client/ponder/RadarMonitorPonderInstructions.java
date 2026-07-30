package com.limbo2136.powerradar.client.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;

public class RadarMonitorPonderInstructions {
    final SceneBuilder builder;

    public RadarMonitorPonderInstructions(SceneBuilder builder) {
        this.builder = builder;
    }

    public void showOverviewModuleArea(
        BlockPos firstPos,
        BlockPos secondPos,
        Direction facing,
        float size
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.overview(firstPos, secondPos, facing, size);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showRadarPanelArea(
        BlockPos firstPos,
        BlockPos secondPos,
        Direction facing,
        float size,
        float rotationDegrees
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.radarPanel(
                    firstPos, secondPos, facing, size, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showBlips(
        BlockPos firstPos,
        BlockPos secondPos,
        Direction facing,
        int count,
        RadarTargetCategory category,
        RadarStructureType radarType,
        float areaSize,
        float rotationDegrees
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.blips(
                    firstPos, secondPos, facing, count, category, radarType, areaSize, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showOnboardOverviewModuleArea(
        BlockPos computerPos,
        Direction facing,
        float size
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.onboardOverview(computerPos, facing, size);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }

    public void showOnboardRadarPanelArea(
        BlockPos computerPos,
        Direction facing,
        float size,
        float rotationDegrees
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.onboardRadarPanel(
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
        float rotationDegrees
    ) {
        builder.addInstruction(scene -> {
            RadarMonitorElement element = RadarMonitorElement.onboardBlips(
                    computerPos, facing, count, category, radarType, areaSize, rotationDegrees);
            element.forceApplyFade(1);
            scene.addElement(element);
        });
    }
}
