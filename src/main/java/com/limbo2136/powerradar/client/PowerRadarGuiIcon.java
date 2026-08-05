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
import org.joml.Matrix4f;

/** Одна реализация 16x16-иконки для всех настроек Power Radar в интерфейсе Create. */
final class PowerRadarGuiIcon extends AllIcons {
    private static final ResourceLocation TEXTURE = PowerRadar.id("textures/gui/radar_ui/icons.png");
    private static final float ATLAS_SIZE = 256.0F;
    private static final int ICON_SIZE = 16;

    private final int u;
    private final int v;

    PowerRadarGuiIcon(int u, int v) {
        super(0, 0);
        this.u = u;
        this.v = v;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(TEXTURE, x, y, 0, this.u, this.v, ICON_SIZE, ICON_SIZE, 256, 256);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.text(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        Color rgb = new Color(color);
        float u1 = this.u / ATLAS_SIZE;
        float u2 = (this.u + ICON_SIZE) / ATLAS_SIZE;
        float v1 = this.v / ATLAS_SIZE;
        float v2 = (this.v + ICON_SIZE) / ATLAS_SIZE;
        vertex(builder, matrix, 0.0F, 0.0F, rgb, u1, v1);
        vertex(builder, matrix, 0.0F, 1.0F, rgb, u1, v2);
        vertex(builder, matrix, 1.0F, 1.0F, rgb, u2, v2);
        vertex(builder, matrix, 1.0F, 0.0F, rgb, u2, v1);
    }

    private static void vertex(
            VertexConsumer builder,
            Matrix4f matrix,
            float x,
            float y,
            Color color,
            float u,
            float v
    ) {
        builder.addVertex(matrix, x, y, 0.0F)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), 255)
                .setUv(u, v)
                .setLight(LightTexture.FULL_BRIGHT);
    }
}
