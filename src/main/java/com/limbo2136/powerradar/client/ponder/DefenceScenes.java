package com.limbo2136.powerradar.client.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.limbo2136.powerradar.registry.ModItems;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

public class DefenceScenes {

    public static void shellAlarm(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);

        scene.title("shell_alarm", "Using a Shell Alarm");
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.overlay()
                .showText(220)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Shell Alarm warns you when a radar detects a shell threatening the protected area. Detecting incoming threats requires power and a connection to a radar network.");
        scene.idle(45);
        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(1, util.grid().at(2, 1, 1)),
                new InWorldNode(0, util.grid().at(2, 1, 3)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(2, 1, 1)),
                new InWorldNode(1, util.grid().at(2, 1, 3)));
        scene.idle(160);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showControls(
                        util.vector().topOf(2, 1, 1),
                        Pointing.DOWN,
                        140)
                .rightClick();
        scene.overlay()
                .showText(140)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("Right-click and hold the Shell Alarm to adjust the size of its protected area.");
        scene.idle(150);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(240)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("The Shell Alarm evaluates every projectile detected by its radar network. If a projectile threatens the protected area, the alarm emits a redstone signal with a strength of 15.");
        scene.rotateCameraY(90);
        scene.idle(20);
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(
                util.select().position(3, 1, 1),
                NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 15));
        scene.effects().createRedstoneParticles(
                util.grid().at(3, 1, 1),
                0xFF0000,
                10);
        scene.idle(210);
        scene.world().modifyBlockEntityNBT(
                util.select().position(3, 1, 1),
                NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 0));

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(200)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("When installed on a Sable structure, the Shell Alarm emits a short redstone pulse whenever the structure enters another radar's coverage.");
        scene.idle(40);
        scene.world().modifyBlockEntityNBT(
                util.select().position(3, 1, 1),
                NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 15));
        scene.effects().createRedstoneParticles(
                util.grid().at(3, 1, 1),
                0xFF0000,
                10);
        scene.idle(5);
        scene.world().modifyBlockEntityNBT(
                util.select().position(3, 1, 1),
                NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", 0));
        scene.idle(165);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(180)
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .text("While its Sable structure is being tracked by a Target Controller, the Shell Alarm emits a repeating redstone signal.");
        scene.idle(40);
        for (int i = 0; i < 10; i++) {
            scene.world().modifyBlockEntityNBT(
                    util.select().position(3, 1, 1),
                    NixieTubeBlockEntity.class,
                    nbt -> nbt.putInt("RedstoneStrength", 15));
            scene.effects().createRedstoneParticles(
                    util.grid().at(3, 1, 1),
                    0xFF0000,
                    10);
            scene.idle(5);
            scene.world().modifyBlockEntityNBT(
                    util.select().position(3, 1, 1),
                    NixieTubeBlockEntity.class,
                    nbt -> nbt.putInt("RedstoneStrength", 0));
            scene.idle(5);
        }
        scene.idle(50);
    }

    public static void interception(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        WireConnectionInstructions connects = new WireConnectionInstructions(builder);

        scene.title("interception", "Connecting and Powering an Interception Controller");
        scene.idle(10);

        scene.addKeyframe();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(4, 1, 1, 7, 4, 4), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(0, util.grid().at(4, 1, 1)),
                new InWorldNode(1, util.grid().at(4, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(4, 1, 1)),
                new InWorldNode(0, util.grid().at(4, 1, 4)));
        connects.createConnection(
                new InWorldNode(0, util.grid().at(7, 1, 1)),
                new InWorldNode(1, util.grid().at(7, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(7, 1, 1)),
                new InWorldNode(0, util.grid().at(7, 1, 4)));
        scene.overlay()
                .showText(160)
                .pointAt(util.vector().topOf(4, 1, 1))
                .placeNearTarget()
                .text("The Shell Alarm creates its own interception network. To connect an Interception Controller, right-click the Shell Alarm while holding the controller.");
        scene.overlay()
                .showOutline(
                        PonderPalette.RED,
                        "shellAlarm",
                        util.select().position(4, 1, 1),
                        200);
        scene.overlay()
                .showControls(util.vector().topOf(4, 1, 1),
                        Pointing.DOWN,
                        80)
                .rightClick()
                .withItem(ModItems.INTERCEPTION_CONTROLLER.get().getDefaultInstance());
        scene.idle(80);
        scene.world().showSection(util.select().position(1, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay()
                .showOutline(
                        PonderPalette.RED,
                        "interceptController",
                        util.select().position(1, 1, 2),
                        110);
        scene.idle(120);

        scene.addKeyframe();
        scene.idle(10);
        scene.rotateCameraY(-90);
        scene.idle(20);
        scene.overlay()
                .showText(120)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Interception Controller allows connected autocannons to intercept dangerous projectiles.");
        scene.idle(20);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().layer(3).substract(util.select().position(7, 3, 1)), Direction.DOWN);
        scene.idle(100);

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay()
                .showText(100)
                .pointAt(util.vector().topOf(1, 1, 2))
                .placeNearTarget()
                .text("The Interception Controller also requires power to operate.");
        scene.idle(20);
        scene.world().showSection(util.select().position(1, 1, 4), Direction.DOWN);
        scene.idle(15);
        connects.createConnection(
                new InWorldNode(0, util.grid().at(1, 1, 2)),
                new InWorldNode(1, util.grid().at(1, 1, 4)));
        connects.createConnection(
                new InWorldNode(1, util.grid().at(1, 1, 2)),
                new InWorldNode(0, util.grid().at(1, 1, 4)));
        scene.idle(80);
    }
}
