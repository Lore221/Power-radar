package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.network.RadarMonitorRequestPayload;
import com.limbo2136.powerradar.network.RadarMonitorSettingsPayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorTargetSelectionPayload;
import com.limbo2136.powerradar.network.RadarMonitorWhitelistPayload;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarDisplayProjection;
import com.limbo2136.powerradar.radar.RadarDisplayProjector;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.TargetTrajectoryMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class RadarMonitorScreen extends Screen {
    private static final ResourceLocation GUI_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_gui_background.png");
    private static final ResourceLocation SIDE_PANEL =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_side_panel.png");
    private static final ResourceLocation BUTTON =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button.png");
    private static final ResourceLocation BUTTON_HOVERED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button_hovered.png");
    private static final ResourceLocation BUTTON_DISABLED =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/monitor_button_disabled.png");
    private static final ResourceLocation RADAR_SCREEN_BACK =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_screen_back.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_60 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_60.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_90 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_90.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_120 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_120.png");
    private static final ResourceLocation RADAR_OVERVIEW_OCTAGON =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_overview_octagon.png");
    private static final int BACKGROUND_TEXTURE_SIZE = 32;
    private static final int PANEL_TEXTURE_SIZE = 128;
    private static final int RADAR_SCREEN_TEXTURE_SIZE = 128;
    private static final int MIN_VISIBLE_MAP_SIZE_BLOCKS = RadarDisplayProjector.MONITOR_MAP_SIZE_BLOCKS;
    private static final int MAX_VISIBLE_MAP_SIZE_BLOCKS = 10000;
    private static final int MAP_ZOOM_STEP_BLOCKS = 250;
    private static final int GUI_GRID_LINE_COLOR = 0x2ED8FFE8;
    private static final int GRID_LOD_NEAR_LIMIT_BLOCKS = 2000;
    private static final int GRID_LOD_MID_LIMIT_BLOCKS = 5000;
    private static final int GRID_LOD_NEAR_STEP_BLOCKS = 100;
    private static final int GRID_LOD_MID_STEP_BLOCKS = 500;
    private static final int GRID_LOD_FAR_STEP_BLOCKS = 1000;
    private static final int FULL_SNAPSHOT_FALLBACK_TICKS = 100;
    private static final int RADAR_SCREEN_FRAME_PIXELS = 3;
    private static final int PANEL_SLICE = 8;
    private static final int BUTTON_TEXTURE_WIDTH = 128;
    private static final int BUTTON_TEXTURE_HEIGHT = 24;
    private static final int GUI_MARGIN = 8;
    private static final int PANEL_TEXT_PADDING = 12;
    private static final int TARGET_BUTTON_BOTTOM_MARGIN = 10;
    private static final int HUB_ROW_GAP = 7;
    private static final int HUB_BUTTON_GAP = 5;
    private static final int HUB_MODE_BUTTON_HEIGHT = 21;
    private static final int HUB_CATEGORY_BUTTON_SIZE = 24;
    private static final int HUB_CATEGORY_ICON_SIZE = 18;
    private static final int HUB_CATEGORY_ICON_TEXTURE_SIZE = 24;
    private static final int TEXT = 0xFFB8FFD2;
    private static final int TEXT_DIM = 0xFF6FAE83;
    private static final int TEXT_SUGGESTION = 0x806FAE83;
    private static final int TEXT_BAD = 0xFFFF6B6B;
    private static final int SELECTED_TARGET = 0xFFFFFFFF;
    private static final Component NO_LINK_TEXT = Component.translatable("message.power_radar.monitor.no_linked_radar");
    private static final Component INVALID_STRUCTURE_TEXT = Component.translatable("message.power_radar.monitor.invalid_structure");
    private final RadarDisplaySpriteRenderer spriteRenderer = new RadarDisplaySpriteRenderer();
    private final List<RadarBlipRenderData> blips = new ArrayList<>();
    private final List<HubClickTarget> hubClickTargets = new ArrayList<>();
    private RadarMonitorSnapshotPayload snapshot;
    private RadarMonitorDisplayData displayData;
    private int ticksSinceUpdate;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int ticksSinceSnapshot;
    private long observedClientStateVersion = Long.MIN_VALUE;
    private long lastRequestedDueScanGameTime = Long.MIN_VALUE;
    private int radarOriginX;
    private int radarOriginY;
    private int radarRadius;
    private int visibleMapSizeBlocks = MIN_VISIBLE_MAP_SIZE_BLOCKS;
    private double mapCenterOffsetX;
    private double mapCenterOffsetZ;
    private boolean draggingMap;
    private double lastDragMouseX;
    private double lastDragMouseY;
    private int spriteDrawCount;
    private String[] rightLines = new String[0];
    private String selectedTargetKey;
    private boolean targetSelectionChangedInScreen;
    private int targetButtonX;
    private int targetButtonY;
    private int targetButtonWidth;
    private int targetButtonHeight;
    private int clearTargetButtonY;
    private int whitelistButtonX;
    private int whitelistButtonY;
    private int whitelistButtonWidth;
    private int whitelistButtonHeight;
    private boolean whitelistOpen;
    private String whitelistPlayerInput = "";
    private int whitelistPlayerIndex = -1;
    private boolean whitelistPlayerSelectedByScroll;
    private int whitelistPanelX;
    private int whitelistPanelY;
    private int whitelistPanelWidth;
    private int whitelistPanelHeight;
    private int whitelistPlayerMinusX;
    private int whitelistPlayerPlusX;
    private int whitelistPlayerRowY;
    private Component hoveredHubTooltip;
    private GridCacheKey gridCacheKey;
    private List<GridLine> gridLines = List.of();
    private BlipCacheKey blipCacheKey;

    public RadarMonitorScreen(RadarMonitorSnapshotPayload snapshot) {
        super(Component.translatable("screen.power_radar.monitor.title"));
        updateSnapshot(snapshot);
    }

    public boolean isFor(BlockPos monitorPos) {
        return this.snapshot.monitorPos().equals(monitorPos);
    }

    public void updateSnapshot(RadarMonitorSnapshotPayload snapshot) {
        this.snapshot = snapshot;
        applyClientState(RadarMonitorClientState.applySnapshot(snapshot));
        this.lastRequestedDueScanGameTime = Long.MIN_VALUE;
    }

    private void refreshFromClientState() {
        RadarMonitorClientState.Entry entry = RadarMonitorClientState.get(this.snapshot.monitorPos());
        if (entry != null && entry.updateVersion() != this.observedClientStateVersion) {
            applyClientState(entry);
        }
    }

    private void applyClientState(RadarMonitorClientState.Entry entry) {
        RadarMonitorDisplayData nextDisplayData = entry.displayData();
        if (nextDisplayData == null) {
            return;
        }
        this.displayData = nextDisplayData;
        this.observedClientStateVersion = entry.updateVersion();
        this.ticksSinceSnapshot = 0;
        if (!this.targetSelectionChangedInScreen) {
            restoreManualTargetSelection();
        }
        keepSelectedTargetIfPresent();
        if (hasLayout()) {
            rebuildBlipCache();
        }
        rebuildTextCache();
    }

    @Override
    public void tick() {
        this.ticksSinceUpdate++;
        this.ticksSinceSnapshot++;
        refreshFromClientState();
        if (shouldRequestSnapshot()) {
            this.ticksSinceUpdate = 0;
            PacketDistributor.sendToServer(new RadarMonitorRequestPayload(this.snapshot.monitorPos(), this.snapshot.revision()));
        }
    }

    private boolean shouldRequestSnapshot() {
        if (this.displayData == null) {
            return this.ticksSinceUpdate >= Math.max(1, RadarConstants.RADAR_MONITOR_UPDATE_INTERVAL_TICKS);
        }
        if (!this.displayData.linked() || this.displayData.lastScanGameTime() <= 0L) {
            return this.ticksSinceUpdate >= Math.max(1, RadarConstants.RADAR_MONITOR_UPDATE_INTERVAL_TICKS);
        }
        return this.ticksSinceUpdate >= FULL_SNAPSHOT_FALLBACK_TICKS;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackgroundAsset(graphics);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.cachedWidth != this.width || this.cachedHeight != this.height || !hasLayout()) {
            rebuildLayoutCache();
        }

        this.hoveredHubTooltip = null;
        this.hubClickTargets.clear();
        drawBackgroundAsset(graphics);
        renderRadarDisplay(graphics, mouseX, mouseY, partialTick);
        updateTargetButtonBounds();
        renderHubPanel(graphics, mouseX, mouseY);
        renderTargetButton(graphics, mouseX, mouseY);
        if (this.whitelistOpen) {
            renderWhitelistOverlay(graphics, mouseX, mouseY);
        }
        if (this.hoveredHubTooltip != null) {
            graphics.renderTooltip(this.font, this.hoveredHubTooltip, mouseX, mouseY);
        }
    }

    private void renderRadarDisplay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawRadarWorkArea(graphics, partialTick);
        if (!this.displayData.monitorRendererEnabled()) {
            drawCenteredInRadarArea(graphics, Component.translatable(this.displayData.monitorElectricalState().translationKey()), TEXT_BAD);
            return;
        }
        if (!this.displayData.linked()) {
            drawCenteredInRadarArea(graphics, NO_LINK_TEXT, TEXT_BAD);
            return;
        }
        if (!this.displayData.structureValid()) {
            drawCenteredInRadarArea(graphics, INVALID_STRUCTURE_TEXT, TEXT_BAD);
            return;
        }
        this.spriteDrawCount = 0;
        RadarBlipRenderData hoveredBlip = hoveredBlip(mouseX, mouseY).orElse(null);
        for (RadarBlipRenderData blip : this.blips) {
            int alpha = blipAlpha(blip, partialTick);
            if (alpha <= 0) {
                continue;
            }
            if (isSelectedBlip(blip)) {
                this.spriteRenderer.drawLockedSelectedBlip(graphics, blip, alpha, lockedSelectedBlipDrawSize());
            } else if (blip == hoveredBlip) {
                this.spriteRenderer.drawSelectedBlip(graphics, blip, alpha, selectedBlipDrawSize());
            } else {
                this.spriteRenderer.drawBlip(graphics, blip, alpha, blipDrawSize());
            }
            this.spriteDrawCount++;
        }
    }

    private void drawRadarWorkArea(GuiGraphics graphics, float partialTick) {
        int size = this.radarRadius * 2;
        int x = this.radarOriginX - this.radarRadius;
        int y = this.radarOriginY - this.radarRadius;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(RADAR_SCREEN_BACK, x, y, size, size, 0.0F, 0.0F,
                RADAR_SCREEN_TEXTURE_SIZE, RADAR_SCREEN_TEXTURE_SIZE, RADAR_SCREEN_TEXTURE_SIZE, RADAR_SCREEN_TEXTURE_SIZE);
        int inset = radarFrameInsetPixels(size);
        int innerSize = Math.max(1, size - inset * 2);
        drawRadarGrid(graphics, x + inset, y + inset, innerSize);
        if (this.displayData != null) {
            List<RadarDisplayCoverage> coverages = this.displayData.coverages().isEmpty()
                    ? List.of(legacyCoverage())
                    : this.displayData.coverages();
            graphics.enableScissor(x + inset, y + inset, x + inset + innerSize, y + inset + innerSize);
            for (RadarDisplayCoverage coverageData : coverages) {
                drawRadarCoverage(graphics, coverageData, x + inset, y + inset, innerSize);
            }
            graphics.disableScissor();
        }
        RenderSystem.disableBlend();
    }

    private void drawRadarCoverage(GuiGraphics graphics, RadarDisplayCoverage coverageData, int x, int y, int innerSize) {
        RadarDisplayProjection radarProjection = RadarDisplayProjector.projectWorldPoint(
                this.displayData,
                coverageData.dimensionId(),
                coverageData.originX(),
                coverageData.originY(),
                coverageData.originZ(),
                viewYawDegrees(),
                visibleMapRadiusBlocks(),
                this.mapCenterOffsetX,
                this.mapCenterOffsetZ);
        ResourceLocation coverage = radarCoverageTexture(coverageData);
        if (coverage != null && radarProjection.visible() && coverageData.currentRange() > 0) {
            double contentRadius = innerSize / 2.0D;
            int coverageRadius = Math.max(1, (int) Math.round(contentRadius
                    * coverageData.currentRange()
                    / visibleMapRadiusBlocks()));
            int coverageSize = Math.max(1, coverageRadius * 2);
            int coverageCenterX = x + innerSize / 2 + (int) Math.round(radarProjection.x() * contentRadius);
            int coverageCenterY = y + innerSize / 2 + (int) Math.round(radarProjection.y() * contentRadius);
            drawRotatedRadarTexture(graphics, coverage, coverageCenterX - coverageRadius, coverageCenterY - coverageRadius,
                    coverageSize, coverageRotationDegrees(coverageData),
                    0, 0,
                    RADAR_SCREEN_TEXTURE_SIZE,
                    RADAR_SCREEN_TEXTURE_SIZE);
        }
        if (radarProjection.visible()) {
            double contentRadius = innerSize / 2.0D;
            int markerX = x + innerSize / 2 + (int) Math.round(radarProjection.x() * contentRadius);
            int markerY = y + innerSize / 2 + (int) Math.round(radarProjection.y() * contentRadius);
            graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xE0B8FFD2);
        }
    }

    private void drawRadarGrid(GuiGraphics graphics, int x, int y, int size) {
        graphics.enableScissor(x, y, x + size, y + size);
        for (GridLine line : cachedGridLines(x, y, size)) {
            if (line.vertical()) {
                drawVerticalGridLine(graphics, line.coordinate(), y, size);
            } else {
                drawHorizontalGridLine(graphics, x, line.coordinate(), size);
            }
        }
        graphics.disableScissor();
    }

    private List<GridLine> cachedGridLines(int x, int y, int size) {
        GridCacheKey key = new GridCacheKey(
                x,
                y,
                size,
                this.visibleMapSizeBlocks,
                Double.doubleToLongBits(this.mapCenterOffsetX),
                Double.doubleToLongBits(this.mapCenterOffsetZ),
                Float.floatToIntBits(viewYawDegrees()));
        if (!key.equals(this.gridCacheKey)) {
            this.gridCacheKey = key;
            this.gridLines = buildRadarGridLines(x, y, size);
        }
        return this.gridLines;
    }

    private List<GridLine> buildRadarGridLines(int x, int y, int size) {
        int stepBlocks = visibleGridCellBlocks();
        int radiusBlocks = visibleMapRadiusBlocks();
        double pixelsPerBlock = size / (double) this.visibleMapSizeBlocks;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        float viewYaw = viewYawDegrees();
        double viewRadians = Math.toRadians(viewYaw);
        double upX = Math.sin(viewRadians);
        double upZ = -Math.cos(viewRadians);
        double rightX = Math.cos(viewRadians);
        double rightZ = Math.sin(viewRadians);
        double centerHorizontal = this.mapCenterOffsetX * rightX + this.mapCenterOffsetZ * rightZ;
        double centerVertical = this.mapCenterOffsetX * upX + this.mapCenterOffsetZ * upZ;
        int minLine = (int) Math.floor((-radiusBlocks + centerHorizontal) / stepBlocks) - 1;
        int maxLine = (int) Math.ceil((radiusBlocks + centerHorizontal) / stepBlocks) + 1;
        ArrayList<GridLine> lines = new ArrayList<>();
        for (int i = minLine; i <= maxLine; i++) {
            double worldLine = i * (double) stepBlocks;
            int lineX = centerX + (int) Math.round((worldLine - centerHorizontal) * pixelsPerBlock);
            lines.add(new GridLine(true, lineX));
        }
        minLine = (int) Math.floor((-radiusBlocks + centerVertical) / stepBlocks) - 1;
        maxLine = (int) Math.ceil((radiusBlocks + centerVertical) / stepBlocks) + 1;
        for (int i = minLine; i <= maxLine; i++) {
            double worldLine = i * (double) stepBlocks;
            int lineY = centerY - (int) Math.round((worldLine - centerVertical) * pixelsPerBlock);
            lines.add(new GridLine(false, lineY));
        }
        return List.copyOf(lines);
    }

    private static void drawVerticalGridLine(GuiGraphics graphics, int lineX, int y, int size) {
        graphics.fill(lineX, y, lineX + 1, y + size, GUI_GRID_LINE_COLOR);
    }

    private static void drawHorizontalGridLine(GuiGraphics graphics, int x, int lineY, int size) {
        graphics.fill(x, lineY, x + size, lineY + 1, GUI_GRID_LINE_COLOR);
    }

    private void drawRotatedRadarTexture(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int size,
            float rotationDegrees,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
        graphics.pose().translate(-size / 2.0F, -size / 2.0F, 0.0F);
        drawTexturedGuiQuad(
                graphics,
                texture,
                0,
                0,
                size,
                size,
                sourceX / (float) RADAR_SCREEN_TEXTURE_SIZE,
                (sourceX + sourceWidth) / (float) RADAR_SCREEN_TEXTURE_SIZE,
                sourceY / (float) RADAR_SCREEN_TEXTURE_SIZE,
                (sourceY + sourceHeight) / (float) RADAR_SCREEN_TEXTURE_SIZE);
        graphics.pose().popPose();
    }

    private static void drawTexturedGuiQuad(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            float minU,
            float maxU,
            float minV,
            float maxV
    ) {
        graphics.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x, y, 0.0F).setUv(minU, minV).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, x, y + height, 0.0F).setUv(minU, maxV).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, x + width, y + height, 0.0F).setUv(maxU, maxV).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, x + width, y, 0.0F).setUv(maxU, minV).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private ResourceLocation phasedArrayConeTexture() {
        if (this.displayData == null
                || this.displayData.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return null;
        }
        int sectorAngle = this.displayData.sectorAngle();
        if (sectorAngle <= 60) {
            return RADAR_SWEEP_CONE_60;
        }
        if (sectorAngle <= 90) {
            return RADAR_SWEEP_CONE_90;
        }
        return RADAR_SWEEP_CONE_120;
    }

    private ResourceLocation phasedArrayConeTexture(RadarDisplayCoverage coverage) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return null;
        }
        int sectorAngle = coverage.sectorAngle();
        if (sectorAngle <= 60) {
            return RADAR_SWEEP_CONE_60;
        }
        if (sectorAngle <= 90) {
            return RADAR_SWEEP_CONE_90;
        }
        return RADAR_SWEEP_CONE_120;
    }

    private ResourceLocation radarCoverageTexture() {
        if (this.displayData == null) {
            return null;
        }
        if (this.displayData.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return RADAR_OVERVIEW_OCTAGON;
        }
        return phasedArrayConeTexture();
    }

    private ResourceLocation radarCoverageTexture(RadarDisplayCoverage coverage) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return RADAR_OVERVIEW_OCTAGON;
        }
        return phasedArrayConeTexture(coverage);
    }

    private boolean isOverviewRadarActive() {
        return this.displayData != null
                && this.displayData.linked()
                && this.displayData.structureValid()
                && this.displayData.orientationState().structureType() == RadarStructureType.OVERVIEW;
    }

    private float coverageRotationDegrees() {
        if (this.displayData.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return 0.0F;
        }
        double gameTime = this.displayData.serverGameTime() + this.ticksSinceSnapshot;
        float radarYaw = this.displayData.orientationState().yawAt(gameTime);
        return RadarGeometry.relativeDegrees(radarYaw, viewYawDegrees());
    }

    private float coverageRotationDegrees(RadarDisplayCoverage coverage) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return 0.0F;
        }
        double gameTime = this.displayData.serverGameTime() + this.ticksSinceSnapshot;
        float radarYaw = coverage.orientationState().yawAt(gameTime);
        return RadarGeometry.relativeDegrees(radarYaw, viewYawDegrees());
    }

    private RadarDisplayCoverage legacyCoverage() {
        return new RadarDisplayCoverage(
                this.displayData.radarId(),
                this.displayData.controllerPos(),
                this.displayData.radarDimensionId(),
                this.displayData.radarOriginX(),
                this.displayData.radarOriginY(),
                this.displayData.radarOriginZ(),
                this.displayData.orientationState(),
                this.displayData.currentRange(),
                this.displayData.sectorAngle());
    }

    private float viewYawDegrees() {
        return this.displayData == null
                ? RadarGeometry.yawDegrees(net.minecraft.core.Direction.NORTH)
                : RadarMonitorViewOrientation.viewYawDegrees(this.displayData);
    }

    private void renderHubPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int margin = GUI_MARGIN;
        int panelWidth = sidePanelWidth();
        int panelX = this.width - margin - panelWidth;
        int panelY = margin;
        int panelHeight = this.height - margin * 2;
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
        int textPadding = PANEL_TEXT_PADDING;
        int contentX = panelX + textPadding;
        int contentWidth = panelWidth - textPadding * 2;
        int y = panelY + textPadding;

        y = renderModeButtons(graphics, mouseX, mouseY, contentX, y, contentWidth);
        y += HUB_ROW_GAP;
        y = renderCategoryButtons(graphics, mouseX, mouseY, contentX, y, contentWidth,
                Component.translatable("message.power_radar.monitor.detection_filter"), false);
        y += HUB_ROW_GAP;
        y = renderCategoryButtons(graphics, mouseX, mouseY, contentX, y, contentWidth,
                Component.translatable("message.power_radar.monitor.target_filter"), true);
        y += HUB_ROW_GAP + 2;
        y = renderTrajectoryButtons(graphics, mouseX, mouseY, contentX, y, contentWidth);
        y += HUB_ROW_GAP + 2;

        String[] lines = selectedTarget()
                .map(this::selectedTargetLines)
                .orElse(this.rightLines);
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 && selectedTarget().isPresent() ? SELECTED_TARGET : TEXT_DIM;
            graphics.drawString(this.font, fitLine(lines[i], contentWidth), contentX, y, color, false);
            y += 11;
        }
        y += 4;
        this.whitelistButtonX = contentX;
        this.whitelistButtonY = y;
        this.whitelistButtonWidth = contentWidth;
        this.whitelistButtonHeight = 20;
        renderSmallButton(graphics, mouseX, mouseY, this.whitelistButtonX, this.whitelistButtonY,
                this.whitelistButtonWidth, this.whitelistButtonHeight,
                Component.translatable("message.power_radar.monitor.whitelist"), false, true);
    }

    private void rebuildLayoutCache() {
        this.cachedWidth = this.width;
        this.cachedHeight = this.height;
        int panelReserve = sidePanelWidth() + GUI_MARGIN * 3;
        int availableWidth = Math.max(80, this.width - panelReserve);
        int availableHeight = Math.max(80, this.height - GUI_MARGIN * 2);
        int maxDiameter = Math.max(80, Math.min(availableWidth, availableHeight));
        int texturePixelScale = Math.max(1, maxDiameter / RADAR_SCREEN_TEXTURE_SIZE);
        this.radarRadius = RADAR_SCREEN_TEXTURE_SIZE * texturePixelScale / 2;
        this.radarOriginX = GUI_MARGIN + availableWidth / 2;
        this.radarOriginY = this.height / 2 + 4;
        rebuildBlipCache();
        rebuildTextCache();
        updateTargetButtonBounds();
    }

    private void rebuildBlipCache() {
        BlipCacheKey nextKey = blipCacheKey();
        if (nextKey.equals(this.blipCacheKey)) {
            return;
        }
        this.blipCacheKey = nextKey;
        this.blips.clear();
        if (!hasLayout() || !this.displayData.monitorRendererEnabled() || !this.displayData.linked() || !this.displayData.structureValid()) {
            if (PowerRadarDebugOptions.scanOptimizationLogging()) {
                PowerRadar.LOGGER.info(
                        "[PowerRadar BugReport][MonitorScreen] hidden layout={} renderer={} linked={} structureValid={} targets={} range={}",
                        hasLayout(),
                        this.displayData.monitorRendererEnabled(),
                        this.displayData.linked(),
                        this.displayData.structureValid(),
                        this.displayData.targets().size(),
                        this.displayData.currentRange()
                );
            }
            return;
        }
        int targetIndex = 0;
        for (RadarDisplayTarget target : this.displayData.targets()) {
            addBlip(target, targetIndex++);
        }
        if (PowerRadarDebugOptions.scanOptimizationLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][MonitorScreen] monitor={} structure={} targets={} blips={} range={} vertical={} mode={} interval={} lastScan={} snapshotRevision={}",
                    this.displayData.monitorPos(),
                    this.displayData.orientationState().structureType(),
                    this.displayData.targets().size(),
                    this.blips.size(),
                    this.displayData.currentRange(),
                    this.displayData.verticalScanHeight(),
                    this.displayData.mode(),
                    this.displayData.trackUpdateIntervalTicks(),
                    this.displayData.lastScanGameTime(),
                    this.snapshot.revision()
            );
        }
    }

    private BlipCacheKey blipCacheKey() {
        return new BlipCacheKey(
                this.observedClientStateVersion,
                this.radarOriginX,
                this.radarOriginY,
                this.radarRadius,
                this.visibleMapSizeBlocks,
                Double.doubleToLongBits(this.mapCenterOffsetX),
                Double.doubleToLongBits(this.mapCenterOffsetZ),
                Float.floatToIntBits(viewYawDegrees()),
                this.displayData == null ? 0 : this.displayData.targets().size());
    }

    private void addBlip(RadarDisplayTarget target, int targetIndex) {
        if (!target.dimensionId().equals(this.displayData.radarDimensionId())) {
            return;
        }

        RadarDisplayProjection projection = RadarDisplayProjector.project(
                this.displayData,
                target,
                viewYawDegrees(),
                visibleMapRadiusBlocks(),
                this.mapCenterOffsetX,
                this.mapCenterOffsetZ);
        if (!projection.visible()) {
            return;
        }

        int safeBlipHalfSize = (lockedSelectedBlipDrawSize() + 1) / 2;
        double contentRadius = Math.max(1.0D,
                this.radarRadius - radarFrameInsetPixels(this.radarRadius * 2) - safeBlipHalfSize);
        int x = this.radarOriginX + (int) Math.round(projection.x() * contentRadius);
        int y = this.radarOriginY + (int) Math.round(projection.y() * contentRadius);
        String stableKey = target.stableSelectionKey();
        this.blips.add(new RadarBlipRenderData(stableKey, x, y, 0xFFFFFFFF, projection.radialFraction(), target.category(), targetIndex, target.displayAgeTicks()));
    }

    private void rebuildTextCache() {
        this.rightLines = new String[] {
                Component.translatable("power_radar.electrical.state", Component.translatable(this.displayData.monitorElectricalState().translationKey())).getString(),
                Component.translatable("power_radar.electrical.screen_size", this.displayData.monitorScreenSize() + "x" + this.displayData.monitorScreenSize()).getString(),
                "map: " + this.visibleMapSizeBlocks + "x" + this.visibleMapSizeBlocks + "m",
                "grid: " + visibleGridCellBlocks() + "m",
                "radar range: " + this.displayData.currentRange() + "m",
                "targets: " + this.displayData.targets().size()
        };
    }

    private int blipAlpha(RadarBlipRenderData blip, float partialTick) {
        int fadeDelayTicks = Math.max(0, RadarConstants.RADAR_MONITOR_BLIP_FADE_DELAY_TICKS);
        int fadeDurationTicks = Math.max(1, RadarConstants.RADAR_MONITOR_BLIP_FADE_TICKS);
        int fadeTicks = fadeDelayTicks + fadeDurationTicks;
        double ageTicks = Math.max(0, blip.displayAgeTicks());
        if (ageTicks > 0.0) {
            ageTicks += this.ticksSinceSnapshot + partialTick;
        }
        if (ageTicks <= fadeDelayTicks) {
            return 255;
        }
        if (ageTicks >= fadeTicks) {
            return 0;
        }
        return Math.max(0, Math.min(255, (int) Math.round(255.0 * (fadeTicks - ageTicks) / fadeDurationTicks)));
    }

    private int blipDrawSize() {
        double scale = Math.sqrt((double) MIN_VISIBLE_MAP_SIZE_BLOCKS / Math.max(MIN_VISIBLE_MAP_SIZE_BLOCKS, this.visibleMapSizeBlocks));
        return Math.max(4, Math.min(RadarConstants.GUI_BLIP_DRAW_SIZE,
                (int) Math.round(RadarConstants.GUI_BLIP_DRAW_SIZE * scale)));
    }

    private int selectedBlipDrawSize() {
        return Math.max(blipDrawSize(), blipDrawSize() + selectedBlipSizeBonus());
    }

    private int lockedSelectedBlipDrawSize() {
        return selectedBlipDrawSize() + selectedBlipSizeBonus();
    }

    private int selectedBlipSizeBonus() {
        return Math.max(1, (int) Math.round(2.0D * blipDrawSize() / RadarConstants.GUI_BLIP_DRAW_SIZE));
    }

    private boolean hasLayout() {
        return this.radarRadius > 0;
    }

    private static int radarFrameInsetPixels(int renderedSize) {
        return (int) Math.ceil(renderedSize * (double) RADAR_SCREEN_FRAME_PIXELS / RADAR_SCREEN_TEXTURE_SIZE);
    }

    private int visibleMapRadiusBlocks() {
        return Math.max(1, this.visibleMapSizeBlocks / 2);
    }

    private int visibleGridCellBlocks() {
        if (this.visibleMapSizeBlocks <= GRID_LOD_NEAR_LIMIT_BLOCKS) {
            return GRID_LOD_NEAR_STEP_BLOCKS;
        }
        if (this.visibleMapSizeBlocks <= GRID_LOD_MID_LIMIT_BLOCKS) {
            return GRID_LOD_MID_STEP_BLOCKS;
        }
        return GRID_LOD_FAR_STEP_BLOCKS;
    }

    private boolean isMouseOverRadar(double mouseX, double mouseY) {
        int size = this.radarRadius * 2;
        int x = this.radarOriginX - this.radarRadius;
        int y = this.radarOriginY - this.radarRadius;
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    private boolean adjustMapZoom(double scrollY) {
        if (scrollY == 0.0D) {
            return false;
        }
        int previous = this.visibleMapSizeBlocks;
        int direction = scrollY < 0.0D ? 1 : -1;
        int steps = Math.max(1, (int) Math.round(Math.abs(scrollY)));
        this.visibleMapSizeBlocks = clampMapSize(this.visibleMapSizeBlocks + direction * MAP_ZOOM_STEP_BLOCKS * steps);
        if (this.visibleMapSizeBlocks == previous) {
            return true;
        }
        clampMapCenter();
        rebuildBlipCache();
        rebuildTextCache();
        return true;
    }

    private static int clampMapSize(int value) {
        int clamped = Math.max(MIN_VISIBLE_MAP_SIZE_BLOCKS, Math.min(MAX_VISIBLE_MAP_SIZE_BLOCKS, value));
        int cell = Math.max(1, RadarDisplayProjector.MONITOR_GRID_CELL_BLOCKS);
        return Math.max(MIN_VISIBLE_MAP_SIZE_BLOCKS, Math.round(clamped / (float) cell) * cell);
    }

    private void panMapByPixels(double deltaX, double deltaY) {
        int size = this.radarRadius * 2;
        int inset = radarFrameInsetPixels(size);
        int innerSize = Math.max(1, size - inset * 2);
        double blocksPerPixel = this.visibleMapSizeBlocks / (double) innerSize;
        float viewYaw = viewYawDegrees();
        double viewRadians = Math.toRadians(viewYaw);
        double upX = Math.sin(viewRadians);
        double upZ = -Math.cos(viewRadians);
        double rightX = Math.cos(viewRadians);
        double rightZ = Math.sin(viewRadians);
        double horizontalBlocks = -deltaX * blocksPerPixel;
        double verticalBlocks = deltaY * blocksPerPixel;
        this.mapCenterOffsetX += rightX * horizontalBlocks + upX * verticalBlocks;
        this.mapCenterOffsetZ += rightZ * horizontalBlocks + upZ * verticalBlocks;
        clampMapCenter();
        rebuildBlipCache();
    }

    private void clampMapCenter() {
        double maxOffset = Math.max(0.0D, (MAX_VISIBLE_MAP_SIZE_BLOCKS - this.visibleMapSizeBlocks) / 2.0D);
        this.mapCenterOffsetX = clamp(this.mapCenterOffsetX, -maxOffset, maxOffset);
        this.mapCenterOffsetZ = clamp(this.mapCenterOffsetZ, -maxOffset, maxOffset);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && activateHubButton(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && targetButtonVisible() && targetButtonHovered(mouseX, mouseY)) {
            selectedTarget().ifPresent(target -> {
                if (target.targetUuid() != null) {
                    PacketDistributor.sendToServer(new RadarMonitorTargetSelectionPayload(this.snapshot.monitorPos(), target.targetUuid()));
                }
            });
            return true;
        }
        if (button == 0 && clearTargetButtonVisible() && clearTargetButtonHovered(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new RadarMonitorTargetSelectionPayload(this.snapshot.monitorPos(), null));
            this.selectedTargetKey = null;
            this.targetSelectionChangedInScreen = true;
            return true;
        }
        if (button == 0 && whitelistButtonVisible() && whitelistButtonHovered(mouseX, mouseY)) {
            this.whitelistOpen = true;
            this.whitelistPlayerInput = "";
            this.whitelistPlayerIndex = -1;
            this.whitelistPlayerSelectedByScroll = false;
            return true;
        }
        if (button == 0 && this.whitelistOpen && handleWhitelistClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && selectTargetAt(mouseX, mouseY)) {
            updateTargetButtonBounds();
            return true;
        }
        if (button == 0 && isMouseOverRadar(mouseX, mouseY)) {
            this.draggingMap = true;
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingMap) {
            panMapByPixels(mouseX - this.lastDragMouseX, mouseY - this.lastDragMouseY);
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingMap) {
            this.draggingMap = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean activateHubButton(double mouseX, double mouseY) {
        for (HubClickTarget target : this.hubClickTargets) {
            if (!target.contains(mouseX, mouseY)) {
                continue;
            }
            if (target.mode() != null) {
                sendSettings(target.mode(), this.displayData.detectionFilterMask(), this.displayData.autotargetFilterMask(), this.displayData.targetTrajectoryMode());
                return true;
            }
            if (target.category() != null) {
                int bit = RadarDetectionFilters.bit(target.category().category());
                if (bit == 0) {
                    return true;
                }
                if (target.targetFilter()) {
                    int nextMask = this.displayData.autotargetFilterMask() ^ bit;
                    sendSettings(this.displayData.mode(), this.displayData.detectionFilterMask(), nextMask, this.displayData.targetTrajectoryMode());
                } else {
                    int nextMask = this.displayData.detectionFilterMask() ^ bit;
                    sendSettings(this.displayData.mode(), nextMask, this.displayData.autotargetFilterMask(), this.displayData.targetTrajectoryMode());
                }
                return true;
            }
            if (target.trajectoryMode() != null) {
                sendSettings(this.displayData.mode(), this.displayData.detectionFilterMask(), this.displayData.autotargetFilterMask(), target.trajectoryMode());
                return true;
            }
        }
        return false;
    }

    private void sendSettings(RadarScanMode mode, int detectionFilterMask, int autotargetFilterMask, TargetTrajectoryMode targetTrajectoryMode) {
        PacketDistributor.sendToServer(new RadarMonitorSettingsPayload(
                this.snapshot.monitorPos(),
                mode,
                RadarDetectionFilters.sanitize(detectionFilterMask),
                RadarDetectionFilters.sanitize(autotargetFilterMask),
                targetTrajectoryMode));
    }

    private boolean selectTargetAt(double mouseX, double mouseY) {
        Optional<RadarBlipRenderData> nearest = hoveredBlip(mouseX, mouseY);
        if (nearest.isEmpty()) {
            return false;
        }
        RadarDisplayTarget target = this.displayData.targets().get(nearest.get().targetIndex());
        this.selectedTargetKey = target.stableSelectionKey();
        this.targetSelectionChangedInScreen = true;
        return true;
    }

    private Optional<RadarBlipRenderData> hoveredBlip(double mouseX, double mouseY) {
        RadarBlipRenderData nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        double hitRadius = Math.max(5.0, lockedSelectedBlipDrawSize() * 0.75);
        double hitRadiusSq = hitRadius * hitRadius;
        for (RadarBlipRenderData blip : this.blips) {
            if (blip.targetIndex() < 0 || blip.targetIndex() >= this.displayData.targets().size()) {
                continue;
            }
            double dx = mouseX - blip.screenX();
            double dy = mouseY - blip.screenY();
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= hitRadiusSq && distanceSq < nearestDistanceSq) {
                nearest = blip;
                nearestDistanceSq = distanceSq;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private void keepSelectedTargetIfPresent() {
        if (this.selectedTargetKey == null) {
            return;
        }
        if (selectedTarget().isEmpty()) {
            this.selectedTargetKey = null;
        }
    }

    private void restoreManualTargetSelection() {
        if (this.displayData == null || this.displayData.manualTargetUuid() == null) {
            return;
        }
        String manualKey = "uuid:" + this.displayData.manualTargetUuid();
        boolean present = this.displayData.targets().stream()
                .anyMatch(target -> manualKey.equals(target.stableSelectionKey()));
        if (present) {
            this.selectedTargetKey = manualKey;
        }
    }

    private Optional<RadarDisplayTarget> selectedTarget() {
        if (this.selectedTargetKey == null || this.displayData == null) {
            return Optional.empty();
        }
        return this.displayData.targets().stream()
                .filter(target -> this.selectedTargetKey.equals(target.stableSelectionKey()))
                .findFirst();
    }

    private String[] selectedTargetLines(RadarDisplayTarget target) {
        return new String[] {
                Component.translatable("message.power_radar.monitor.selected_target").getString(),
                Component.translatable("message.power_radar.monitor.target_name", targetName(target)).getString(),
                Component.translatable("message.power_radar.monitor.target_category", categoryText(target.category())).getString(),
                Component.translatable("message.power_radar.monitor.target_position", rounded(target.x()), rounded(target.y()), rounded(target.z())).getString(),
                Component.translatable("message.power_radar.monitor.target_velocity", velocityText(target)).getString()
        };
    }

    private String targetName(RadarDisplayTarget target) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            return target.displayName();
        }
        return target.entityTypeId().getPath();
    }

    private String categoryText(com.limbo2136.powerradar.radar.RadarTargetCategory category) {
        return Component.translatable("message.power_radar.monitor.category." + category.name().toLowerCase(java.util.Locale.ROOT)).getString();
    }

    private String velocityText(RadarDisplayTarget target) {
        if (!target.hasVelocity()) {
            return "-";
        }
        double blocksPerSecond = Math.sqrt(
                target.velocityX() * target.velocityX()
                        + target.velocityY() * target.velocityY()
                        + target.velocityZ() * target.velocityZ()
        ) * 20.0D;
        return rounded(blocksPerSecond) + " m/s";
    }

    private static String rounded(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String fitLine(String line, int maxWidth) {
        if (this.font.width(line) <= maxWidth) {
            return line;
        }
        return this.font.plainSubstrByWidth(line, Math.max(12, maxWidth - this.font.width("..."))) + "...";
    }

    private int sidePanelWidth() {
        return Math.min(220, Math.max(168, this.width / 5));
    }

    private boolean isSelectedBlip(RadarBlipRenderData blip) {
        if (this.selectedTargetKey == null
                || blip.targetIndex() < 0
                || blip.targetIndex() >= this.displayData.targets().size()) {
            return false;
        }
        RadarDisplayTarget target = this.displayData.targets().get(blip.targetIndex());
        return this.selectedTargetKey.equals(target.stableSelectionKey());
    }

    private void updateTargetButtonBounds() {
        Optional<RadarDisplayTarget> selected = selectedTarget();
        int margin = GUI_MARGIN;
        int panelWidth = sidePanelWidth();
        this.targetButtonWidth = panelWidth - PANEL_TEXT_PADDING * 2;
        this.targetButtonHeight = 20;
        this.targetButtonX = this.width - margin - this.targetButtonWidth;
        this.clearTargetButtonY = this.height - TARGET_BUTTON_BOTTOM_MARGIN - this.targetButtonHeight;
        this.targetButtonY = this.clearTargetButtonY - HUB_BUTTON_GAP - this.targetButtonHeight;
    }

    private int renderModeButtons(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.mode"), x, y, TEXT_DIM, false);
        y += 12;
        int buttonWidth = Math.max(40, (width - HUB_BUTTON_GAP * 2) / 3);
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_air"),
                this.displayData.mode() == RadarScanMode.SKY,
                true);
        this.hubClickTargets.add(HubClickTarget.mode(x, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT, RadarScanMode.SKY));
        renderSmallButton(graphics, mouseX, mouseY, x + buttonWidth + HUB_BUTTON_GAP, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_general"),
                this.displayData.mode() == RadarScanMode.GROUND,
                true);
        this.hubClickTargets.add(HubClickTarget.mode(x + buttonWidth + HUB_BUTTON_GAP, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT, RadarScanMode.GROUND));
        renderSmallButton(graphics, mouseX, mouseY, x + (buttonWidth + HUB_BUTTON_GAP) * 2, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.mode_ground"),
                this.displayData.mode() == RadarScanMode.SURFACE_SCANNER,
                true);
        this.hubClickTargets.add(HubClickTarget.mode(x + (buttonWidth + HUB_BUTTON_GAP) * 2, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT, RadarScanMode.SURFACE_SCANNER));
        return y + HUB_MODE_BUTTON_HEIGHT;
    }

    private int renderCategoryButtons(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            Component title,
            boolean targetFilter
    ) {
        graphics.drawString(this.font, title, x, y, TEXT_DIM, false);
        y += 12;
        HubCategory[] categories = targetFilter ? HubCategory.targetValues() : HubCategory.values();
        int gap = Math.max(3, Math.min(HUB_BUTTON_GAP, (width - HUB_CATEGORY_BUTTON_SIZE * categories.length) / Math.max(1, categories.length - 1)));
        int totalWidth = HUB_CATEGORY_BUTTON_SIZE * categories.length + gap * Math.max(0, categories.length - 1);
        int startX = x + Math.max(0, (width - totalWidth) / 2);
        for (int i = 0; i < categories.length; i++) {
            HubCategory category = categories[i];
            boolean enabled = category.enabledFor(this.displayData.mode(), targetFilter);
            boolean selected = RadarDetectionFilters.enabled(
                    targetFilter ? this.displayData.autotargetFilterMask() : this.displayData.detectionFilterMask(),
                    category.category());
            renderSquareButton(graphics, mouseX, mouseY, startX + i * (HUB_CATEGORY_BUTTON_SIZE + gap), y,
                    category, enabled, selected);
            if (enabled) {
                this.hubClickTargets.add(HubClickTarget.category(startX + i * (HUB_CATEGORY_BUTTON_SIZE + gap), y,
                        HUB_CATEGORY_BUTTON_SIZE, HUB_CATEGORY_BUTTON_SIZE, category, targetFilter));
            }
        }
        return y + HUB_CATEGORY_BUTTON_SIZE;
    }

    private int renderTrajectoryButtons(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.trajectory"), x, y, TEXT_DIM, false);
        y += 12;
        int buttonWidth = Math.max(56, (width - HUB_BUTTON_GAP) / 2);
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.trajectory_flat"),
                this.displayData.targetTrajectoryMode() != TargetTrajectoryMode.HIGH_ARC,
                true);
        this.hubClickTargets.add(HubClickTarget.trajectory(x, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT, TargetTrajectoryMode.FLAT));
        renderSmallButton(graphics, mouseX, mouseY, x + buttonWidth + HUB_BUTTON_GAP, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT,
                Component.translatable("message.power_radar.monitor.trajectory_high"),
                this.displayData.targetTrajectoryMode() == TargetTrajectoryMode.HIGH_ARC,
                true);
        this.hubClickTargets.add(HubClickTarget.trajectory(x + buttonWidth + HUB_BUTTON_GAP, y, buttonWidth, HUB_MODE_BUTTON_HEIGHT, TargetTrajectoryMode.HIGH_ARC));
        return y + HUB_MODE_BUTTON_HEIGHT;
    }

    private void renderSmallButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height,
            Component label,
            boolean selected,
            boolean enabled
    ) {
        ResourceLocation texture = buttonTexture(x, y, width, height, mouseX, mouseY, selected, enabled);
        drawNineSlice(graphics, texture, x, y, width, height, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        int color = enabled ? (selected ? SELECTED_TARGET : TEXT) : TEXT_DIM;
        graphics.drawCenteredString(this.font, fitLine(label.getString(), width - 8), x + width / 2, y + (height - 8) / 2, color);
    }

    private void renderSquareButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            HubCategory category,
            boolean enabled,
            boolean selected
    ) {
        ResourceLocation texture = buttonTexture(x, y, HUB_CATEGORY_BUTTON_SIZE, HUB_CATEGORY_BUTTON_SIZE, mouseX, mouseY, selected, enabled);
        drawNineSlice(graphics, texture, x, y, HUB_CATEGORY_BUTTON_SIZE, HUB_CATEGORY_BUTTON_SIZE,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        if (mouseX >= x && mouseX < x + HUB_CATEGORY_BUTTON_SIZE && mouseY >= y && mouseY < y + HUB_CATEGORY_BUTTON_SIZE) {
            this.hoveredHubTooltip = category.tooltip();
        }
        int iconX = x + (HUB_CATEGORY_BUTTON_SIZE - HUB_CATEGORY_ICON_SIZE) / 2;
        int iconY = y + (HUB_CATEGORY_BUTTON_SIZE - HUB_CATEGORY_ICON_SIZE) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, enabled ? 1.0F : 0.38F);
        graphics.blit(category.icon(), iconX, iconY, HUB_CATEGORY_ICON_SIZE, HUB_CATEGORY_ICON_SIZE,
                0.0F, 0.0F, HUB_CATEGORY_ICON_TEXTURE_SIZE, HUB_CATEGORY_ICON_TEXTURE_SIZE,
                HUB_CATEGORY_ICON_TEXTURE_SIZE, HUB_CATEGORY_ICON_TEXTURE_SIZE);
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

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawNineSlice(graphics, SIDE_PANEL, x, y, width, height, PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE, PANEL_SLICE);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int color) {
        graphics.drawCenteredString(this.font, text, this.width / 2, this.height / 2, color);
    }

    private void drawCenteredInRadarArea(GuiGraphics graphics, Component text, int color) {
        graphics.drawCenteredString(this.font, text, this.radarOriginX, this.radarOriginY, color);
    }

    private void drawBackgroundAsset(GuiGraphics graphics) {
        graphics.blit(GUI_BACKGROUND, 0, 0, this.width, this.height, 0.0F, 0.0F,
                BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE);
    }

    private void renderTargetButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!targetButtonVisible()) {
            return;
        }
        ResourceLocation texture = targetButtonHovered(mouseX, mouseY) ? BUTTON_HOVERED : BUTTON;
        drawNineSlice(graphics, texture, this.targetButtonX, this.targetButtonY, this.targetButtonWidth, this.targetButtonHeight,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        Component label = Component.translatable("message.power_radar.monitor.target_select_action");
        int textX = this.targetButtonX + (this.targetButtonWidth - this.font.width(label)) / 2;
        int textY = this.targetButtonY + (this.targetButtonHeight - 8) / 2;
        graphics.drawString(this.font, label, textX, textY, TEXT, false);

        ResourceLocation clearTexture = clearTargetButtonHovered(mouseX, mouseY) ? BUTTON_HOVERED : BUTTON;
        drawNineSlice(graphics, clearTexture, this.targetButtonX, this.clearTargetButtonY, this.targetButtonWidth, this.targetButtonHeight,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT, 6);
        Component clearLabel = Component.translatable("message.power_radar.monitor.target_clear_action");
        int clearTextX = this.targetButtonX + (this.targetButtonWidth - this.font.width(clearLabel)) / 2;
        int clearTextY = this.clearTargetButtonY + (this.targetButtonHeight - 8) / 2;
        graphics.drawString(this.font, clearLabel, clearTextX, clearTextY, TEXT_DIM, false);
    }

    private boolean targetButtonVisible() {
        return selectedTarget().isPresent();
    }

    private boolean clearTargetButtonVisible() {
        return selectedTarget().isPresent();
    }

    private boolean whitelistButtonVisible() {
        return true;
    }

    private boolean targetButtonHovered(double mouseX, double mouseY) {
        return mouseX >= this.targetButtonX
                && mouseX < this.targetButtonX + this.targetButtonWidth
                && mouseY >= this.targetButtonY
                && mouseY < this.targetButtonY + this.targetButtonHeight;
    }

    private boolean clearTargetButtonHovered(double mouseX, double mouseY) {
        return mouseX >= this.targetButtonX
                && mouseX < this.targetButtonX + this.targetButtonWidth
                && mouseY >= this.clearTargetButtonY
                && mouseY < this.clearTargetButtonY + this.targetButtonHeight;
    }

    private boolean whitelistButtonHovered(double mouseX, double mouseY) {
        return mouseX >= this.whitelistButtonX
                && mouseX < this.whitelistButtonX + this.whitelistButtonWidth
                && mouseY >= this.whitelistButtonY
                && mouseY < this.whitelistButtonY + this.whitelistButtonHeight;
    }

    private void renderWhitelistOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        this.whitelistPanelWidth = Math.min(330, Math.max(260, this.width / 3));
        this.whitelistPanelHeight = 176;
        this.whitelistPanelX = (this.width - this.whitelistPanelWidth) / 2;
        this.whitelistPanelY = (this.height - this.whitelistPanelHeight) / 2;
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        drawPanel(graphics, this.whitelistPanelX, this.whitelistPanelY, this.whitelistPanelWidth, this.whitelistPanelHeight);
        int x = this.whitelistPanelX + 12;
        int y = this.whitelistPanelY + 12;
        int rowWidth = this.whitelistPanelWidth - 24;
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.whitelist"), x, y, SELECTED_TARGET, false);
        y += 20;
        this.whitelistPlayerRowY = y;
        renderPlayerWhitelistRow(graphics, mouseX, mouseY, x, y, rowWidth);
        y += 28;
        renderWhitelistRow(graphics, mouseX, mouseY, x, y, rowWidth,
                Component.translatable("message.power_radar.monitor.whitelist.sable").getString(), "-", false);
        y += 34;
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.whitelist.players").getString(), x, y, TEXT_DIM, false);
        graphics.drawString(this.font, Component.translatable("message.power_radar.monitor.whitelist.sable").getString(), x + rowWidth / 2, y, TEXT_DIM, false);
        y += 12;
        for (int i = 0; i < Math.min(5, this.displayData.whitelistedPlayerNames().size()); i++) {
            graphics.drawString(this.font, fitLine(this.displayData.whitelistedPlayerNames().get(i), rowWidth / 2 - 8), x, y + i * 10, TEXT, false);
        }
        for (int i = 0; i < Math.min(5, this.displayData.whitelistedSableNames().size()); i++) {
            graphics.drawString(this.font, fitLine(this.displayData.whitelistedSableNames().get(i), rowWidth / 2 - 8),
                    x + rowWidth / 2, y + i * 10, TEXT, false);
        }
    }

    private void renderWhitelistRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, String label, String value, boolean enabled) {
        int buttonSize = 20;
        if (enabled) {
            this.whitelistPlayerMinusX = x;
            this.whitelistPlayerPlusX = x + width - buttonSize;
        }
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonSize, buttonSize, Component.literal("-"), false, enabled);
        renderSmallButton(graphics, mouseX, mouseY, x + width - buttonSize, y, buttonSize, buttonSize, Component.literal("+"), false, enabled);
        graphics.drawString(this.font, label, x + buttonSize + 6, y + 1, enabled ? TEXT_DIM : TEXT_BAD, false);
        graphics.drawString(this.font, fitLine(value == null ? "" : value, width - buttonSize * 2 - 12),
                x + buttonSize + 6, y + 11, enabled ? TEXT : TEXT_DIM, false);
    }

    private void renderPlayerWhitelistRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        int buttonSize = 20;
        this.whitelistPlayerMinusX = x;
        this.whitelistPlayerPlusX = x + width - buttonSize;
        renderSmallButton(graphics, mouseX, mouseY, x, y, buttonSize, buttonSize, Component.literal("-"), false, true);
        renderSmallButton(graphics, mouseX, mouseY, x + width - buttonSize, y, buttonSize, buttonSize, Component.literal("+"), false, true);
        int textX = x + buttonSize + 6;
        int maxTextWidth = width - buttonSize * 2 - 12;
        graphics.drawString(this.font,
                Component.translatable("message.power_radar.monitor.whitelist.players"),
                textX, y + 1, TEXT_DIM, false);

        if (this.whitelistPlayerSelectedByScroll) {
            graphics.drawString(this.font, fitLine(currentWhitelistCandidate(), maxTextWidth), textX, y + 11, TEXT, false);
            return;
        }

        String input = this.whitelistPlayerInput == null ? "" : this.whitelistPlayerInput;
        String visibleInput = this.font.plainSubstrByWidth(input, maxTextWidth);
        graphics.drawString(this.font, visibleInput, textX, y + 11, TEXT, false);
        String suggestion = suggestedWhitelistPlayer();
        if (!input.isEmpty() && suggestion.regionMatches(true, 0, input, 0, input.length())) {
            String suffix = suggestion.substring(input.length());
            int suffixX = textX + this.font.width(visibleInput);
            int remainingWidth = Math.max(0, maxTextWidth - this.font.width(visibleInput));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(suffix, remainingWidth),
                    suffixX, y + 11, TEXT_SUGGESTION, false);
        }
    }

    private boolean handleWhitelistClick(double mouseX, double mouseY) {
        if (mouseX < this.whitelistPanelX || mouseX >= this.whitelistPanelX + this.whitelistPanelWidth
                || mouseY < this.whitelistPanelY || mouseY >= this.whitelistPanelY + this.whitelistPanelHeight) {
            this.whitelistOpen = false;
            return true;
        }
        String candidate = currentWhitelistCandidate();
        if (candidate.isBlank()) {
            return true;
        }
        if (mouseY >= this.whitelistPlayerRowY && mouseY < this.whitelistPlayerRowY + 20) {
            if (mouseX >= this.whitelistPlayerMinusX && mouseX < this.whitelistPlayerMinusX + 20) {
                PacketDistributor.sendToServer(new RadarMonitorWhitelistPayload(this.snapshot.monitorPos(),
                        RadarMonitorWhitelistPayload.Action.REMOVE_PLAYER, candidate));
                return true;
            }
            if (mouseX >= this.whitelistPlayerPlusX && mouseX < this.whitelistPlayerPlusX + 20) {
                PacketDistributor.sendToServer(new RadarMonitorWhitelistPayload(this.snapshot.monitorPos(),
                        RadarMonitorWhitelistPayload.Action.ADD_PLAYER, candidate));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.whitelistOpen && !this.displayData.onlinePlayerNames().isEmpty()) {
            List<String> filtered = filteredOnlinePlayers();
            int size = filtered.size();
            if (size > 0) {
                int step = scrollY > 0 ? -1 : 1;
                if (this.whitelistPlayerIndex < 0) {
                    this.whitelistPlayerIndex = step < 0 ? size - 1 : 0;
                } else {
                    this.whitelistPlayerIndex = Math.floorMod(this.whitelistPlayerIndex + step, size);
                }
                this.whitelistPlayerSelectedByScroll = true;
            }
            return true;
        }
        if (isMouseOverRadar(mouseX, mouseY)) {
            return adjustMapZoom(scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.whitelistOpen && !Character.isISOControl(codePoint)) {
            this.whitelistPlayerInput += codePoint;
            this.whitelistPlayerIndex = filteredOnlinePlayers().isEmpty() ? -1 : 0;
            this.whitelistPlayerSelectedByScroll = false;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.whitelistOpen) {
            if (keyCode == 256) {
                this.whitelistOpen = false;
                return true;
            }
            if (keyCode == 259 && !this.whitelistPlayerInput.isEmpty()) {
                this.whitelistPlayerInput = this.whitelistPlayerInput.substring(0, this.whitelistPlayerInput.length() - 1);
                this.whitelistPlayerIndex = this.whitelistPlayerInput.isEmpty()
                        ? -1
                        : (filteredOnlinePlayers().isEmpty() ? -1 : 0);
                this.whitelistPlayerSelectedByScroll = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String currentWhitelistCandidate() {
        List<String> filtered = filteredOnlinePlayers();
        if (this.whitelistPlayerSelectedByScroll && !filtered.isEmpty() && this.whitelistPlayerIndex >= 0) {
            this.whitelistPlayerIndex = Math.floorMod(this.whitelistPlayerIndex, filtered.size());
            return filtered.get(this.whitelistPlayerIndex);
        }
        String input = this.whitelistPlayerInput == null ? "" : this.whitelistPlayerInput.trim();
        return this.displayData.onlinePlayerNames().stream()
                .filter(input::equalsIgnoreCase)
                .findFirst()
                .orElse("");
    }

    private String suggestedWhitelistPlayer() {
        List<String> filtered = filteredOnlinePlayers();
        if (filtered.isEmpty()) {
            return "";
        }
        int index = Math.max(0, Math.min(this.whitelistPlayerIndex, filtered.size() - 1));
        return filtered.get(index);
    }

    private List<String> filteredOnlinePlayers() {
        String input = this.whitelistPlayerInput == null ? "" : this.whitelistPlayerInput.trim().toLowerCase(java.util.Locale.ROOT);
        if (input.isEmpty()) {
            return this.displayData.onlinePlayerNames();
        }
        return this.displayData.onlinePlayerNames().stream()
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(input))
                .toList();
    }

    private static void drawNineSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int slice
    ) {
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

    private static void blitPart(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight
    ) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(texture, x, y, width, height, (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    private enum HubCategory {
        HOSTILE("message.power_radar.monitor.filter.hostile", "filter_hostile.png", RadarTargetCategory.HOSTILE_MOB),
        PASSIVE("message.power_radar.monitor.filter.passive", "filter_passive.png", RadarTargetCategory.PASSIVE_MOB),
        PLAYER("message.power_radar.monitor.filter.player", "filter_player.png", RadarTargetCategory.PLAYER),
        SABLE("message.power_radar.monitor.filter.sable", "filter_sable.png", RadarTargetCategory.SABLE_STRUCTURE),
        PROJECTILE("message.power_radar.monitor.filter.projectile", "filter_projectile.png", RadarTargetCategory.PROJECTILE);

        private final String tooltipKey;
        private final ResourceLocation icon;
        private final RadarTargetCategory category;

        HubCategory(String tooltipKey, String iconFileName, RadarTargetCategory category) {
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

        private static HubCategory[] targetValues() {
            return new HubCategory[] { HOSTILE, PASSIVE, PLAYER, SABLE };
        }

        private boolean enabledFor(RadarScanMode mode, boolean targetFilter) {
            if (targetFilter) {
                return this.category != RadarTargetCategory.PROJECTILE;
            }
            return switch (this.category) {
                case PLAYER -> mode != RadarScanMode.SKY;
                case HOSTILE_MOB, PASSIVE_MOB -> mode == RadarScanMode.GROUND;
                case PROJECTILE -> mode == RadarScanMode.GROUND || mode == RadarScanMode.SKY;
                case SABLE_STRUCTURE -> mode == RadarScanMode.SURFACE_SCANNER;
                case UNKNOWN -> false;
            };
        }
    }

    private record HubClickTarget(
            int x,
            int y,
            int width,
            int height,
            RadarScanMode mode,
            HubCategory category,
            boolean targetFilter,
            TargetTrajectoryMode trajectoryMode
    ) {
        private static HubClickTarget mode(int x, int y, int width, int height, RadarScanMode mode) {
            return new HubClickTarget(x, y, width, height, mode, null, false, null);
        }

        private static HubClickTarget category(int x, int y, int width, int height, HubCategory category, boolean targetFilter) {
            return new HubClickTarget(x, y, width, height, null, category, targetFilter, null);
        }

        private static HubClickTarget trajectory(int x, int y, int width, int height, TargetTrajectoryMode trajectoryMode) {
            return new HubClickTarget(x, y, width, height, null, null, false, trajectoryMode);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x
                    && mouseX < this.x + this.width
                    && mouseY >= this.y
                    && mouseY < this.y + this.height;
        }
    }

    private record GridCacheKey(
            int x,
            int y,
            int size,
            int visibleMapSizeBlocks,
            long mapCenterOffsetXBits,
            long mapCenterOffsetZBits,
            int viewYawBits
    ) {
    }

    private record GridLine(boolean vertical, int coordinate) {
    }

    private record BlipCacheKey(
            long clientStateVersion,
            int radarOriginX,
            int radarOriginY,
            int radarRadius,
            int visibleMapSizeBlocks,
            long mapCenterOffsetXBits,
            long mapCenterOffsetZBits,
            int viewYawBits,
            int targetCount
    ) {
    }

}
