package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.config.PowerRadarClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarDisplaySpriteRenderer {
    public void drawMarker(
            GuiGraphics graphics,
            float centerX,
            float centerY,
            int alpha,
            int drawSize,
            RadarBlipSprite sprite,
            int color,
            float depth
    ) {
        if (alpha <= 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float halfSize = drawSize * 0.5F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - halfSize, centerY - halfSize, depth);
        drawLayer(graphics, sprite, drawSize, color, alpha);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    public void drawBlip(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            float depth
    ) {
        drawBlip(graphics, blip, alpha, drawSize, palette, null, 0, depth);
    }

    public void drawSelectedBlip(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            float depth
    ) {
        drawBlip(graphics, blip, alpha, drawSize, palette,
                RadarBlipSprite.HOVERED_FRAME, palette.hoveredFrame(), depth);
    }

    public void drawLockedSelectedBlip(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            float depth
    ) {
        drawBlip(graphics, blip, alpha, drawSize, palette,
                RadarBlipSprite.SELECTED_FRAME, palette.selectedFrame(), depth);
    }

    private void drawBlip(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            RadarBlipSprite frame,
            int frameColor,
            float depth
    ) {
        if (alpha <= 0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float halfSize = drawSize / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                blip.screenX() - halfSize,
                blip.screenY() - halfSize,
                depth
        );
        // Иконка рисуется первой, рамка — поверх неё на той же экранной позиции.
        RadarBlipSprite icon = RadarBlipSprite.forCategory(blip.category());
        drawLayer(graphics, icon, drawSize, palette.blip(blip.category()), alpha, blip.rotationDegrees());
        if (frame != null) {
            drawLayer(graphics, frame, drawSize, frameColor, alpha);
        }
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void drawLayer(GuiGraphics graphics, RadarBlipSprite sprite, int cellDrawSize, int color, int alpha) {
        drawLayer(graphics, sprite, cellDrawSize, color, alpha, 0.0F);
    }

    private static void drawLayer(
            GuiGraphics graphics,
            RadarBlipSprite sprite,
            int cellDrawSize,
            int color,
            int alpha,
            float rotationDegrees
    ) {
        int width = Math.max(1, Math.round(cellDrawSize * sprite.width() / (float) RadarBlipSprite.CELL_SIZE));
        int height = Math.max(1, Math.round(cellDrawSize * sprite.height() / (float) RadarBlipSprite.CELL_SIZE));
        float offsetX = (cellDrawSize - width) * 0.5F;
        float offsetY = (cellDrawSize - height) * 0.5F;
        graphics.pose().pushPose();
        if (rotationDegrees != 0.0F) {
            float center = cellDrawSize * 0.5F;
            graphics.pose().translate(center, center, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
            graphics.pose().translate(-center, -center, 0.0F);
        }
        graphics.pose().translate(offsetX, offsetY, 0.0F);
        RenderSystem.setShaderColor(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                alpha / 255.0F);
        graphics.blit(
                RadarBlipSprite.ATLAS,
                0, 0, width, height,
                sprite.sourceX(), sprite.sourceY(), sprite.width(), sprite.height(),
                RadarBlipSprite.ATLAS_SIZE, RadarBlipSprite.ATLAS_SIZE);
        graphics.pose().popPose();
    }

}
