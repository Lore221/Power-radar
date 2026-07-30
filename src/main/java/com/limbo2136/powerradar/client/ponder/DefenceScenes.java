package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.registry.ModItems;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import rbasamoyai.createbigcannons.ponder.CBCAnimateBlockEntityInstruction;

public class DefenceScenes {

    public static void ShellAlarm(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connect = new WireConnectionInstructions(builder);
        scene.title("shell_alarm", "Shell alarm");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 1 ,1), Direction.DOWN);
        scene.idle(20);
        scene.overlay()
            .showText(100)
            .pointAt(util.vector().topOf(4, 1, 1))
            .placeNearTarget()
            .text("To connect the shell alarm to the network, right-click on the radar link.");
        scene.overlay()
            .showControls(
                util.vector().topOf(4, 1, 1),
                Pointing.DOWN,
                100
            )
            .rightClick()
            .withItem(ModBlocks.SHELL_ALARM.get().asItem().getDefaultInstance());
        scene.overlay()
            .showOutline(
                PonderPalette.BLUE,
                "radar_link",
                util.select().position(4, 1, 1),
                170
            );
        scene.idle(120);
        scene.world().showSection(util.select().fromTo(7, 1, 1, 7, 1, 4), Direction.DOWN);
        scene.idle(10);
        connect.createConnection(
            new InWorldNode(1, 7, 1, 4),
            new InWorldNode(1, 7, 1, 1)
        );
        connect.createConnection(
            new InWorldNode(0, 7, 1, 4),
            new InWorldNode(0, 7, 1, 1)
        );
        scene.overlay()
            .showOutline(
                PonderPalette.BLUE,
                "shell_alarm",
                util.select().position(7, 1, 1),
                40
            );
        scene.idle(60);

        scene.addKeyframe();
            scene.idle(10);
            scene.overlay()
            .showText(100)
            .pointAt(util.vector().topOf(7, 1, 1))
            .placeNearTarget()
            .text("The size of the protected zone is adjusted by holding the right mouse button on the shell alarm");
        scene.overlay()
            .showControls(
                util.vector().topOf(7, 1, 1),
                Pointing.DOWN,
                100
            )
            .rightClick();
        scene.idle(100);

        scene.addKeyframe();
        scene.idle(10);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
            scene.idle(20);
            scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(7, 1, 1))
            .placeNearTarget()
            .text("In the presence of dangerous projectiles, the shell alarm emits a redstone signal with a strength of 15.");
            scene.world().modifyBlockEntityNBT(
                util.select().position(6, 1, 1),
                NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 15));
            scene.effects().createRedstoneParticles(util.grid().at(6, 1, 1), 0xFF0000, 10);
            scene.idle(90);
    }

    public static void InterceptController(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connect = new WireConnectionInstructions(builder);
        scene.title("interception_controller", "Interception controller");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 1, 1), Direction.DOWN);
        scene.world().showSection(util.select().position(7, 1, 1), Direction.DOWN);
        scene.world().showSection(util.select().position(7, 1, 4), Direction.DOWN);
        scene.idle(10);
        connect.createConnection(
            new InWorldNode(1, 7, 1, 4),
            new InWorldNode(1, 7, 1, 1)
        );
        connect.createConnection(
            new InWorldNode(0, 7, 1, 4),
            new InWorldNode(0, 7, 1, 1)
        );

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(7, 1, 1))
            .placeNearTarget()
            .text("When deployed, Shell alarm creates an interception network.");
        scene.idle(100);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(7, 1, 1))
            .placeNearTarget()
            .text("To connect the interception controller to the interception network, right-click on the shell alarm.");
        scene.overlay()
            .showControls(
                util.vector().topOf(7, 1, 1),
                Pointing.DOWN,
                80
            )
            .rightClick()
            .withItem(ModBlocks.INTERCEPTION_CONTROLLER.get().asItem().getDefaultInstance());
        scene.overlay()
            .showOutline(
                PonderPalette.RED,
                "shell_alarm",
                util.select().position(7, 1, 1),
                200
            );
        scene.idle(100);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1 ,4), Direction.DOWN);
        scene.idle(20);
        scene.overlay()
            .showOutline(
                PonderPalette.RED,
                "controller",
                util.select().position(1, 1, 2),
                80
            );
        connect.createConnection(
            new InWorldNode(0, 1, 1, 4),
            new InWorldNode(1, 1, 1, 2)
        );
        connect.createConnection(
            new InWorldNode(1, 1, 1, 4),
            new InWorldNode(0, 1, 1, 2)
        );
        scene.idle(10);
        ElementLink<WorldSectionElement> cannon =
            scene.world().showIndependentSection(
                util.select().fromTo(1, 3, 0, 1, 3, 3), Direction.DOWN
            );
        scene.world().configureCenterOfRotation(cannon, util.vector().centerOf(1, 3, 1));
        scene.idle(20);
        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showText(60)
            .pointAt(util.vector().topOf(1, 3, 1))
            .placeNearTarget()
            .text("To intercept projectiles, a flac round with an interception fuse is required.");
        scene.overlay()
            .showControls(
                util.vector().topOf(1, 3, 1),
                Pointing.DOWN,
                60
            )
            .rightClick()
            .withItem(ModItems.INTERCEPTION_FUZE.get().asItem().getDefaultInstance());
        scene.idle(60);
        scene.world().rotateSection(cannon, -60, 0, 0, 20);
        scene.addInstruction(
            CBCAnimateBlockEntityInstruction.cannonMountPitch(util.grid().at(1, 1, 1), 60.0f, 20)
        );
        scene.idle(40);
    }

    public static void OnBoardComputer (SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);
        RadarMonitorPonderInstructions display = new RadarMonitorPonderInstructions(builder);

        scene.title("onboard_computer", "Onboard computer");
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        connects.createConnection(
            new InWorldNode(0, 2, 1, 3),
            new InWorldNode(1, 2, 1, 1)
        );
        connects.createConnection(
            new InWorldNode(1, 2, 1, 3),
        new InWorldNode(0, 2, 1, 1)
        );
        scene.overlay()
            .showText(140)
            .pointAt(util.vector().topOf(2, 1, 1))
            .placeNearTarget()
            .text("The onboard computer can only be installed on a sable structure. It creates its own radar network, to which everything except the target controller can be connected.");
        scene.idle(10);
        display.showOnboardOverviewModuleArea(util.grid().at(2, 1 , 1), Direction.NORTH, 0.8f);
        display.showOnboardBlips(util.grid().at(2, 1 , 1), Direction.NORTH, 4, RadarTargetCategory.HOSTILE_MOB, RadarStructureType.OVERVIEW, 0.8f, 0);
        scene.idle(160);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showText(100)
            .pointAt(util.vector().topOf(2, 1, 1))
            .placeNearTarget()
            .text("The on-board computer also acts as a shell alarm, but with a fixed zone, which is equal to x1.1 of the structure size.");
        scene.idle(120);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(2, 1, 1))
            .placeNearTarget()
            .text("It is possible to install up to 4 modules on the on-board computer.");
            scene.idle(10);
        scene.world().modifyBlockEntityNBT(
            util.select().position(2, 1, 1),
            OnboardComputerBlockEntity.class,
            nbt -> {
                ListTag modules = new ListTag();
                addOnboardModule(modules, 0, "minecraft:compass");
                addOnboardModule(modules, 1, "minecraft:clock");
                addOnboardModule(modules, 2, "simulated:velocity_sensor");
                addOnboardModule(modules, 3, "simulated:altitude_sensor");
                nbt.put("OnboardModules", modules);
            }
        );
        scene.idle(80);
        scene.world().modifyBlockEntityNBT(
            util.select().position(2, 1, 1),
            OnboardComputerBlockEntity.class,
            nbt -> {
                nbt.put("OnboardModules", new ListTag());
                nbt.remove("AccelerometerColumns");
                nbt.remove("VariometerColumns");
            }
        );
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
            .showText(80)
            .pointAt(util.vector().topOf(2, 1, 1))
            .placeNearTarget()
            .text("Some vertical combinations of modules allow you to get a completely new module.");
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(
            util.select().position(2, 1, 1),
            OnboardComputerBlockEntity.class,
            nbt -> {
                ListTag modules = new ListTag();
                addOnboardModule(modules, 0, "simulated:velocity_sensor");
                addOnboardModule(modules, 1, "minecraft:clock");
                addOnboardModule(modules, 2, "minecraft:clock");
                addOnboardModule(modules, 3, "simulated:altitude_sensor");
                nbt.put("OnboardModules", modules);
                nbt.putByte("AccelerometerColumns", (byte) 1);
                nbt.putByte("VariometerColumns", (byte) 2);
            }
        );
       scene.idle(100);
    }

    private static void addOnboardModule(ListTag modules, int slot, String itemId) {
        CompoundTag module = new CompoundTag();
        module.putByte("Slot", (byte) slot);

        CompoundTag stack = new CompoundTag();
        stack.putString("id", itemId);
        stack.putInt("count", 1);
        module.put("Stack", stack);
        modules.add(module);
    }
}
