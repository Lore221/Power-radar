package com.limbo2136.powerradar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarDisplaySpriteRenderer {
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

    public void drawHoveredFrame(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            float depth
    ) {
        drawFrame(graphics, blip, alpha, drawSize, RadarBlipSprite.HOVERED_FRAME, palette.hoveredFrame(), depth);
    }

    public void drawSelectedFrame(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
            PowerRadarClientConfig.RadarRenderPalette palette,
            float depth
    ) {
        drawFrame(graphics, blip, alpha, drawSize, RadarBlipSprite.SELECTED_FRAME, palette.selectedFrame(), depth);
    }

    private static void drawFrame(
            GuiGraphics graphics,
            RadarBlipRenderData blip,
            int alpha,
            int drawSize,
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
        graphics.pose().translate(blip.screenX() - halfSize, blip.screenY() - halfSize, depth);
        drawLayer(graphics, frame, drawSize, frameColor, alpha);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
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
        drawLayer(graphics, icon, drawSize, palette.blip(blip.category()), alpha);
        if (frame != null) {
            drawLayer(graphics, frame, drawSize, frameColor, alpha);
        }
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void drawLayer(GuiGraphics graphics, RadarBlipSprite sprite, int cellDrawSize, int color, int alpha) {
        int width = Math.max(1, Math.round(cellDrawSize * sprite.width() / (float) RadarBlipSprite.CELL_SIZE));
        int height = Math.max(1, Math.round(cellDrawSize * sprite.height() / (float) RadarBlipSprite.CELL_SIZE));
        int x = (cellDrawSize - width) / 2;
        int y = (cellDrawSize - height) / 2;
        RenderSystem.setShaderColor(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                alpha / 255.0F);
        graphics.blit(
                RadarBlipSprite.ATLAS,
                x, y, width, height,
                sprite.sourceX(), sprite.sourceY(), sprite.width(), sprite.height(),
                RadarBlipSprite.ATLAS_SIZE, RadarBlipSprite.ATLAS_SIZE);
    }

}
