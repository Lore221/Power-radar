package com.limbo2136.powerradar.client.ponder;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Неподвижный зомби для Ponder: не добавляется в виртуальный мир и потому не запускает AI.
 */
public class ZombiePonderElement extends AnimatedSceneElementBase {

    private final Vec3 location;
    private Zombie zombie;

    public ZombiePonderElement(Vec3 location) {
        this.location = location;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        if (zombie != null) {
            resetZombie();
        }
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (zombie == null) {
            zombie = createZombie(scene.getWorld());
        }

        zombie.tickCount++;
        zombie.xOld = zombie.getX();
        zombie.yOld = zombie.getY();
        zombie.zOld = zombie.getZ();
        zombie.yRotO = zombie.getYRot();
        zombie.xRotO = zombie.getXRot();
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        if (zombie == null) {
            zombie = createZombie(world);
        }

        PoseStack poseStack = graphics.pose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(location.x, location.y, location.z);
        dispatcher.render(zombie, 0.0, 0.0, 0.0, 0.0F, pt, poseStack, buffer, lightCoordsFromFade(fade));
        poseStack.popPose();
    }

    private static Zombie createZombie(PonderLevel world) {
        Zombie result = new Zombie(world);
        result.setNoAi(true);
        result.setNoGravity(true);
        result.setYRot(result.yRotO = 90.0F);
        return result;
    }

    private void resetZombie() {
        zombie.setPosRaw(0.0, 0.0, 0.0);
        zombie.xOld = 0.0;
        zombie.yOld = 0.0;
        zombie.zOld = 0.0;
        zombie.setXRot(zombie.xRotO = 0.0F);
        zombie.setYRot(zombie.yRotO = 90.0F);
    }
}
