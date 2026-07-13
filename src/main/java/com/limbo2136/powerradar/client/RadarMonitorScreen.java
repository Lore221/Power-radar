package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.network.RadarMonitorRequestPayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorTargetSelectionPayload;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarDisplayProjection;
import com.limbo2136.powerradar.radar.RadarDisplayProjector;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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
    private static final ResourceLocation RADAR_SCREEN_BACK =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_screen_back.png");
    private static final ResourceLocation RADAR_GRID_SCALE_100 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_grid_scale_100x.png");
    private static final ResourceLocation RADAR_GRID_SCALE_500 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_grid_scale_500x.png");
    private static final ResourceLocation RADAR_GRID_SCALE_1000 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_grid_scale_1000x.png");
    private static final int BACKGROUND_TEXTURE_SIZE = 32;
    private static final int GRID_SCALE_TEXTURE_SIZE = 256;
    private static final int GUI_HEIGHT_PERCENT = 90;
    private static final int GUI_INNER_INSET_TEXTURE_PIXELS = 2;
    private static final int RADAR_SCREEN_TEXTURE_SIZE = 128;
    private static final int MIN_VISIBLE_MAP_SIZE_BLOCKS = RadarDisplayProjector.MIN_MONITOR_MAP_SIZE_BLOCKS;
    private static final int MAX_VISIBLE_MAP_SIZE_BLOCKS = RadarDisplayProjector.MAX_MONITOR_MAP_SIZE_BLOCKS;
    private static final int MAP_ZOOM_STEP_BLOCKS = 100;
    private static final int BLIP_REFERENCE_MAP_SIZE_BLOCKS =
            RadarDisplayProjector.MINIMUM_RADAR_REFERENCE_MAP_SIZE_BLOCKS;
    private static final double STRUCTURE_BLIP_SCALE_MULTIPLIER = 1.35D;
    private static final int GUI_GRID_LINE_COLOR = 0x2ED8FFE8;
    private static final int GRID_LOD_NEAR_LIMIT_BLOCKS = 2000;
    private static final int GRID_LOD_MID_LIMIT_BLOCKS = 5000;
    private static final int GRID_LOD_NEAR_STEP_BLOCKS = 100;
    private static final int GRID_LOD_MID_STEP_BLOCKS = 500;
    private static final int GRID_LOD_FAR_STEP_BLOCKS = 1000;
    private static final int FULL_SNAPSHOT_FALLBACK_TICKS = 100;
    private static final float GUI_BLIP_DEPTH_STEP = 1.0F;
    private static final int RADAR_SCREEN_FRAME_PIXELS = 3;
    private static final int GUI_MARGIN = 8;
    private static final int TEXT_BAD = 0xFFFF6B6B;
    private static final Component NO_LINK_TEXT = Component.translatable("message.power_radar.monitor.no_linked_radar");
    private static final Component INVALID_STRUCTURE_TEXT = Component.translatable("message.power_radar.monitor.invalid_structure");
    private final RadarDisplaySpriteRenderer spriteRenderer = new RadarDisplaySpriteRenderer();
    private final List<RadarBlipRenderData> blips = new ArrayList<>();
    private RadarMonitorSnapshotPayload snapshot;
    private RadarMonitorDisplayData displayData;
    private int ticksSinceUpdate;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int ticksSinceSnapshot;
    private long observedClientStateVersion = Long.MIN_VALUE;
    private int radarOriginX;
    private int radarOriginY;
    private int radarRadius;
    private int guiX;
    private int guiY;
    private int guiSize;
    private ResourceLocation cachedGridScaleTexture = RADAR_GRID_SCALE_100;
    private int visibleMapSizeBlocks = MIN_VISIBLE_MAP_SIZE_BLOCKS;
    private boolean initialMapScaleApplied;
    private double mapCenterOffsetX;
    private double mapCenterOffsetZ;
    private boolean draggingMap;
    private double lastDragMouseX;
    private double lastDragMouseY;
    private String selectedTargetKey;
    private boolean targetSelectionChangedInScreen;
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
        if (!this.initialMapScaleApplied && RadarDisplayProjector.maximumRadarRange(nextDisplayData) > 0) {
            this.visibleMapSizeBlocks = RadarDisplayProjector.recommendedMapSizeBlocks(nextDisplayData);
            this.initialMapScaleApplied = true;
            updateCachedGridScaleTexture();
        }
        this.observedClientStateVersion = entry.updateVersion();
        this.ticksSinceSnapshot = 0;
        if (!this.targetSelectionChangedInScreen) {
            restoreManualTargetSelection();
        }
        keepSelectedTargetIfPresent();
        if (hasLayout()) {
            rebuildBlipCache();
        }
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

        drawBackgroundAsset(graphics);
        renderRadarDisplay(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRadarDisplay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PowerRadarClientConfig.RadarRenderPalette palette = PowerRadarClientConfig.radarRenderPalette();
        drawRadarWorkArea(graphics, partialTick, palette.cone());
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
        RadarBlipRenderData hoveredBlip = hoveredBlip(mouseX, mouseY).orElse(null);
        float topBlipDepth = this.blips.size() * GUI_BLIP_DEPTH_STEP;
        for (int blipIndex = 0; blipIndex < this.blips.size(); blipIndex++) {
            RadarBlipRenderData blip = this.blips.get(blipIndex);
            int alpha = blipAlpha(blip, partialTick);
            if (alpha <= 0) {
                continue;
            }
            float depth = blipIndex * GUI_BLIP_DEPTH_STEP;
            if (isSelectedBlip(blip)) {
                this.spriteRenderer.drawLockedSelectedBlip(
                        graphics, blip, alpha, blipDrawSize(blip), palette,
                        topBlipDepth + GUI_BLIP_DEPTH_STEP * 2.0F);
            } else if (blip == hoveredBlip) {
                this.spriteRenderer.drawSelectedBlip(
                        graphics, blip, alpha, blipDrawSize(blip), palette,
                        topBlipDepth + GUI_BLIP_DEPTH_STEP);
            } else {
                this.spriteRenderer.drawBlip(graphics, blip, alpha, blipDrawSize(blip), palette, depth);
            }
        }
        drawGridScaleOverlay(graphics);
    }

    private void drawGridScaleOverlay(GuiGraphics graphics) {
        int size = this.radarRadius * 2;
        int x = this.radarOriginX - this.radarRadius;
        int y = this.radarOriginY - this.radarRadius;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(this.cachedGridScaleTexture, x, y, size, size, 0.0F, 0.0F,
                GRID_SCALE_TEXTURE_SIZE, GRID_SCALE_TEXTURE_SIZE, GRID_SCALE_TEXTURE_SIZE, GRID_SCALE_TEXTURE_SIZE);
        RenderSystem.disableBlend();
    }

    private void drawRadarWorkArea(GuiGraphics graphics, float partialTick, int coneColor) {
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
                drawRadarCoverage(graphics, coverageData, x + inset, y + inset, innerSize, coneColor);
            }
            graphics.disableScissor();
        }
        RenderSystem.disableBlend();
    }

    private void drawRadarCoverage(
            GuiGraphics graphics,
            RadarDisplayCoverage coverageData,
            int x,
            int y,
            int innerSize,
            int coneColor
    ) {
        RadarDisplayProjection radarProjection = RadarDisplayProjector.projectWorldPointUnclipped(
                this.displayData,
                coverageData.dimensionId(),
                coverageData.originX(),
                coverageData.originY(),
                coverageData.originZ(),
                viewYawDegrees(),
                visibleMapRadiusBlocks(),
                this.mapCenterOffsetX,
                this.mapCenterOffsetZ);
        RadarCoverageSprite coverage = RadarCoverageSprite.forCoverage(coverageData);
        if (radarProjection.visible() && coverageData.currentRange() > 0) {
            double contentRadius = innerSize / 2.0D;
            int coverageRadius = Math.max(1, (int) Math.round(contentRadius
                    * coverageData.currentRange()
                    / visibleMapRadiusBlocks()));
            int coverageCenterX = x + innerSize / 2 + (int) Math.round(radarProjection.x() * contentRadius);
            int coverageCenterY = y + innerSize / 2 + (int) Math.round(radarProjection.y() * contentRadius);
            drawRotatedRadarSprite(graphics, coverage, coverageCenterX, coverageCenterY,
                    coverageRadius, coverageRotationDegrees(coverageData), coneColor);
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

    private void drawRotatedRadarSprite(
            GuiGraphics graphics,
            RadarCoverageSprite sprite,
            int centerX,
            int centerY,
            int radius,
            float rotationDegrees,
            int color
    ) {
        float minX = sprite.minX(centerX, radius);
        float minY = sprite.minY(centerY, radius);
        float maxX = sprite.maxX(centerX, radius);
        float maxY = sprite.maxY(centerY, radius);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
        graphics.pose().translate(-centerX, -centerY, 0.0F);
        drawTexturedGuiQuad(
                graphics,
                sprite.texture(),
                minX,
                minY,
                maxX - minX,
                maxY - minY,
                sprite.minU(),
                sprite.maxU(),
                sprite.minV(),
                sprite.maxV(),
                color);
        graphics.pose().popPose();
    }

    private static void drawTexturedGuiQuad(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            float minU,
            float maxU,
            float minV,
            float maxV,
            int color
    ) {
        graphics.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        buffer.addVertex(matrix, x, y, 0.0F).setUv(minU, minV).setColor(red, green, blue, 255);
        buffer.addVertex(matrix, x, y + height, 0.0F).setUv(minU, maxV).setColor(red, green, blue, 255);
        buffer.addVertex(matrix, x + width, y + height, 0.0F).setUv(maxU, maxV).setColor(red, green, blue, 255);
        buffer.addVertex(matrix, x + width, y, 0.0F).setUv(maxU, minV).setColor(red, green, blue, 255);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
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

    private void rebuildLayoutCache() {
        this.cachedWidth = this.width;
        this.cachedHeight = this.height;
        int targetGuiSize = this.height * GUI_HEIGHT_PERCENT / 100;
        this.guiSize = Math.max(32, Math.min(targetGuiSize, this.width - GUI_MARGIN * 2));
        this.guiSize -= this.guiSize & 1;
        this.guiX = (this.width - this.guiSize) / 2;
        this.guiY = (this.height - this.guiSize) / 2;
        int guiInnerInset = Math.max(1, (int) Math.ceil(
                this.guiSize * GUI_INNER_INSET_TEXTURE_PIXELS / (double) BACKGROUND_TEXTURE_SIZE));
        this.radarRadius = Math.max(1, (this.guiSize - guiInnerInset * 2) / 2);
        this.radarOriginX = this.width / 2;
        this.radarOriginY = this.height / 2;
        rebuildBlipCache();
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
        this.blips.sort(Comparator.comparing(
                RadarBlipRenderData::stableKey,
                Comparator.nullsFirst(String::compareTo)));
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
                Float.floatToIntBits(viewYawDegrees()));
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

        double contentRadius = Math.max(1.0D,
                this.radarRadius - radarFrameInsetPixels(this.radarRadius * 2));
        int x = this.radarOriginX + (int) Math.round(projection.x() * contentRadius);
        int y = this.radarOriginY + (int) Math.round(projection.y() * contentRadius);
        String stableKey = target.stableSelectionKey();
        this.blips.add(new RadarBlipRenderData(stableKey, x, y, 0xFFFFFFFF, projection.radialFraction(), target.category(), targetIndex, target.displayAgeTicks()));
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

    private int blipDrawSize(RadarBlipRenderData blip) {
        double scale = (double) BLIP_REFERENCE_MAP_SIZE_BLOCKS
                / Math.max(MIN_VISIBLE_MAP_SIZE_BLOCKS, this.visibleMapSizeBlocks);
        double radarTextureScale = this.radarRadius * 2.0D / RADAR_SCREEN_TEXTURE_SIZE;
        double categoryScale = RadarConstants.RADAR_BLIP_RENDER_SCALE;
        if (blip.category() == RadarTargetCategory.UNKNOWN
                || blip.category() == RadarTargetCategory.SABLE_STRUCTURE) {
            categoryScale *= STRUCTURE_BLIP_SCALE_MULTIPLIER;
        }
        return Math.max(1, (int) Math.round(
                RadarBlipSprite.CELL_SIZE * radarTextureScale * scale * categoryScale));
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
        updateCachedGridScaleTexture();
        clampMapCenter();
        rebuildBlipCache();
        return true;
    }

    private void updateCachedGridScaleTexture() {
        ResourceLocation nextTexture = switch (visibleGridCellBlocks()) {
            case GRID_LOD_NEAR_STEP_BLOCKS -> RADAR_GRID_SCALE_100;
            case GRID_LOD_MID_STEP_BLOCKS -> RADAR_GRID_SCALE_500;
            default -> RADAR_GRID_SCALE_1000;
        };
        if (!nextTexture.equals(this.cachedGridScaleTexture)) {
            this.cachedGridScaleTexture = nextTexture;
        }
    }

    private static int clampMapSize(int value) {
        return Math.max(MIN_VISIBLE_MAP_SIZE_BLOCKS, Math.min(MAX_VISIBLE_MAP_SIZE_BLOCKS, value));
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
        if (button == 0 && isMouseOverRadar(mouseX, mouseY)) {
            if (!selectTargetAt(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new RadarMonitorTargetSelectionPayload(this.snapshot.monitorPos(), null));
                this.selectedTargetKey = null;
                this.targetSelectionChangedInScreen = true;
            }
            return true;
        }
        if (button == 1 && isMouseOverRadar(mouseX, mouseY)) {
            this.draggingMap = true;
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 && this.draggingMap) {
            panMapByPixels(mouseX - this.lastDragMouseX, mouseY - this.lastDragMouseY);
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && this.draggingMap) {
            this.draggingMap = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean selectTargetAt(double mouseX, double mouseY) {
        Optional<RadarBlipRenderData> nearest = hoveredBlip(mouseX, mouseY);
        if (nearest.isEmpty()) {
            return false;
        }
        RadarDisplayTarget target = this.displayData.targets().get(nearest.get().targetIndex());
        this.selectedTargetKey = target.stableSelectionKey();
        this.targetSelectionChangedInScreen = true;
        if (target.targetUuid() != null) {
            PacketDistributor.sendToServer(new RadarMonitorTargetSelectionPayload(this.snapshot.monitorPos(), target.targetUuid()));
        }
        return true;
    }

    private Optional<RadarBlipRenderData> hoveredBlip(double mouseX, double mouseY) {
        RadarBlipRenderData nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (RadarBlipRenderData blip : this.blips) {
            if (blip.targetIndex() < 0 || blip.targetIndex() >= this.displayData.targets().size()) {
                continue;
            }
            double dx = mouseX - blip.screenX();
            double dy = mouseY - blip.screenY();
            double distanceSq = dx * dx + dy * dy;
            double hitRadius = Math.max(5.0, blipDrawSize(blip) * 0.75);
            double hitRadiusSq = hitRadius * hitRadius;
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

    private boolean isSelectedBlip(RadarBlipRenderData blip) {
        return this.selectedTargetKey != null && this.selectedTargetKey.equals(blip.stableKey());
    }

    private void drawCenteredInRadarArea(GuiGraphics graphics, Component text, int color) {
        graphics.drawCenteredString(this.font, text, this.radarOriginX, this.radarOriginY, color);
    }

    private void drawBackgroundAsset(GuiGraphics graphics) {
        graphics.blit(GUI_BACKGROUND, this.guiX, this.guiY, this.guiSize, this.guiSize, 0.0F, 0.0F,
                BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOverRadar(mouseX, mouseY)) {
            return adjustMapZoom(scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
            int viewYawBits
    ) {
    }

}
