package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.network.RadarControllerSettingsPayload;
import com.limbo2136.powerradar.network.RadarControllerSnapshotPayload;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.TargetTrajectoryMode;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class RadarControllerScreen extends Screen {
    private static final ResourceLocation BUTTON =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button.png");
    private static final ResourceLocation BUTTON_HOVERED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button_hovered.png");
    private static final ResourceLocation BUTTON_DISABLED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button_disabled.png");
    private static final int BUTTON_TEXTURE_WIDTH = 128;
    private static final int BUTTON_TEXTURE_HEIGHT = 24;
    private static final int PANEL_WIDTH = 196;
    private static final int PANEL_HEIGHT = 232;
    private static final int BUTTON_GAP = 5;
    private static final int MODE_BUTTON_HEIGHT = 21;
    private static final int CATEGORY_BUTTON_SIZE = 24;
    private static final int CATEGORY_ICON_SIZE = 18;
    private static final int CATEGORY_ICON_TEXTURE_SIZE = 24;
    private static final int BG = 0xF01C1C1A;
    private static final int BG_INSET = 0xFF25231F;
    private static final int EDGE_DARK = 0xFF0E0E0D;
    private static final int EDGE_LIGHT = 0xFF6F6A5B;
    private static final int BRASS = 0xFFD5A44D;
    private static final int BRASS_DIM = 0xFF9D7A3D;
    private static final int TEXT = 0xFFE8DFCB;
    private static final int TEXT_DIM = 0xFFB8AD93;
    private static final int TEXT_BAD = 0xFFFF8C70;

    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private RadarControllerSnapshotPayload snapshot;
    private Component hoveredTooltip;

    public RadarControllerScreen(RadarControllerSnapshotPayload snapshot) {
        super(Component.translatable("screen.power_radar.controller.title"));
        this.snapshot = snapshot;
    }

    public boolean isFor(BlockPos controllerPos) {
        return this.snapshot.controllerPos().equals(controllerPos);
    }

    public void updateSnapshot(RadarControllerSnapshotPayload snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.clickTargets.clear();
        this.hoveredTooltip = null;
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        drawPanel(graphics, left, top);
        graphics.drawString(this.font, this.title, left + 12, top + 10, BRASS, false);

        int x = left + 12;
        int y = top + 28;
        int contentWidth = PANEL_WIDTH - 24;
        y = renderModeButtons(graphics, mouseX, mouseY, x, y, contentWidth);
        y += 10;
        y = renderCategoryButtons(graphics, mouseX, mouseY, x, y, contentWidth);
        y += 10;
        y = renderTrajectoryButtons(graphics, mouseX, mouseY, x, y, contentWidth);
        y += 12;
        renderSummary(graphics, x, y, contentWidth);

        if (this.hoveredTooltip != null) {
            graphics.renderTooltip(this.font, this.hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickTarget target : this.clickTargets) {
                if (!target.contains(mouseX, mouseY)) {
                    continue;
                }
                if (target.mode() != null) {
                    sendSettings(target.mode(), this.snapshot.detectionFilterMask(), this.snapshot.targetTrajectoryMode());
                    return true;
                }
                if (target.category() != null) {
                    int bit = RadarDetectionFilters.bit(target.category().category());
                    sendSettings(this.snapshot.mode(), this.snapshot.detectionFilterMask() ^ bit, this.snapshot.targetTrajectoryMode());
                    return true;
                }
                if (target.trajectoryMode() != null) {
                    sendSettings(this.snapshot.mode(), this.snapshot.detectionFilterMask(), target.trajectoryMode());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendSettings(RadarScanMode mode, int detectionFilterMask, TargetTrajectoryMode targetTrajectoryMode) {
        PacketDistributor.sendToServer(new RadarControllerSettingsPayload(
                this.snapshot.controllerPos(),
                mode,
                RadarDetectionFilters.sanitize(detectionFilterMask),
                targetTrajectoryMode));
    }

    private int renderModeButtons(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.mode"), x, y, TEXT_DIM, false);
        y += 12;
        int buttonWidth = Math.max(40, (width - BUTTON_GAP * 2) / 3);
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonWidth, MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_air"), this.snapshot.mode() == RadarScanMode.SKY);
        this.clickTargets.add(ClickTarget.mode(x, y, buttonWidth, MODE_BUTTON_HEIGHT, RadarScanMode.SKY));
        renderSmallButton(graphics, mouseX, mouseY, x + buttonWidth + BUTTON_GAP, y, buttonWidth, MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_general"), this.snapshot.mode() == RadarScanMode.GROUND);
        this.clickTargets.add(ClickTarget.mode(x + buttonWidth + BUTTON_GAP, y, buttonWidth, MODE_BUTTON_HEIGHT, RadarScanMode.GROUND));
        renderSmallButton(graphics, mouseX, mouseY, x + (buttonWidth + BUTTON_GAP) * 2, y, buttonWidth, MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_ground"), this.snapshot.mode() == RadarScanMode.SURFACE_SCANNER);
        this.clickTargets.add(ClickTarget.mode(x + (buttonWidth + BUTTON_GAP) * 2, y, buttonWidth, MODE_BUTTON_HEIGHT, RadarScanMode.SURFACE_SCANNER));
        return y + MODE_BUTTON_HEIGHT;
    }

    private int renderCategoryButtons(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.detection_filter"), x, y, TEXT_DIM, false);
        y += 12;
        CategoryButton[] categories = CategoryButton.values();
        int gap = Math.max(3, Math.min(BUTTON_GAP, (width - CATEGORY_BUTTON_SIZE * categories.length) / Math.max(1, categories.length - 1)));
        int totalWidth = CATEGORY_BUTTON_SIZE * categories.length + gap * Math.max(0, categories.length - 1);
        int startX = x + Math.max(0, (width - totalWidth) / 2);
        for (int i = 0; i < categories.length; i++) {
            CategoryButton category = categories[i];
            boolean enabled = category.enabledFor(this.snapshot.mode());
            boolean selected = RadarDetectionFilters.enabled(this.snapshot.detectionFilterMask(), category.category());
            int buttonX = startX + i * (CATEGORY_BUTTON_SIZE + gap);
            renderCategoryButton(graphics, mouseX, mouseY, buttonX, y, category, enabled, selected);
            if (enabled) {
                this.clickTargets.add(ClickTarget.category(buttonX, y, CATEGORY_BUTTON_SIZE, CATEGORY_BUTTON_SIZE, category));
            }
        }
        return y + CATEGORY_BUTTON_SIZE;
    }

    private int renderTrajectoryButtons(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.trajectory"), x, y, TEXT_DIM, false);
        y += 12;
        int buttonWidth = Math.max(56, (width - BUTTON_GAP) / 2);
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonWidth, MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.trajectory_flat"),
                this.snapshot.targetTrajectoryMode() != TargetTrajectoryMode.HIGH_ARC);
        this.clickTargets.add(ClickTarget.trajectory(x, y, buttonWidth, MODE_BUTTON_HEIGHT, TargetTrajectoryMode.FLAT));
        renderSmallButton(graphics, mouseX, mouseY, x + buttonWidth + BUTTON_GAP, y, buttonWidth, MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.trajectory_high"),
                this.snapshot.targetTrajectoryMode() == TargetTrajectoryMode.HIGH_ARC);
        this.clickTargets.add(ClickTarget.trajectory(x + buttonWidth + BUTTON_GAP, y, buttonWidth, MODE_BUTTON_HEIGHT, TargetTrajectoryMode.HIGH_ARC));
        return y + MODE_BUTTON_HEIGHT;
    }

    private void renderSummary(GuiGraphics graphics, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.range").getString()
                + ": " + this.snapshot.currentRange() + " / " + this.snapshot.maxRange(), x, y, TEXT, false);
        y += 11;
        graphics.drawString(this.font, Component.translatable("power_radar.electrical.panel_count", this.snapshot.validPanelCount()), x, y, TEXT_DIM, false);
        y += 11;
        graphics.drawString(this.font, Component.translatable("power_radar.electrical.basic_panel_count", this.snapshot.basicPanelCount()), x, y, TEXT_DIM, false);
        y += 11;
        int stateColor = this.snapshot.assembled() ? TEXT_DIM : TEXT_BAD;
        graphics.drawString(this.font, Component.translatable("power_radar.electrical.state",
                Component.translatable(this.snapshot.electricalState().translationKey())), x, y, stateColor, false);
        y += 19;
        graphics.drawString(this.font, Component.translatable("message.power_radar.controller.whitelist_placeholder"), x, y, BRASS_DIM, false);
        y += 11;
        graphics.drawString(this.font, Component.translatable("message.power_radar.controller.target_placeholder"), x, y, BRASS_DIM, false);
    }

    private void renderSmallButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, Component label, boolean selected) {
        ResourceLocation texture = buttonTexture(x, y, width, height, mouseX, mouseY, selected, true);
        drawNineSlice(graphics, texture, x, y, width, height, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        graphics.drawCenteredString(this.font, fitLine(label.getString(), width - 8), x + width / 2, y + (height - 8) / 2, selected ? BRASS : TEXT);
    }

    private void renderCategoryButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, CategoryButton category, boolean enabled, boolean selected) {
        ResourceLocation texture = buttonTexture(x, y, CATEGORY_BUTTON_SIZE, CATEGORY_BUTTON_SIZE, mouseX, mouseY, selected, enabled);
        drawNineSlice(graphics, texture, x, y, CATEGORY_BUTTON_SIZE, CATEGORY_BUTTON_SIZE, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        if (mouseX >= x && mouseX < x + CATEGORY_BUTTON_SIZE && mouseY >= y && mouseY < y + CATEGORY_BUTTON_SIZE) {
            this.hoveredTooltip = category.tooltip();
        }
        int iconX = x + (CATEGORY_BUTTON_SIZE - CATEGORY_ICON_SIZE) / 2;
        int iconY = y + (CATEGORY_BUTTON_SIZE - CATEGORY_ICON_SIZE) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, enabled ? 1.0F : 0.38F);
        graphics.blit(category.icon(), iconX, iconY, CATEGORY_ICON_SIZE, CATEGORY_ICON_SIZE,
                0.0F, 0.0F, CATEGORY_ICON_TEXTURE_SIZE, CATEGORY_ICON_TEXTURE_SIZE,
                CATEGORY_ICON_TEXTURE_SIZE, CATEGORY_ICON_TEXTURE_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private ResourceLocation buttonTexture(int x, int y, int width, int height, int mouseX, int mouseY, boolean selected, boolean enabled) {
        if (!enabled) {
            return BUTTON_DISABLED;
        }
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        return hovered || selected ? BUTTON_HOVERED : BUTTON;
    }

    private void drawPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 3, y + 3, x + PANEL_WIDTH + 3, y + PANEL_HEIGHT + 3, 0x80000000);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BG);
        graphics.fill(x + 4, y + 4, x + PANEL_WIDTH - 4, y + PANEL_HEIGHT - 4, BG_INSET);
        graphics.hLine(x, x + PANEL_WIDTH - 1, y, EDGE_LIGHT);
        graphics.hLine(x, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, EDGE_DARK);
        graphics.vLine(x, y, y + PANEL_HEIGHT - 1, EDGE_LIGHT);
        graphics.vLine(x + PANEL_WIDTH - 1, y, y + PANEL_HEIGHT - 1, EDGE_DARK);
        graphics.hLine(x + 8, x + PANEL_WIDTH - 9, y + 23, BRASS_DIM);
    }

    private String fitLine(String line, int maxWidth) {
        if (this.font.width(line) <= maxWidth) {
            return line;
        }
        return this.font.plainSubstrByWidth(line, Math.max(12, maxWidth - this.font.width("..."))) + "...";
    }

    private static void drawNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                      int textureWidth, int textureHeight, int slice) {
        int right = textureWidth - slice;
        int bottom = textureHeight - slice;
        int middleWidth = Math.max(0, width - slice * 2);
        int middleHeight = Math.max(0, height - slice * 2);
        int sourceMiddleWidth = textureWidth - slice * 2;
        int sourceMiddleHeight = textureHeight - slice * 2;
        blitPart(graphics, texture, x, y, slice, slice, 0, 0, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x + width - slice, y, slice, slice, right, 0, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x, y + height - slice, slice, slice, 0, bottom, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x + width - slice, y + height - slice, slice, slice, right, bottom, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x + slice, y, middleWidth, slice, slice, 0, sourceMiddleWidth, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x + slice, y + height - slice, middleWidth, slice, slice, bottom, sourceMiddleWidth, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, x, y + slice, slice, middleHeight, 0, slice, slice, sourceMiddleHeight, textureWidth, textureHeight);
        blitPart(graphics, texture, x + width - slice, y + slice, slice, middleHeight, right, slice, slice, sourceMiddleHeight, textureWidth, textureHeight);
        blitPart(graphics, texture, x + slice, y + slice, middleWidth, middleHeight, slice, slice, sourceMiddleWidth, sourceMiddleHeight, textureWidth, textureHeight);
    }

    private static void blitPart(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                 int sourceX, int sourceY, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(texture, x, y, width, height, (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    private enum CategoryButton {
        HOSTILE("message.power_radar.monitor.filter.hostile", "filter_hostile.png", RadarTargetCategory.HOSTILE_MOB),
        PASSIVE("message.power_radar.monitor.filter.passive", "filter_passive.png", RadarTargetCategory.PASSIVE_MOB),
        PLAYER("message.power_radar.monitor.filter.player", "filter_player.png", RadarTargetCategory.PLAYER),
        SABLE("message.power_radar.monitor.filter.sable", "filter_sable.png", RadarTargetCategory.SABLE_STRUCTURE),
        PROJECTILE("message.power_radar.monitor.filter.projectile", "filter_projectile.png", RadarTargetCategory.PROJECTILE);

        private final String tooltipKey;
        private final ResourceLocation icon;
        private final RadarTargetCategory category;

        CategoryButton(String tooltipKey, String iconFileName, RadarTargetCategory category) {
            this.tooltipKey = tooltipKey;
            this.icon = ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/" + iconFileName);
            this.category = category;
        }

        private ResourceLocation icon() {
            return this.icon;
        }

        private RadarTargetCategory category() {
            return this.category;
        }

        private Component tooltip() {
            return Component.translatable(this.tooltipKey);
        }

        private boolean enabledFor(RadarScanMode mode) {
            return switch (this.category) {
                case PLAYER -> mode != RadarScanMode.SKY;
                case HOSTILE_MOB, PASSIVE_MOB -> mode == RadarScanMode.GROUND;
                case PROJECTILE -> mode == RadarScanMode.GROUND || mode == RadarScanMode.SKY;
                case SABLE_STRUCTURE -> mode == RadarScanMode.SURFACE_SCANNER;
                case UNKNOWN -> false;
            };
        }
    }

    private record ClickTarget(int x, int y, int width, int height, RadarScanMode mode, CategoryButton category, TargetTrajectoryMode trajectoryMode) {
        private static ClickTarget mode(int x, int y, int width, int height, RadarScanMode mode) {
            return new ClickTarget(x, y, width, height, mode, null, null);
        }

        private static ClickTarget category(int x, int y, int width, int height, CategoryButton category) {
            return new ClickTarget(x, y, width, height, null, category, null);
        }

        private static ClickTarget trajectory(int x, int y, int width, int height, TargetTrajectoryMode trajectoryMode) {
            return new ClickTarget(x, y, width, height, null, null, trajectoryMode);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
