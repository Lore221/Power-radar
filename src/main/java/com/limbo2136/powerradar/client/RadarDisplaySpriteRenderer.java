package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarDisplaySpriteRenderer {
    private static final ResourceLocation BLIP_PLAYER =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_player.png");
    private static final ResourceLocation BLIP_HOSTILE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_hostile.png");
    private static final ResourceLocation BLIP_PROJECTILE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_projectile.png");
    private static final ResourceLocation BLIP_PASSIVE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_passive.png");
    private static final ResourceLocation BLIP_PLAYER_SELECTED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_player_selected.png");
    private static final ResourceLocation BLIP_HOSTILE_SELECTED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_hostile_selected.png");
    private static final ResourceLocation BLIP_PROJECTILE_SELECTED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_projectile_selected.png");
    private static final ResourceLocation BLIP_PASSIVE_SELECTED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_passive_selected.png");
    private static final ResourceLocation BLIP_OTHER_SELECTED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/blip_other_selected.png");

    public void drawBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha) {
        drawBlip(graphics, blip, alpha, RadarConstants.GUI_BLIP_DRAW_SIZE);
    }

    public void drawBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha, int drawSize) {
        drawBlip(graphics, blip, alpha, blipTexture(blip.category()), drawSize);
    }

    public void drawSelectedBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha) {
        drawBlip(graphics, blip, alpha, selectedBlipTexture(blip.category()), RadarConstants.GUI_BLIP_DRAW_SIZE);
    }

    public void drawSelectedBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha, int drawSize) {
        drawBlip(graphics, blip, alpha, selectedBlipTexture(blip.category()), drawSize);
    }

    public void drawLockedSelectedBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha) {
        drawBlip(graphics, blip, alpha, selectedBlipTexture(blip.category()), RadarConstants.GUI_BLIP_DRAW_SIZE + 4);
    }

    public void drawLockedSelectedBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha, int drawSize) {
        drawBlip(graphics, blip, alpha, selectedBlipTexture(blip.category()), drawSize);
    }

    private void drawBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha, ResourceLocation texture) {
        drawBlip(graphics, blip, alpha, texture, RadarConstants.GUI_BLIP_DRAW_SIZE);
    }

    private void drawBlip(GuiGraphics graphics, RadarBlipRenderData blip, int alpha, ResourceLocation texture, int drawSize) {
        if (alpha <= 0) {
            return;
        }
        float blipAlpha = alpha / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, blipAlpha);
        float guiScaleCompensation = guiScaleCompensation();
        float halfSize = drawSize / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                blip.screenX() - halfSize * guiScaleCompensation,
                blip.screenY() - halfSize * guiScaleCompensation,
                0.0F
        );
        graphics.pose().scale(guiScaleCompensation, guiScaleCompensation, 1.0F);
        blit(
                graphics,
                texture,
                0,
                0,
                drawSize,
                drawSize,
                RadarConstants.BLIP_TEXTURE_SIZE,
                RadarConstants.BLIP_TEXTURE_SIZE
        );
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static float guiScaleCompensation() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (1.0D / Math.max(1.0D, guiScale));
    }

    private ResourceLocation blipTexture(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> BLIP_PLAYER;
            case HOSTILE_MOB -> BLIP_HOSTILE;
            case PROJECTILE -> BLIP_PROJECTILE;
            case PASSIVE_MOB -> BLIP_PASSIVE;
            case SABLE_STRUCTURE, UNKNOWN -> BLIP_PASSIVE;
        };
    }

    private ResourceLocation selectedBlipTexture(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> BLIP_PLAYER_SELECTED;
            case HOSTILE_MOB -> BLIP_HOSTILE_SELECTED;
            case PROJECTILE -> BLIP_PROJECTILE_SELECTED;
            case PASSIVE_MOB -> BLIP_PASSIVE_SELECTED;
            case SABLE_STRUCTURE, UNKNOWN -> BLIP_OTHER_SELECTED;
        };
    }

    private static void blit(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int textureWidth,
            int textureHeight
    ) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
    }
}
