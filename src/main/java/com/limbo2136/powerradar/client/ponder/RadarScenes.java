package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.block.RadarDisplayBlock;
import com.limbo2136.powerradar.block.RadarDisplayFrameShape;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public class RadarScenes {

    public static void radarController(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);

        scene.title("radar_controller", "Building an Active Radar");
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(15);
        scene.overlay()
                .showText(80)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Radar Controller is the core unit of the structure.");
        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(180)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("There are three types of radar, each with a different vertical scanning range. A Basic Radar Controller can detect targets slightly above and below itself.");
        scene.idle(190);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().hideSection(util.select().position(2, 1, 1), Direction.EAST);
        scene.idle(15);
        ElementLink<WorldSectionElement> block1 = scene.world().showIndependentSection(
                util.select().position(1, 1, 1),
                Direction.EAST);
        scene.world().moveSection(block1, util.vector().of(1, 0, 0), 15);
        scene.idle(20);
        scene.overlay()
                .showText(130)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Surface Radar Controller can detect targets below itself, but not too deep underground.");
        scene.idle(140);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().hideIndependentSection(block1, Direction.WEST);
        scene.idle(15);
        ElementLink<WorldSectionElement> block2 = scene.world().showIndependentSection(
                util.select().position(3, 1, 1),
                Direction.WEST);
        scene.world().moveSection(block2, util.vector().of(-1, 0, 0), 15);
        scene.idle(20);
        scene.overlay()
                .showText(100)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Air Radar Controller can detect targets at much higher altitudes.");
        scene.idle(110);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().hideIndependentSection(block2, Direction.WEST);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 2, 1, 3), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(1, util.grid().at(2, 1, 1)),
                new InWorldNode(0, util.grid().at(2, 1, 3)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(2, 1, 1)),
                new InWorldNode(1, util.grid().at(2, 1, 3)));
        scene.overlay()
                .showText(60)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The radar requires power to operate.");
        scene.idle(70);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 4, 1), Direction.SOUTH);
        scene.overlay()
                .showText(120)
                .pointAt(util.vector().blockSurface(util.grid().at(2, 3, 1), Direction.NORTH))
                .placeNearTarget()
                .text("To detect targets, the radar needs either PESA Panels or Overview Modules.");
        scene.idle(130);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().hideSection(util.select().fromTo(1, 2, 1, 3, 4, 1), Direction.NORTH);
        scene.idle(15);
        ElementLink<WorldSectionElement> fromTo1 = scene.world()
                .showIndependentSection(util.select().fromTo(2, 2, 3, 2, 4, 3), Direction.NORTH);
        scene.world().moveSection(fromTo1, util.vector().of(0, 0, -2), 30);
        scene.idle(35);
        scene.overlay()
                .showText(190)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("Overview Modules provide 360-degree coverage, but consume more power than PESA Panels and are limited in number: by default, no more than five modules can be installed.");
        scene.idle(200);
    }

    public static void consumers(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("radar_network_consumers", "Connecting Consumers to the Radar Network");
        scene.world().modifyBlock(
                util.grid().at(2, 1, 1),
                state -> state.setValue(
                        RadarDisplayBlock.FRAME_SHAPE,
                        RadarDisplayFrameShape.SINGLE),
                false);
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().position(2, 1, 4), Direction.DOWN);
        scene.overlay()
                .showText(120)
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 4), Direction.NORTH))
                .placeNearTarget()
                .text("When a Radar Controller is placed, it creates a new radar network.");
        scene.idle(130);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showControls(
                        util.vector().topOf(2, 1, 4),
                        Pointing.DOWN,
                        60)
                .rightClick();
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "radarController",
                        util.select().position(2, 1, 4),
                        220);
        scene.overlay()
                .showText(220)
                .pointAt(util.vector().topOf(2, 1, 4))
                .placeNearTarget()
                .text("To connect a network consumer, such as a display, right-click the installed Radar Controller with the consumer's block item. Then place the tuned block where you want it.");
        scene.idle(60);
        scene.world().showSection(util.select().fromTo(2, 2, 4, 2, 4, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.SOUTH);
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "RadarDisplay",
                        util.select().position(2, 1, 1),
                        180);
        scene.idle(170);
        scene.overlay()
                .showText(160)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The display is now connected to the Radar Controller's network. Other compatible consumers are connected in the same way.");
        scene.idle(170);
    }

    public static void radarDisplay(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        RadarDisplayInstruction display = new RadarDisplayInstruction(builder);

        scene.title("radar_display", "Building and Powering a Radar Display");
        scene.idle(10);
        scene.world().modifyBlock(
                util.grid().at(2, 1, 1),
                state -> state.setValue(
                        RadarDisplayBlock.FRAME_SHAPE,
                        RadarDisplayFrameShape.SINGLE),
                false);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(15);
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "RadarScene",
                        util.select().position(2, 1, 1),
                        50);
        scene.rotateCameraY(-90);
        scene.idle(20);
        scene.overlay()
                .showText(100)
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.SOUTH))
                .placeNearTarget()
                .text("A display can be expanded by adding more display blocks.");
        scene.idle(20);
        scene.world().showSection(
                util.select().fromTo(1, 1, 1, 3, 3, 1).substract(util.select().position(2, 1, 1)),
                Direction.SOUTH);
        scene.idle(30);
        scene.world().modifyBlock(
                util.grid().at(2, 1, 1),
                state -> state,
                true);
        scene.world().restoreBlocks(util.select().position(2, 1, 1));
        scene.overlay()
                .showOutline(
                        PonderPalette.BLUE,
                        "RadarScene",
                        util.select().fromTo(1, 1, 1, 3, 3, 1),
                        50);
        scene.idle(60);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(140)
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.SOUTH))
                .placeNearTarget()
                .text("The display requires power. Its power consumption increases with the number of display blocks.");
        scene.idle(40);
        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(1, util.grid().at(2, 1, 1)),
                new InWorldNode(1, util.grid().at(2, 1, 3)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(2, 1, 1)),
                new InWorldNode(0, util.grid().at(2, 1, 3)));
        scene.idle(15);
        scene.rotateCameraY(90);
        scene.idle(20);
        display.showRadarPanelArea(util.select().fromTo(1, 1, 1, 3, 3, 1), Direction.NORTH, 0.8f, 0);
        display.showBlips(util.select().fromTo(1, 1, 1, 3, 3, 1), Direction.NORTH, 5,
                RadarTargetCategory.HOSTILE_MOB,
                RadarStructureType.PHASED_ARRAY, 0.8f, 0);
        scene.idle(60);
    }

    public static void logicDock(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);

        scene.title("logic_dock", "Using the Logic Dock");
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(15);
        scene.overlay()
                .showText(300)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Logic Dock has three card slots. Its cards control which targets appear on displays, which targets cannons can automatically engage, and which players or Sable structures are allowed or blocked. The dock requires power.");
        scene.idle(40);
        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(1, util.grid().at(2, 1, 1)),
                new InWorldNode(1, util.grid().at(2, 1, 3)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(2, 1, 1)),
                new InWorldNode(0, util.grid().at(2, 1, 3)));
        scene.idle(255);

        if (CreateBigCannonsIntegration.isLoaded()) {
            scene.addKeyframe();
            scene.idle(10);
            scene.overlay()
                    .showControls(
                            util.vector().topOf(2, 1, 1),
                            Pointing.DOWN,
                            100)
                    .rightClick()
                    .withItem(ModItems.TARGETING_CARD.get().asItem().getDefaultInstance());
            scene.idle(10);
            scene.world().modifyBlockEntityNBT(
                    util.select().position(2, 1, 1),
                    LogicDockBlockEntity.class,
                    nbt -> {
                        CompoundTag card = new CompoundTag();
                        card.putString("id", "power_radar:targeting_card");
                        card.putInt("count", 1);
                        nbt.put("Card0", card);
                        nbt.putByte("CardPresenceMask", (byte) 1);
                    });
            scene.overlay()
                    .showText(100)
                    .pointAt(util.vector().topOf(2, 1, 1))
                    .placeNearTarget()
                    .text("The Targeting Card selects which target categories connected cannons can automatically engage.");
            scene.idle(100);
            scene.world().modifyBlockEntityNBT(
                    util.select().position(2, 1, 1),
                    LogicDockBlockEntity.class,
                    nbt -> {
                        nbt.remove("Card0");
                        nbt.putByte("CardPresenceMask", (byte) 0);
                    });
            scene.idle(10);
        }

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showControls(
                        util.vector().topOf(2, 1, 1),
                        Pointing.DOWN,
                        100)
                .rightClick()
                .withItem(ModItems.DISPLAY_CARD.get().asItem().getDefaultInstance());
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(
                util.select().position(2, 1, 1),
                LogicDockBlockEntity.class,
                nbt -> {
                    CompoundTag card = new CompoundTag();
                    card.putString("id", "power_radar:display_card");
                    card.putInt("count", 1);
                    nbt.put("Card1", card);
                    nbt.putByte("CardPresenceMask", (byte) 2);
                });
        scene.overlay()
                .showText(100)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Display Card selects which target categories appear on connected displays.");
        scene.idle(100);
        scene.world().modifyBlockEntityNBT(
                util.select().position(2, 1, 1),
                LogicDockBlockEntity.class,
                nbt -> {
                    nbt.remove("Card1");
                    nbt.putByte("CardPresenceMask", (byte) 0);
                });
        scene.idle(10);

        if (CreateBigCannonsIntegration.isLoaded()) {
            scene.addKeyframe();
            scene.idle(10);
            scene.overlay()
                    .showControls(
                            util.vector().topOf(2, 1, 1),
                            Pointing.DOWN,
                            100)
                    .rightClick()
                    .withItem(ModItems.ALLOWLIST_CARD.get().asItem().getDefaultInstance());
            scene.idle(10);
            scene.world().modifyBlockEntityNBT(
                    util.select().position(2, 1, 1),
                    LogicDockBlockEntity.class,
                    nbt -> {
                        CompoundTag card = new CompoundTag();
                        card.putString("id", "power_radar:allowlist_card");
                        card.putInt("count", 1);
                        nbt.put("Card2", card);
                        nbt.putByte("CardPresenceMask", (byte) 4);
                    });
            scene.overlay()
                    .showText(100)
                    .pointAt(util.vector().topOf(2, 1, 1))
                    .placeNearTarget()
                    .text("The Allowlist Card lets you add players and Sable structures to an allowlist or blacklist.");
            scene.idle(110);
        }
    }
}
