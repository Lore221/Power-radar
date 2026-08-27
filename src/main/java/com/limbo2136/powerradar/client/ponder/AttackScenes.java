package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import rbasamoyai.createbigcannons.ponder.CBCAnimateBlockEntityInstruction;

public class AttackScenes {

    public static void targetController(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        RadarDisplayInstruction display = new RadarDisplayInstruction(builder);

        scene.title("target_controller", "Using a Target Controller");
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().layer(1).substract(util.select().fromTo(6, 1, 1, 8, 1, 1))
                .substract(util.select().position(1, 1, 1)).substract(util.select().position(1, 1, 4)), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(6, 1, 1, 8, 3, 1), Direction.SOUTH);
        scene.idle(15);
        scene.world().modifyBlockEntityNBT(
                util.select().position(4, 1, 1),
                LogicDockBlockEntity.class,
                nbt -> {
                    CompoundTag card = new CompoundTag();
                    card.putString("id", "power_radar:targeting_card");
                    card.putInt("count", 1);
                    nbt.put("Card0", card);
                    nbt.putByte("CardPresenceMask", (byte) 1);
                });
        connects.createConnection(
                new InWorldNode(0, util.grid().at(4, 1, 1)),
                new InWorldNode(0, util.grid().at(4, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(4, 1, 1)),
                new InWorldNode(1, util.grid().at(4, 1, 4)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(7, 1, 1)),
                new InWorldNode(1, util.grid().at(7, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(7, 1, 1)),
                new InWorldNode(0, util.grid().at(7, 1, 4)));
        display.showOverviewModuleArea(
                util.select().fromTo(6, 1, 1, 8, 3, 1),
                Direction.NORTH,
                0.8f);
        display.showBlips(
                util.select().fromTo(6, 1, 1, 8, 3, 1),
                Direction.NORTH,
                10,
                RadarTargetCategory.PASSIVE_MOB,
                RadarStructureType.OVERVIEW,
                0.8f,
                0);
        scene.idle(15);
        scene.overlay()
                .showText(200)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Target Controller uses a connected Display or Logic Dock to aim the cannon at a selected target or at targets matching the automatic targeting settings.");
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "display",
                        util.select().fromTo(6, 1, 1, 8, 3, 1),
                        200);
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "logic Dock",
                        util.select().position(4, 1, 1),
                        200);
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "target_controller",
                        util.select().position(1, 1, 2),
                        200);
        scene.idle(210);

        scene.addKeyframe();
        scene.idle(10);
        scene.rotateCameraY(-90);
        scene.idle(20);
        scene.overlay()
                .showText(120)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Target Controller must be connected to a fully assembled cannon.");
        scene.idle(20);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(110);
        ElementLink<WorldSectionElement> bigCannon = scene.world().showIndependentSection(
                util.select().fromTo(1, 4, 0, 1, 4, 4), Direction.DOWN);
        scene.world().moveSection(bigCannon, util.vector().of(0, -1, 0), 15);
        scene.idle(15);
        scene.overlay()
                .showText(80)
                .pointAt(util.vector().topOf(1, 3, 2))
                .placeNearTarget()
                .text("The Target Controller can also control Big Cannons.");
        scene.idle(95);
        scene.world().hideIndependentSection(bigCannon, Direction.UP);
        scene.idle(15);
        ElementLink<WorldSectionElement> autoCannon = scene.world().showIndependentSection(
                util.select().fromTo(1, 3, 0, 1, 3, 4), Direction.UP);
        scene.idle(15);
        scene.overlay()
                .showText(80)
                .pointAt(util.vector().topOf(1, 3, 2))
                .placeNearTarget()
                .text("Autocannons can also be controlled by the Target Controller.");
        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(80)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Target Controller also requires power to operate.");
        scene.world().showSection(util.select().position(1, 1, 4), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(0, util.grid().at(1, 1, 2)),
                new InWorldNode(0, util.grid().at(1, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(1, 1, 2)),
                new InWorldNode(1, util.grid().at(1, 1, 4)));
        scene.idle(75);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(180)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Target Controller has two trajectory modes: Flat and High. Hold right-click on its side panel to switch between them.");
        scene.overlay()
                .showControls(
                        util.vector().blockSurface(util.grid().at(1, 1, 2), Direction.EAST),
                        Pointing.DOWN,
                        180)
                .rightClick();
        scene.idle(190);

        scene.addKeyframe();
        scene.idle(10);
        scene.rotateCameraY(90);
        scene.idle(20);
        ElementLink<ParrotElement> target = scene.special().createBirb(
                util.vector().of(7.5, 3, 7.5),
                ParrotPose.FlappyPose::new);
        scene.special().moveParrot(target, util.vector().of(0, 0.2, 0), 10);
        scene.idle(10);
        scene.overlay()
                .showText(140)
                .pointAt(util.vector().topOf(1, 3, 2))
                .placeNearTarget()
                .text("Once a target has been selected, the Target Controller aims the cannon along the selected trajectory.");
        scene.special().moveParrot(target, util.vector().of(0, -0.2, 0), 10);
        scene.idle(10);
        scene.special().moveParrot(target, util.vector().of(0, 0.2, 0), 10);
        scene.idle(10);
        scene.world().configureCenterOfRotation(autoCannon, util.vector().centerOf(1, 3, 1));
        scene.world().rotateSection(autoCannon, 0, 45, 0, 20);
        scene.addInstruction(
                CBCAnimateBlockEntityInstruction.cannonMountYaw(util.grid().at(1, 1, 1), -45, 20));
        scene.special().moveParrot(target, util.vector().of(0, -0.2, 0), 10);
        scene.special().rotateParrot(target, 0, 90, 0, 10);
        scene.idle(10);
        scene.special().moveParrot(target, util.vector().of(0, 0.2, 0), 10);
        scene.idle(10);
        scene.special().moveParrot(target, util.vector().of(-6, 0, 0), 100);
        scene.world().rotateSection(autoCannon, 0, -45, 0, 100);
        scene.addInstruction(
                CBCAnimateBlockEntityInstruction.cannonMountYaw(util.grid().at(1, 1, 1), 45, 100));
        scene.idle(50);
    }
}
