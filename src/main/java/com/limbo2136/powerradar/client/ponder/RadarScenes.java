package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.registry.ModBlocks;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public class RadarScenes {

    public static void radarBasics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);

        scene.title("radar_basics", "Build a radar");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(20);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(3, 1, 2))
            .placeNearTarget()
            .text("Radar controller is the main block of the structure");
        connects.createConnection(
            new InWorldNode(1, util.grid().at(1, 1, 2)),
            new InWorldNode(0, util.grid().at(3, 1, 2))
        );
        connects.createConnection(
            new InWorldNode(0, util.grid().at(1, 1, 2)),
            new InWorldNode(1, util.grid().at(3, 1, 2))
        );
        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);
        scene.rotateCameraY(90);
        scene.idle(20);
        for (int y = 2; y <= 3; y++) {
            for (int z = 1; z <=3; z++) {
                scene.world().showSection(util.select().position(3, y, z), Direction.DOWN);
                scene.idle(2);
            }
        }
        scene.idle(10);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().blockSurface(util.grid().at(3, 2, 2), Direction.EAST))
            .placeNearTarget()
            .text("Panels allow the controller to see targets");
        scene.idle(80);
    }

    public static void linkBasic(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        RadarMonitorPonderInstructions display = new RadarMonitorPonderInstructions(builder);

        scene.title("link_basics", "Linking radars");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        ElementLink<WorldSectionElement> firstRadarLink =
            scene.world().showIndependentSection(
                util.select().position(1, 2, 1),
                Direction.DOWN
            );
        scene.world().moveSection(
            firstRadarLink,
            util.vector().of(0, -1, 0),
            0
        );
        scene.idle(5);
        scene.overlay()
            .showText(60)
            .pointAt(util.vector().blockSurface(
                util.grid().at(1, 1, 1),
                Direction.NORTH
            ))
            .placeNearTarget()
            .text("When placed, radar link creates a new network");
        scene.idle(80);
        scene.overlay()
            .showControls(
                util.vector().topOf(1, 1, 1),
                Pointing.DOWN,
                60
            )
            .rightClick()
            .withItem(ModBlocks.RADAR_LINK.get().asItem().getDefaultInstance());
        scene.overlay()
            .showOutline(
                PonderPalette.BLUE,
                firstRadarLink,
                util.select().position(1, 1, 1),
                100
            );
        scene.idle(20);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().blockSurface(
                util.grid().at(1, 1, 1),
                Direction.NORTH))
            .placeNearTarget()
            .text("Right-click on an existing link to link them");
        scene.idle(20);
        ElementLink<WorldSectionElement> SecondRadarLink =
            scene.world().showIndependentSection(
                util.select().position(4, 1 , 5),
                Direction.DOWN
            );
        scene.world().rotateSection(
            SecondRadarLink,
            0, 0, 270, 0
        );
        scene.overlay()
            .showOutline(
                PonderPalette.BLUE,
                SecondRadarLink,
                util.select().position(4, 1, 5),
                60
            );
        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().moveSection(
            firstRadarLink,
            util.vector().of(0, 1, 0),
            20
        );
        scene.world().rotateSection(
            SecondRadarLink,
            0, 0, 90,
            20
        );
        scene.idle(10);
        scene.world().showSection(
            util.select().fromTo(1, 1, 1, 5, 1, 5)
            .substract(util.select().position(4,1,5)),
            Direction.UP
        );
        scene.idle(10);
        connects.createConnection(
            new InWorldNode(1, util.grid().at(1, 1, 3)),
            new InWorldNode(0, util.grid().at(1, 1, 1))
        );
        connects.createConnection(
            new InWorldNode(0, util.grid().at(1, 1, 3)),
            new InWorldNode(1, util.grid().at(1, 1, 1))
        );
        connects.createConnection(
            new InWorldNode(1, util.grid().at(5, 1, 3)),
            new InWorldNode(0, util.grid().at(5, 1, 5))
        );
        connects.createConnection(
            new InWorldNode(0, util.grid().at(5, 1, 3)),
            new InWorldNode(1, util.grid().at(5, 1, 5))
        );
        scene.idle(20);
        scene.world().showSection(
            util.select().fromTo(0, 1, 0, 2, 3, 0),
            Direction.SOUTH
        );
        scene.world().showSection(
            util.select().position(5, 2, 5),
            Direction.DOWN
        );
        scene.idle(20);
        scene.overlay()
            .showText(60)
            .pointAt(util.vector().blockSurface(
                util.grid().at(1, 2, 0),
                Direction.NORTH))
            .placeNearTarget()
            .text("If the connection is successful, the radar data will be visible on the display.");
        display.showOverviewModuleArea(
            util.grid().at(0, 1, 0),
            util.grid().at(2, 3, 0),
            Direction.NORTH,
            0.8f);
        display.showBlips(
            util.grid().at(0, 1, 0),
            util.grid().at(2, 3, 0),
            Direction.NORTH,
            5,
            RadarTargetCategory.HOSTILE_MOB,
            RadarStructureType.OVERVIEW,
            0.8f,
            0.0f);
        scene.idle(80);

        scene.addKeyframe();
        scene.idle(10);
        scene.rotateCameraY(-90);
        scene.idle(20);
        scene.overlay()
            .showControls(
                util.vector().topOf(1, 2, 1),
                Pointing.DOWN,
                80
            )
            .rightClick()
            .withItem(Items.COMPASS.getDefaultInstance());
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(1, 1, 1))
            .placeNearTarget()
            .text("The compass can be linked to the network and will point to the selected target.");
        scene.idle(100);
    }

    public static void logicDockBasic(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        scene.title("logic_dock_basics", "Logic dock connection");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        connects.createConnection(
            new InWorldNode(1, util.grid().at(2, 1, 3)),
            new InWorldNode(0, util.grid().at(2, 1, 1))
        );
        connects.createConnection(
            new InWorldNode(0, util.grid().at(2, 1, 3)),
            new InWorldNode(1, util.grid().at(2, 1, 1))
        );
        scene.idle(20);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.NORTH))
            .placeNearTarget()
            .text("Logic Dock allows you to apply detection and targeting filters, as well as create whitelists of players and sable structures.");
        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showControls(
                util.vector().topOf(2, 1, 1),
                Pointing.DOWN,
                100
            )
            .rightClick()
            .withItem(ModItems.TARGETING_CARD.get().asItem().getDefaultInstance());
        scene.world().modifyBlockEntityNBT(util.select().position(2, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                CompoundTag card = new CompoundTag();
                card.putString("id", "power_radar:targeting_card");
                card.putInt("count", 1);

                nbt.put("Card0", card);
                nbt.putByte("CardPresenceMask", (byte) 1);
            }
        );
        scene.overlay()
            .showText(100)
            .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.NORTH))
            .placeNearTarget()
            .text("The targeting card allows you to select categories for auto-aiming cannons.");
        scene.idle(110);
        scene.world().modifyBlockEntityNBT(
            util.select().position(2, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                nbt.remove("Card0");
                nbt.putByte("CardPresenceMask", (byte) 0);
            }
        );
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showControls(
                util.vector().topOf(2, 1, 1),
                Pointing.DOWN,
                100
            )
            .rightClick()
            .withItem(ModItems.DISPLAY_CARD.get().asItem().getDefaultInstance());
        scene.world().modifyBlockEntityNBT(util.select().position(2, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                CompoundTag card = new CompoundTag();
                card.putString("id", "power_radar:display_card");
                card.putInt("count", 1);

                nbt.put("Card1", card);
                nbt.putByte("CardPresenceMask", (byte) 2);
            }
        );
        scene.overlay()
            .showText(100)
            .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.NORTH))
            .placeNearTarget()
            .text("The display card allows you to select target categories to display on screens.");
        scene.idle(110);
        scene.world().modifyBlockEntityNBT(
            util.select().position(2, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                nbt.remove("Card1");
                nbt.putByte("CardPresenceMask", (byte) 0);
            }
        );
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showControls(
                util.vector().topOf(2, 1, 1),
                Pointing.DOWN,
                100
            )
            .rightClick()
            .withItem(ModItems.ALLOWLIST_CARD.get().asItem().getDefaultInstance());
        scene.world().modifyBlockEntityNBT(util.select().position(2, 1, 1),
            LogicDockBlockEntity.class,
            nbt -> {
                CompoundTag card = new CompoundTag();
                card.putString("id", "power_radar:allowlist_card");
                card.putInt("count", 1);

                nbt.put("Card2", card);
                nbt.putByte("CardPresenceMask", (byte) 4);
            }
        );
        scene.overlay()
            .showText(100)
            .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.NORTH))
            .placeNearTarget()
            .text("Allowlist card allows you to add players and sable structures to the white/black list.");
        scene.idle(110);
    }
}
