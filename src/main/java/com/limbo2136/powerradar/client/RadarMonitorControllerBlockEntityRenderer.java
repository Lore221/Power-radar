package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarDisplayProjection;
import com.limbo2136.powerradar.radar.RadarDisplayProjector;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.ShellAlarmDisplayZone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class RadarMonitorControllerBlockEntityRenderer implements BlockEntityRenderer<RadarMonitorControllerBlockEntity> {
    private static final ResourceLocation SCREEN_BASE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_screen_back.png");
    private static final float GRID_WHITE_PIXEL_U = 221.5F / RadarBlipSprite.ATLAS_SIZE;
    private static final float GRID_WHITE_PIXEL_V = 246.5F / RadarBlipSprite.ATLAS_SIZE;
    private static final float SCREEN_MIN = 0.0F;
    private static final float SCREEN_MAX = 1.0F;
    private static final float SCREEN_SIZE = SCREEN_MAX - SCREEN_MIN;
    private static final float SCREEN_FRAME_PIXELS = 2.0F;
    private static final float BLOCK_TEXTURE_PIXELS = 16.0F;
    private static final float SCREEN_BACKGROUND_FRAME_PIXELS = 3.0F;
    private static final float SCREEN_BACKGROUND_TEXTURE_PIXELS = 128.0F;
    private static final float SCREEN_CENTER = 0.5F;
    private static final float BASE_FACE_OFFSET = 0.006F;
    private static final float VERTICAL_GRID_FACE_OFFSET = 0.007F;
    private static final float HORIZONTAL_GRID_FACE_OFFSET = 0.0072F;
    private static final float COVERAGE_FACE_OFFSET = BASE_FACE_OFFSET + 0.002F;
    private static final float COVERAGE_FACE_OFFSET_STEP = 0.00005F;
    private static final int MAX_COVERAGE_DEPTH_LAYERS = 64;
    private static final float BLIP_FACE_OFFSET = 0.012F;
    private static final float BLIP_FACE_OFFSET_STEP = 0.0001F;
    private static final float STRUCTURE_BLIP_SCALE_MULTIPLIER = 1.35F;
    private static final int MAX_BLIP_DEPTH_LAYERS = 64;
    private static final float SELECTED_FRAME_OFFSET = 0.0002F;
    private static final float BLIP_ALPHA_REINFORCEMENT_OFFSET = 0.00001F;
    private static final float DISPLAY_FACE_PLANE = 8.0F / 16.0F;
    private static final int SCREEN_LIGHT = 0x00F000F0;
    private static final int GRID_ALPHA = 48;
    private static final int COVERAGE_ALPHA = 255;
    private static final int SHELL_ALARM_ZONE_ALPHA = 32;
    private final Map<BlockPos, InWorldBlipCache> blipCaches = new HashMap<>();
    private Level cachedLevel;

    public RadarMonitorControllerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            RadarMonitorControllerBlockEntity controller,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (controller.getLevel() != this.cachedLevel) {
            this.blipCaches.clear();
            this.cachedLevel = controller.getLevel();
        }
        if (controller.activeOrigin() == null
                || controller.activeSize() <= 0
                || controller.structureStatus() != com.limbo2136.powerradar.block.RadarDisplayStructureResolver.StructureStatus.ACTIVE) {
            return;
        }

        Direction facing = controller.activeFacing();
        int size = controller.activeSize();
        BlockPos relativeOrigin = controller.activeOrigin().subtract(controller.getBlockPos());
        RadarMonitorClientState.Entry clientState = RadarMonitorClientState.get(controller.getBlockPos());
        RadarMonitorDisplayData displayData = clientState == null ? null : clientState.displayData();
        if (displayData == null || !displayData.monitorRendererEnabled()) {
            return;
        }

        int screenLight = Math.max(packedLight, SCREEN_LIGHT);
        int mapSizeBlocks = RadarDisplayProjector.recommendedMapSizeBlocks(displayData);
        int mapRadiusBlocks = Math.max(1, mapSizeBlocks / 2);
        float backgroundMin = screenBackgroundMin(size);
        float backgroundMax = screenBackgroundMax(size);
        float projectionRadius = screenProjectionRadius(size);
        drawMatrixQuad(poseStack, bufferSource, SCREEN_BASE, facing, relativeOrigin, size,
                backgroundMin, backgroundMin, backgroundMax, backgroundMax,
                255, 255, 255, 255, screenLight, BASE_FACE_OFFSET);
        drawRadarGrid(poseStack, bufferSource, facing, relativeOrigin, size, screenLight, mapSizeBlocks);
        if (!displayData.linked() || !displayData.structureValid()) {
            return;
        }

        double ticksSinceSnapshot = ticksSinceSnapshot(clientState, partialTick);
        float viewYawDegrees = RadarMonitorViewOrientation.viewYawDegrees(displayData);
        List<RadarDisplayCoverage> coverages = displayData.coverages().isEmpty()
                ? List.of(legacyCoverage(displayData))
                : displayData.coverages();
        double gameTime = clientState.lastClientUpdateGameTime() + ticksSinceSnapshot;
        PowerRadarClientConfig.RadarRenderPalette palette = PowerRadarClientConfig.radarRenderPalette();
        for (ShellAlarmDisplayZone zone : displayData.shellAlarmZones()) {
            RadarDisplayProjection zoneProjection = RadarDisplayProjector.projectWorldPointUnclipped(
                    displayData, zone.dimensionId(), zone.centerX(), zone.centerY(), zone.centerZ(),
                    viewYawDegrees, mapRadiusBlocks, 0.0D, 0.0D);
            if (!zoneProjection.visible()) {
                continue;
            }
            float halfSide = (float) (projectionRadius * zone.sideBlocks() / (2.0D * mapRadiusBlocks));
            ScreenPoint center = new ScreenPoint(
                    SCREEN_CENTER + (float) zoneProjection.x() * projectionRadius,
                    SCREEN_CENTER + (float) zoneProjection.y() * projectionRadius);
            drawClippedRotatedMatrixQuad(poseStack, bufferSource, RadarBlipSprite.ATLAS, facing,
                    relativeOrigin, size, center.u() - halfSide, center.v() - halfSide,
                    center.u() + halfSide, center.v() + halfSide,
                    GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V, GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V,
                    center, 0.0F,
                    palette.red(palette.shellAlarmZone()), palette.green(palette.shellAlarmZone()),
                    palette.blue(palette.shellAlarmZone()), SHELL_ALARM_ZONE_ALPHA, screenLight,
                    COVERAGE_FACE_OFFSET, true);
        }
        for (int coverageIndex = 0; coverageIndex < coverages.size(); coverageIndex++) {
            RadarDisplayCoverage coverageData = coverages.get(coverageIndex);
            float coverageFaceOffset = COVERAGE_FACE_OFFSET
                    + Math.min(coverageIndex, MAX_COVERAGE_DEPTH_LAYERS) * COVERAGE_FACE_OFFSET_STEP;
            RadarCoverageSprite coverageSprite = RadarCoverageSprite.forCoverage(coverageData);
            RadarDisplayProjection radarProjection = RadarDisplayProjector.projectWorldPoint(
                    displayData,
                    coverageData.dimensionId(),
                    coverageData.originX(),
                    coverageData.originY(),
                    coverageData.originZ(),
                    viewYawDegrees,
                    mapRadiusBlocks,
                    0.0D,
                    0.0D);
            if (radarProjection.visible() && coverageData.currentRange() > 0) {
                float coverageRadius = (float) (projectionRadius
                        * coverageData.currentRange()
                        / mapRadiusBlocks);
                ScreenPoint center = new ScreenPoint(
                        SCREEN_CENTER + (float) radarProjection.x() * projectionRadius,
                        SCREEN_CENTER + (float) radarProjection.y() * projectionRadius
                );
                drawClippedRotatedMatrixQuad(poseStack, bufferSource, coverageSprite.texture(), facing, relativeOrigin, size,
                        coverageSprite.minX(center.u(), coverageRadius),
                        coverageSprite.minY(center.v(), coverageRadius),
                        coverageSprite.maxX(center.u(), coverageRadius),
                        coverageSprite.maxY(center.v(), coverageRadius),
                        coverageSprite.minU(), coverageSprite.minV(), coverageSprite.maxU(), coverageSprite.maxV(),
                        center,
                        coverageRotationDegrees(coverageData, gameTime, viewYawDegrees),
                        palette.red(palette.cone()), palette.green(palette.cone()), palette.blue(palette.cone()),
                        COVERAGE_ALPHA, screenLight, coverageFaceOffset, true);
            }
        }

        drawBlips(poseStack, bufferSource, facing, relativeOrigin, size, screenLight, displayData,
                ticksSinceSnapshot, viewYawDegrees, mapRadiusBlocks, clientState.updateVersion(), palette);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(RadarMonitorControllerBlockEntity controller) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(RadarMonitorControllerBlockEntity controller) {
        return controller.activeOrigin() != null && controller.activeSize() > 0
                ? AABB.INFINITE
                : controller.getRenderBoundingBox();
    }

    @Override
    public boolean shouldRender(RadarMonitorControllerBlockEntity controller, Vec3 cameraPos) {
        double viewDistance = this.getViewDistance();
        return controller.getRenderBoundingBox().distanceToSqr(cameraPos) <= viewDistance * viewDistance;
    }

    private void drawBlips(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            int packedLight,
            RadarMonitorDisplayData displayData,
            double ticksSinceSnapshot,
            float viewYawDegrees,
            int mapRadiusBlocks,
            long updateVersion,
            PowerRadarClientConfig.RadarRenderPalette palette
    ) {
        List<InWorldBlip> blips = cachedInWorldBlips(
                displayData,
                controllerKey(displayData),
                size,
                viewYawDegrees,
                mapRadiusBlocks,
                updateVersion);
        if (blips.isEmpty()) {
            return;
        }

        float blipScale = RadarDisplayProjector.MINIMUM_RADAR_REFERENCE_MAP_SIZE_BLOCKS
                / (float) Math.max(1, mapRadiusBlocks * 2);
        for (int blipIndex = 0; blipIndex < blips.size(); blipIndex++) {
            InWorldBlip blip = blips.get(blipIndex);
            int alpha = blipAlpha(blip.displayAgeTicks(), ticksSinceSnapshot);
            if (alpha <= 0) {
                continue;
            }

            boolean manuallySelected = blip.targetUuid() != null && blip.targetUuid().equals(displayData.manualTargetUuid());
            float categoryScale = blip.category() == RadarTargetCategory.UNKNOWN
                    || blip.category() == RadarTargetCategory.SABLE_STRUCTURE
                    ? STRUCTURE_BLIP_SCALE_MULTIPLIER
                    : 1.0F;
            float blipSize = SCREEN_SIZE * (RadarBlipSprite.CELL_SIZE / 128.0F)
                    * RadarConstants.RADAR_BLIP_RENDER_SCALE * categoryScale * blipScale * size;
            float half = blipSize / (2.0F * size);
            float blipFaceOffset = BLIP_FACE_OFFSET
                    + Math.min(blipIndex, MAX_BLIP_DEPTH_LAYERS) * BLIP_FACE_OFFSET_STEP;
            int blipColor = palette.blip(blip.category());
            RadarBlipSprite blipSprite = RadarBlipSprite.forCategory(blip.category());
            drawBlipLayer(poseStack, bufferSource, facing, relativeOrigin, size, blip.center(), half,
                    blipSprite, blipColor, alpha, packedLight, blipFaceOffset);
            drawBlipLayer(poseStack, bufferSource, facing, relativeOrigin, size, blip.center(), half,
                    blipSprite, blipColor, alpha, packedLight,
                    blipFaceOffset + BLIP_ALPHA_REINFORCEMENT_OFFSET);
            if (manuallySelected) {
                drawBlipLayer(poseStack, bufferSource, facing, relativeOrigin, size, blip.center(), half,
                        RadarBlipSprite.SELECTED_FRAME, palette.selectedFrame(), alpha, packedLight,
                        blipFaceOffset + SELECTED_FRAME_OFFSET);
                drawBlipLayer(poseStack, bufferSource, facing, relativeOrigin, size, blip.center(), half,
                        RadarBlipSprite.SELECTED_FRAME, palette.selectedFrame(), alpha, packedLight,
                        blipFaceOffset + SELECTED_FRAME_OFFSET + BLIP_ALPHA_REINFORCEMENT_OFFSET);
            }
        }
    }

    private static void drawBlipLayer(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            ScreenPoint center,
            float cellHalfSize,
            RadarBlipSprite sprite,
            int color,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        float halfWidth = cellHalfSize * sprite.width() / RadarBlipSprite.CELL_SIZE;
        float halfHeight = cellHalfSize * sprite.height() / RadarBlipSprite.CELL_SIZE;
        drawClippedRotatedMatrixQuad(
                poseStack, bufferSource, RadarBlipSprite.ATLAS, facing, relativeOrigin, size,
                center.u() - halfWidth, center.v() - halfHeight,
                center.u() + halfWidth, center.v() + halfHeight,
                sprite.minU(), sprite.minV(), sprite.maxU(), sprite.maxV(),
                center, 0.0F,
                color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF,
                alpha, packedLight, faceOffset, false);
    }

    private List<InWorldBlip> cachedInWorldBlips(
            RadarMonitorDisplayData displayData,
            BlockPos monitorPos,
            int size,
            float viewYawDegrees,
            int mapRadiusBlocks,
            long updateVersion
    ) {
        InWorldBlipCacheKey key = new InWorldBlipCacheKey(
                updateVersion,
                size,
                Float.floatToIntBits(viewYawDegrees),
                mapRadiusBlocks);
        InWorldBlipCache cache = this.blipCaches.get(monitorPos);
        if (cache != null && cache.key().equals(key)) {
            return cache.blips();
        }
        List<InWorldBlip> blips = buildInWorldBlips(displayData, size, viewYawDegrees, mapRadiusBlocks);
        this.blipCaches.put(monitorPos.immutable(), new InWorldBlipCache(key, blips));
        return blips;
    }

    private static BlockPos controllerKey(RadarMonitorDisplayData displayData) {
        return displayData.monitorPos().immutable();
    }

    private static List<InWorldBlip> buildInWorldBlips(
            RadarMonitorDisplayData displayData,
            int size,
            float viewYawDegrees,
            int mapRadiusBlocks
    ) {
        if (displayData.targets().isEmpty()) {
            return List.of();
        }
        float projectionRadius = screenProjectionRadius(size);
        ArrayList<InWorldBlip> blips = new ArrayList<>(displayData.targets().size());
        for (RadarDisplayTarget target : displayData.targets()) {
            RadarDisplayProjection projection = RadarDisplayProjector.project(
                    displayData, target, viewYawDegrees, mapRadiusBlocks);
            if (!projection.visible()) {
                continue;
            }
            blips.add(new InWorldBlip(
                    new ScreenPoint(
                            SCREEN_CENTER + (float) projection.x() * projectionRadius,
                            SCREEN_CENTER + (float) projection.y() * projectionRadius),
                    target.category(),
                    target.targetUuid(),
                    target.stableSelectionKey(),
                    target.displayAgeTicks()));
        }
        blips.sort(Comparator.comparing(InWorldBlip::stableKey, Comparator.nullsFirst(String::compareTo)));
        return List.copyOf(blips);
    }

    private static double ticksSinceSnapshot(
            RadarMonitorClientState.Entry clientState,
            float partialTick
    ) {
        long clientGameTime = Minecraft.getInstance().level == null
                ? clientState.lastClientUpdateGameTime()
                : Minecraft.getInstance().level.getGameTime();
        return Math.max(0L, clientGameTime - clientState.lastClientUpdateGameTime()) + partialTick;
    }

    private static int blipAlpha(
            int displayAgeTicks,
            double ticksSinceSnapshot
    ) {
        int fadeDelayTicks = Math.max(0, RadarConstants.RADAR_MONITOR_BLIP_FADE_DELAY_TICKS);
        int fadeDurationTicks = Math.max(1, RadarConstants.RADAR_MONITOR_BLIP_FADE_TICKS);
        int fadeTicks = fadeDelayTicks + fadeDurationTicks;
        double ageTicks = Math.max(0, displayAgeTicks);
        if (ageTicks > 0.0) {
            ageTicks += ticksSinceSnapshot;
        }
        if (ageTicks <= fadeDelayTicks) {
            return 255;
        }
        if (ageTicks >= fadeTicks) {
            return 0;
        }
        return Math.max(0, Math.min(255, (int) Math.round(255.0 * (fadeTicks - ageTicks) / fadeDurationTicks)));
    }

    private static float coverageRotationDegrees(RadarDisplayCoverage coverage, double gameTime, float viewYawDegrees) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return 0.0F;
        }
        float radarYaw = coverage.orientationState().yawAt(gameTime);
        return RadarGeometry.relativeDegrees(radarYaw, viewYawDegrees);
    }

    private static RadarDisplayCoverage legacyCoverage(RadarMonitorDisplayData displayData) {
        return new RadarDisplayCoverage(
                displayData.radarId(),
                displayData.controllerPos(),
                displayData.radarDimensionId(),
                displayData.radarOriginX(),
                displayData.radarOriginY(),
                displayData.radarOriginZ(),
                displayData.orientationState(),
                displayData.currentRange(),
                displayData.sectorAngle());
    }

    private static float screenFrameInset(int size) {
        return SCREEN_FRAME_PIXELS / (BLOCK_TEXTURE_PIXELS * Math.max(1, size));
    }

    private static float screenBackgroundMin(int size) {
        return SCREEN_MIN + screenFrameInset(size);
    }

    private static float screenBackgroundMax(int size) {
        return SCREEN_MAX - screenFrameInset(size);
    }

    private static float screenContentMin(int size) {
        float backgroundMin = screenBackgroundMin(size);
        float backgroundSize = screenBackgroundMax(size) - backgroundMin;
        return backgroundMin + backgroundSize * SCREEN_BACKGROUND_FRAME_PIXELS / SCREEN_BACKGROUND_TEXTURE_PIXELS;
    }

    private static float screenContentMax(int size) {
        float backgroundMax = screenBackgroundMax(size);
        float backgroundSize = backgroundMax - screenBackgroundMin(size);
        return backgroundMax - backgroundSize * SCREEN_BACKGROUND_FRAME_PIXELS / SCREEN_BACKGROUND_TEXTURE_PIXELS;
    }

    private static float screenProjectionRadius(int size) {
        return Math.max(0.01F, (screenBackgroundMax(size) - screenBackgroundMin(size)) / 2.0F);
    }

    private static void drawRadarGrid(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            int packedLight,
            int mapSizeBlocks
    ) {
        int cells = Math.max(1, mapSizeBlocks / RadarDisplayProjector.MONITOR_GRID_CELL_BLOCKS);
        float contentMin = screenContentMin(size);
        float contentMax = screenContentMax(size);
        float contentSize = contentMax - contentMin;
        float lineHalfWidth = Math.max(0.0008F, SCREEN_SIZE / 256.0F);
        for (int i = 0; i <= cells; i++) {
            float coordinate = contentMin + contentSize * i / cells;
            drawSolidGridQuad(
                    poseStack, bufferSource, facing, relativeOrigin, size,
                    coordinate - lineHalfWidth, contentMin,
                    coordinate + lineHalfWidth, contentMax,
                    packedLight, VERTICAL_GRID_FACE_OFFSET);
            drawSolidGridQuad(
                    poseStack, bufferSource, facing, relativeOrigin, size,
                    contentMin, coordinate - lineHalfWidth,
                    contentMax, coordinate + lineHalfWidth,
                    packedLight, HORIZONTAL_GRID_FACE_OFFSET);
        }
    }

    private static void drawSolidGridQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int packedLight,
            float faceOffset
    ) {
        drawTexturedMatrixQuad(
                poseStack, bufferSource, RadarBlipSprite.ATLAS, facing, relativeOrigin, size,
                new ScreenPoint(minU, maxV),
                new ScreenPoint(maxU, maxV),
                new ScreenPoint(maxU, minV),
                new ScreenPoint(minU, minV),
                GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V,
                GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V,
                GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V,
                GRID_WHITE_PIXEL_U, GRID_WHITE_PIXEL_V,
                216, 255, 232, GRID_ALPHA, packedLight, faceOffset);
    }

    private static void drawMatrixQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        drawTexturedMatrixQuad(poseStack, bufferSource, texture, facing, relativeOrigin, size,
                new ScreenPoint(minU, maxV),
                new ScreenPoint(maxU, maxV),
                new ScreenPoint(maxU, minV),
                new ScreenPoint(minU, minV),
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void drawClippedRotatedMatrixQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            float minU,
            float minV,
            float maxU,
            float maxV,
            float textureMinU,
            float textureMinV,
            float textureMaxU,
            float textureMaxV,
            ScreenPoint pivot,
            float rotationDegrees,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset,
            boolean coverageLayer
    ) {
        ArrayList<TexturedScreenVertex> polygon = new ArrayList<>(4);
        ScreenPoint bottomLeft;
        ScreenPoint bottomRight;
        ScreenPoint topRight;
        ScreenPoint topLeft;
        if (rotationDegrees == 0.0F) {
            bottomLeft = new ScreenPoint(minU, maxV);
            bottomRight = new ScreenPoint(maxU, maxV);
            topRight = new ScreenPoint(maxU, minV);
            topLeft = new ScreenPoint(minU, minV);
        } else {
            double radians = Math.toRadians(rotationDegrees);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            bottomLeft = rotatePoint(minU, maxV, pivot, cosine, sine);
            bottomRight = rotatePoint(maxU, maxV, pivot, cosine, sine);
            topRight = rotatePoint(maxU, minV, pivot, cosine, sine);
            topLeft = rotatePoint(minU, minV, pivot, cosine, sine);
        }
        polygon.add(new TexturedScreenVertex(bottomLeft.u(), bottomLeft.v(), textureMinU, textureMaxV));
        polygon.add(new TexturedScreenVertex(bottomRight.u(), bottomRight.v(), textureMaxU, textureMaxV));
        polygon.add(new TexturedScreenVertex(topRight.u(), topRight.v(), textureMaxU, textureMinV));
        polygon.add(new TexturedScreenVertex(topLeft.u(), topLeft.v(), textureMinU, textureMinV));
        float contentMin = screenContentMin(size);
        float contentMax = screenContentMax(size);
        polygon = clipPolygon(polygon, 0, contentMin, contentMax);
        polygon = clipPolygon(polygon, 1, contentMin, contentMax);
        polygon = clipPolygon(polygon, 2, contentMin, contentMax);
        polygon = clipPolygon(polygon, 3, contentMin, contentMax);
        if (polygon.size() < 3) {
            return;
        }
        drawTexturedMatrixPolygon(poseStack, bufferSource, texture, facing, relativeOrigin, size, polygon,
                red, green, blue, alpha, packedLight, faceOffset, coverageLayer);
    }

    private static ScreenPoint rotatePoint(float u, float v, ScreenPoint pivot, double cosine, double sine) {
        float du = u - pivot.u();
        float dv = v - pivot.v();
        float rotatedU = pivot.u() + (float) (du * cosine - dv * sine);
        float rotatedV = pivot.v() + (float) (du * sine + dv * cosine);
        return new ScreenPoint(rotatedU, rotatedV);
    }

    private static void drawTexturedMatrixQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            ScreenPoint bottomLeft,
            ScreenPoint bottomRight,
            ScreenPoint topRight,
            ScreenPoint topLeft,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        drawTexturedMatrixQuad(
                poseStack, bufferSource, texture, facing, relativeOrigin, size,
                bottomLeft, bottomRight, topRight, topLeft,
                0.0F, 1.0F,
                1.0F, 1.0F,
                1.0F, 0.0F,
                0.0F, 0.0F,
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void drawTexturedMatrixQuad(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            ScreenPoint bottomLeft,
            ScreenPoint bottomRight,
            ScreenPoint topRight,
            ScreenPoint topLeft,
            float bottomLeftU,
            float bottomLeftV,
            float bottomRightU,
            float bottomRightV,
            float topRightU,
            float topRightV,
            float topLeftU,
            float topLeftV,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RadarMonitorRenderTypes.polygonOffset(texture));
        Matrix4f matrix = poseStack.last().pose();
        matrixVertex(consumer, matrix, facing, relativeOrigin, size, bottomLeft, bottomLeftU, bottomLeftV, red, green, blue, alpha, packedLight, faceOffset);
        matrixVertex(consumer, matrix, facing, relativeOrigin, size, bottomRight, bottomRightU, bottomRightV, red, green, blue, alpha, packedLight, faceOffset);
        matrixVertex(consumer, matrix, facing, relativeOrigin, size, topRight, topRightU, topRightV, red, green, blue, alpha, packedLight, faceOffset);
        matrixVertex(consumer, matrix, facing, relativeOrigin, size, topLeft, topLeftU, topLeftV, red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void drawTexturedMatrixPolygon(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            List<TexturedScreenVertex> polygon,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset,
            boolean coverageLayer
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(coverageLayer
                ? RadarMonitorRenderTypes.translucentCoverage(texture)
                : RadarMonitorRenderTypes.polygonOffset(texture));
        Matrix4f matrix = poseStack.last().pose();
        TexturedScreenVertex origin = polygon.get(0);
        for (int i = 1; i < polygon.size() - 1; i++) {
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, origin, red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i), red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
            if (coverageLayer) {
                matrixVertex(consumer, matrix, facing, relativeOrigin, size, origin, red, green, blue, alpha, packedLight, faceOffset);
                matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i), red, green, blue, alpha, packedLight, faceOffset);
                matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
                matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
            }
        }
    }

    private static ArrayList<TexturedScreenVertex> clipPolygon(
            List<TexturedScreenVertex> input,
            int edge,
            float contentMin,
            float contentMax
    ) {
        ArrayList<TexturedScreenVertex> output = new ArrayList<>(input.size() + 1);
        if (input.isEmpty()) {
            return output;
        }
        TexturedScreenVertex previous = input.get(input.size() - 1);
        boolean previousInside = insideClipEdge(previous, edge, contentMin, contentMax);
        for (TexturedScreenVertex current : input) {
            boolean currentInside = insideClipEdge(current, edge, contentMin, contentMax);
            if (currentInside) {
                if (!previousInside) {
                    output.add(intersectClipEdge(previous, current, edge, contentMin, contentMax));
                }
                output.add(current);
            } else if (previousInside) {
                output.add(intersectClipEdge(previous, current, edge, contentMin, contentMax));
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean insideClipEdge(
            TexturedScreenVertex vertex,
            int edge,
            float contentMin,
            float contentMax
    ) {
        return switch (edge) {
            case 0 -> vertex.screenU() >= contentMin;
            case 1 -> vertex.screenU() <= contentMax;
            case 2 -> vertex.screenV() >= contentMin;
            case 3 -> vertex.screenV() <= contentMax;
            default -> true;
        };
    }

    private static TexturedScreenVertex intersectClipEdge(
            TexturedScreenVertex from,
            TexturedScreenVertex to,
            int edge,
            float contentMin,
            float contentMax
    ) {
        float target = edge == 0 || edge == 1
                ? (edge == 0 ? contentMin : contentMax)
                : (edge == 2 ? contentMin : contentMax);
        float delta = edge == 0 || edge == 1
                ? to.screenU() - from.screenU()
                : to.screenV() - from.screenV();
        float t = Math.abs(delta) < 0.000001F
                ? 0.0F
                : (target - (edge == 0 || edge == 1 ? from.screenU() : from.screenV())) / delta;
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new TexturedScreenVertex(
                lerp(from.screenU(), to.screenU(), t),
                lerp(from.screenV(), to.screenV(), t),
                lerp(from.textureU(), to.textureU(), t),
                lerp(from.textureV(), to.textureV(), t)
        );
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static void matrixVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Direction facing,
            BlockPos origin,
            int size,
            ScreenPoint point,
            float textureU,
            float textureV,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        Direction right = com.limbo2136.powerradar.block.RadarDisplayStructureResolver.right(facing);
        float u = point.u() * size;
        float v = (1.0F - point.v()) * size;
        float x = origin.getX();
        float y = origin.getY() + v;
        float z = origin.getZ();
        if (right.getStepX() > 0) {
            x += u;
        } else if (right.getStepX() < 0) {
            x += 1.0F - u;
        } else if (right.getStepZ() > 0) {
            z += u;
        } else if (right.getStepZ() < 0) {
            z += 1.0F - u;
        }

        float normalX = facing.getStepX();
        float normalZ = facing.getStepZ();
        if (facing == Direction.NORTH) {
            z += DISPLAY_FACE_PLANE - faceOffset;
        } else if (facing == Direction.SOUTH) {
            z += DISPLAY_FACE_PLANE + faceOffset;
        } else if (facing == Direction.WEST) {
            x += DISPLAY_FACE_PLANE - faceOffset;
        } else if (facing == Direction.EAST) {
            x += DISPLAY_FACE_PLANE + faceOffset;
        }

        vertex(consumer, matrix, x, y, z, textureU, textureV, red, green, blue, alpha, packedLight, normalX, 0.0F, normalZ);
    }

    private static void matrixVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Direction facing,
            BlockPos origin,
            int size,
            TexturedScreenVertex vertex,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        matrixVertex(consumer, matrix, facing, origin, size,
                new ScreenPoint(vertex.screenU(), vertex.screenV()),
                vertex.textureU(), vertex.textureV(),
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normalX, normalY, normalZ);
    }

    private record ScreenPoint(float u, float v) {
    }

    private record TexturedScreenVertex(float screenU, float screenV, float textureU, float textureV) {
    }

    private record InWorldBlipCache(InWorldBlipCacheKey key, List<InWorldBlip> blips) {
    }

    private record InWorldBlipCacheKey(
            long clientStateVersion,
            int size,
            int viewYawBits,
            int mapRadiusBlocks
    ) {
    }

    private record InWorldBlip(
            ScreenPoint center,
            RadarTargetCategory category,
            UUID targetUuid,
            String stableKey,
            int displayAgeTicks
    ) {
    }

}
