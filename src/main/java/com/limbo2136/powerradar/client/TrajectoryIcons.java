package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class TrajectoryIcons {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/icons.png");
    private static final AllIcons FLAT = new AtlasIcon(224, 48);
    private static final AllIcons HIGH = new AtlasIcon(240, 48);

    private TrajectoryIcons() {
    }

    public static AllIcons icon(boolean highArc) {
        // Публичная сигнатура вызывается отражением из TrajectoryIconBridge.
        return highArc ? HIGH : FLAT;
    }

    private static class AtlasIcon extends AllIcons {
        private final int u;
        private final int v;

        private AtlasIcon(int u, int v) {
            super(0, 0);
            this.u = u;
            this.v = v;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            graphics.blit(ICONS, x, y, 0, this.u, this.v, 16, 16, 256, 256);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int color) {
            VertexConsumer builder = buffer.getBuffer(RenderType.text(ICONS));
            Matrix4f matrix = poseStack.last().pose();
            Color rgb = new Color(color);
            float u1 = this.u / 256.0F;
            float u2 = (this.u + 16) / 256.0F;
            float v1 = this.v / 256.0F;
            float v2 = (this.v + 16) / 256.0F;
            vertex(builder, matrix, new Vec3(0, 0, 0), rgb, u1, v1);
            vertex(builder, matrix, new Vec3(0, 1, 0), rgb, u1, v2);
            vertex(builder, matrix, new Vec3(1, 1, 0), rgb, u2, v2);
            vertex(builder, matrix, new Vec3(1, 0, 0), rgb, u2, v1);
        }

        private static void vertex(
                VertexConsumer builder, Matrix4f matrix, Vec3 position, Color color, float u, float v) {
            builder.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                    .setColor(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .setUv(u, v)
                    .setLight(LightTexture.FULL_BRIGHT);
        }
    }
}
