package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.ponder.CBCAnimateBlockEntityInstruction;

public class TargetingScenes {

    public static void targetingBasic(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        RadarMonitorPonderInstructions display = new RadarMonitorPonderInstructions(builder);
        scene.title("targeting_basic", "Target controller");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 8, 2, 8), Direction.DOWN);
        scene.world().modifyBlockEntityNBT(util.select().position(4, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                CompoundTag card = new CompoundTag();
                card.putString("id", "power_radar:targeting_card");
                card.putInt("count", 1);

                nbt.put("Card0", card);
                nbt.putByte("CardPresenceMask", (byte) 1);
            }
        );
        ElementLink<WorldSectionElement> cannon =
            scene.world().showIndependentSection(
                util.select().fromTo(1, 3, 0, 1, 3, 3), Direction.DOWN
            );
        scene.world().configureCenterOfRotation(cannon, util.vector().centerOf(1, 3, 1));
        scene.idle(10);
        connects.createConnection(
            new InWorldNode(1, 1, 1, 4),
            new InWorldNode(0, 1, 1, 2)
        );
        connects.createConnection(
            new InWorldNode(0, 1, 1, 4),
            new InWorldNode(1, 1, 1, 2)
        );
        connects.createConnection(
            new InWorldNode(1, 4, 1, 4),
            new InWorldNode(0, 4, 1, 1)
        );
        connects.createConnection(
            new InWorldNode(0, 4, 1, 4),
            new InWorldNode(1, 4, 1, 1)
        );
        connects.createConnection(
            new InWorldNode(1, 7, 1, 4),
            new InWorldNode(0, 7, 1, 1)
        );
        connects.createConnection(
            new InWorldNode(0, 7, 1, 4),
            new InWorldNode(1, 7, 1, 1)
        );
        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 2, 0), Direction.SOUTH);
        scene.world().modifyBlock(
            util.grid().at(1, 1, 0),
            state -> state.setValue(LeverBlock.POWERED, true),
            false
        );
        display.showOverviewModuleArea(
            util.grid().at(6, 1, 0),
            util.grid().at(7, 2, 0),
            Direction.NORTH,
            0.8f
        );
        scene.idle(20);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().blockSurface(util.grid().at(1, 1, 2), Direction.WEST))
            .placeNearTarget()
            .text("The Target Controller allows cannons to be automatically aimed in manual and automatic modes.");
        scene.idle(100);

        scene.addKeyframe();
        scene.idle(10);
        scene.addInstruction(new CreateZombieInstruction(
            10,
            Direction.DOWN,
            new ZombiePonderElement(new Vec3(7.5, 1.0, 7.5))
        ));
        scene.idle(10);
        display.showBlips(
            util.grid().at(6, 1, 0),
            util.grid().at(7, 2, 0),
            Direction.NORTH,
            1,
            RadarTargetCategory.HOSTILE_MOB,
            RadarStructureType.OVERVIEW,
            0.8f,
            0
        );
        scene.idle(20);
        scene.overlay()
            .showText(140)
            .pointAt(util.vector().blockSurface(util.grid().at(7, 1, 7), Direction.WEST))
            .placeNearTarget()
            .text("Auto-aiming is enabled for targets selected on the display or for targets from selected categories in the targeting card.");
        scene.idle(40);
        scene.world().rotateSection(cannon, 0.0, 45.0, 0.0, 20);
        scene.addInstruction(
            CBCAnimateBlockEntityInstruction.cannonMountYaw(util.grid().at(1, 1, 1), -45.0f, 20)
        );
        scene.addInstruction(
            CBCAnimateBlockEntityInstruction.cannonMountPitch(util.grid().at(1, 1, 1), -15.0f, 20)
        );
        scene.idle(120);
    }
}
