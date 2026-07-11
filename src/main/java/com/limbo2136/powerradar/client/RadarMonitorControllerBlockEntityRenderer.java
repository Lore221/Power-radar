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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class RadarMonitorControllerBlockEntityRenderer implements BlockEntityRenderer<RadarMonitorControllerBlockEntity> {
    private static final ResourceLocation SCREEN_BASE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_screen_back.png");
    private static final ResourceLocation RADAR_GRID_LINE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_grid_line_pixel.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_60 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_60.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_90 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_90.png");
    private static final ResourceLocation RADAR_SWEEP_CONE_120 =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_sweep_cone_120.png");
    private static final ResourceLocation RADAR_OVERVIEW_OCTAGON =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "textures/gui/radar_monitor/radar_overview_octagon.png");
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

    private static final float SCREEN_MIN = 0.0F;
    private static final float SCREEN_MAX = 1.0F;
    private static final float SCREEN_SIZE = SCREEN_MAX - SCREEN_MIN;
    private static final float SCREEN_FRAME_PIXELS = 2.0F;
    private static final float BLOCK_TEXTURE_PIXELS = 16.0F;
    private static final float SCREEN_BACKGROUND_FRAME_PIXELS = 3.0F;
    private static final float SCREEN_BACKGROUND_TEXTURE_PIXELS = 128.0F;
    private static final float MAX_SELECTED_BLIP_HALF_SIZE = RadarConstants.computeInWorldBlipSize(SCREEN_SIZE) * 1.5F / 2.0F;
    private static final float SCREEN_CENTER = 0.5F;
    private static final float SCREEN_RADIUS = SCREEN_SIZE / 2.0F;
    private static final float BASE_FACE_OFFSET = 0.006F;
    private static final float COVERAGE_FACE_OFFSET_STEP = 0.0007F;
    private static final float BLIP_FACE_OFFSET = 0.011F;
    private static final float DISPLAY_FACE_PLANE = 8.0F / 16.0F;
    private static final int SCREEN_LIGHT = 0x00F000F0;
    private static final int GRID_ALPHA = 48;
    private static final int COVERAGE_ALPHA = 255;
    private static final Map<Integer, List<GridQuad>> IN_WORLD_GRID_GEOMETRY = buildInWorldGridGeometryCache();
    private final Map<BlockPos, InWorldBlipCache> blipCaches = new HashMap<>();

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
        float backgroundMin = screenBackgroundMin(size);
        float backgroundMax = screenBackgroundMax(size);
        float projectionRadius = screenProjectionRadius(size);
        drawMatrixQuad(poseStack, bufferSource, SCREEN_BASE, facing, relativeOrigin, size,
                backgroundMin, backgroundMin, backgroundMax, backgroundMax,
                255, 255, 255, 255, screenLight, BASE_FACE_OFFSET);
        drawRadarGrid(poseStack, bufferSource, facing, relativeOrigin, size, screenLight);
        if (displayData == null || !displayData.linked() || !displayData.structureValid()) {
            return;
        }

        ResourceLocation coverage = radarCoverageTexture(displayData);
        double ticksSinceSnapshot = ticksSinceSnapshot(clientState, partialTick);
        float viewYawDegrees = RadarMonitorViewOrientation.viewYawDegrees(displayData);
        List<RadarDisplayCoverage> coverages = displayData.coverages().isEmpty()
                ? List.of(legacyCoverage(displayData))
                : displayData.coverages();
        double gameTime = clientState.lastClientUpdateGameTime() + ticksSinceSnapshot;
        for (int coverageIndex = 0; coverageIndex < coverages.size(); coverageIndex++) {
            RadarDisplayCoverage coverageData = coverages.get(coverageIndex);
            float coverageFaceOffset = BASE_FACE_OFFSET + 0.002F + coverageIndex * COVERAGE_FACE_OFFSET_STEP;
            ResourceLocation coverageTexture = radarCoverageTexture(coverageData);
            RadarDisplayProjection radarProjection = RadarDisplayProjector.projectWorldPoint(
                    displayData,
                    coverageData.dimensionId(),
                    coverageData.originX(),
                    coverageData.originY(),
                    coverageData.originZ(),
                    viewYawDegrees,
                    RadarDisplayProjector.MONITOR_MAP_RADIUS_BLOCKS,
                    0.0D,
                    0.0D);
            if (coverageTexture != null && radarProjection.visible() && coverageData.currentRange() > 0) {
                float coverageRadius = (float) (projectionRadius
                        * coverageData.currentRange()
                        / RadarDisplayProjector.MONITOR_MAP_RADIUS_BLOCKS);
                ScreenPoint center = new ScreenPoint(
                        SCREEN_CENTER + (float) radarProjection.x() * projectionRadius,
                        SCREEN_CENTER + (float) radarProjection.y() * projectionRadius
                );
                drawClippedRotatedMatrixQuad(poseStack, bufferSource, coverageTexture, facing, relativeOrigin, size,
                        center.u() - coverageRadius,
                        center.v() - coverageRadius,
                        center.u() + coverageRadius,
                        center.v() + coverageRadius,
                        0.0F, 0.0F, 1.0F, 1.0F,
                        center,
                        coverageRotationDegrees(coverageData, gameTime, viewYawDegrees),
                        255, 255, 255, COVERAGE_ALPHA, screenLight, coverageFaceOffset);
            }
            if (radarProjection.visible()) {
                ScreenPoint center = new ScreenPoint(
                        SCREEN_CENTER + (float) radarProjection.x() * projectionRadius,
                        SCREEN_CENTER + (float) radarProjection.y() * projectionRadius
                );
                float half = 0.006F;
                drawClippedMatrixQuad(poseStack, bufferSource, SCREEN_BASE, facing, relativeOrigin, size,
                        center.u() - half, center.v() - half, center.u() + half, center.v() + half,
                        184, 255, 210, 224, screenLight, BLIP_FACE_OFFSET - 0.001F);
            }
        }

        drawBlips(poseStack, bufferSource, facing, relativeOrigin, size, screenLight, displayData, ticksSinceSnapshot, viewYawDegrees);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(RadarMonitorControllerBlockEntity controller, Vec3 cameraPos) {
        if (Vec3.atCenterOf(controller.getBlockPos()).closerThan(cameraPos, this.getViewDistance())) {
            return true;
        }
        return controller.activeOrigin() != null
                && Vec3.atCenterOf(controller.activeOrigin()).closerThan(cameraPos, this.getViewDistance());
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
            float viewYawDegrees
    ) {
        List<InWorldBlip> blips = cachedInWorldBlips(
                displayData,
                controllerKey(displayData),
                relativeOrigin,
                size,
                viewYawDegrees);
        if (blips.isEmpty()) {
            return;
        }

        float blipSize = RadarConstants.computeInWorldBlipSize(SCREEN_SIZE) * size;
        for (InWorldBlip blip : blips) {
            int alpha = blipAlpha(blip.displayAgeTicks(), ticksSinceSnapshot);
            if (alpha <= 0) {
                continue;
            }

            boolean manuallySelected = blip.targetUuid() != null && blip.targetUuid().equals(displayData.manualTargetUuid());
            float selectedScale = manuallySelected ? 1.5F : 1.0F;
            float half = blipSize * selectedScale / (2.0F * size);
            drawMatrixQuad(poseStack, bufferSource,
                    manuallySelected ? selectedBlipTexture(blip.category()) : blipTexture(blip.category()),
                    facing, relativeOrigin, size,
                    blip.center().u() - half, blip.center().v() - half,
                    blip.center().u() + half, blip.center().v() + half,
                    255, 255, 255, alpha, packedLight, BLIP_FACE_OFFSET);
        }
    }

    private List<InWorldBlip> cachedInWorldBlips(
            RadarMonitorDisplayData displayData,
            BlockPos monitorPos,
            BlockPos relativeOrigin,
            int size,
            float viewYawDegrees
    ) {
        RadarMonitorClientState.Entry clientState = RadarMonitorClientState.get(monitorPos);
        long version = clientState == null ? Long.MIN_VALUE : clientState.updateVersion();
        InWorldBlipCacheKey key = new InWorldBlipCacheKey(
                version,
                relativeOrigin.asLong(),
                size,
                Float.floatToIntBits(viewYawDegrees),
                displayData.targets().size());
        InWorldBlipCache cache = this.blipCaches.get(monitorPos);
        if (cache != null && cache.key().equals(key)) {
            return cache.blips();
        }
        List<InWorldBlip> blips = buildInWorldBlips(displayData, size, viewYawDegrees);
        this.blipCaches.put(monitorPos.immutable(), new InWorldBlipCache(key, blips));
        return blips;
    }

    private static BlockPos controllerKey(RadarMonitorDisplayData displayData) {
        return displayData.monitorPos().immutable();
    }

    private static List<InWorldBlip> buildInWorldBlips(RadarMonitorDisplayData displayData, int size, float viewYawDegrees) {
        if (displayData.targets().isEmpty()) {
            return List.of();
        }
        float projectionRadius = screenProjectionRadius(size);
        ArrayList<InWorldBlip> blips = new ArrayList<>(displayData.targets().size());
        for (RadarDisplayTarget target : displayData.targets()) {
            RadarDisplayProjection projection = RadarDisplayProjector.project(displayData, target, viewYawDegrees);
            if (!projection.visible()) {
                continue;
            }
            blips.add(new InWorldBlip(
                    new ScreenPoint(
                            SCREEN_CENTER + (float) projection.x() * projectionRadius,
                            SCREEN_CENTER + (float) projection.y() * projectionRadius),
                    target.category(),
                    target.targetUuid(),
                    target.displayAgeTicks()));
        }
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

    private static ResourceLocation phasedArrayConeTexture(RadarMonitorDisplayData displayData) {
        if (isOverviewRadar(displayData)) {
            return null;
        }
        int sectorAngle = displayData.sectorAngle();
        if (sectorAngle <= 60) {
            return RADAR_SWEEP_CONE_60;
        }
        if (sectorAngle <= 90) {
            return RADAR_SWEEP_CONE_90;
        }
        return RADAR_SWEEP_CONE_120;
    }

    private static ResourceLocation radarCoverageTexture(RadarMonitorDisplayData displayData) {
        if (isOverviewRadar(displayData)) {
            return RADAR_OVERVIEW_OCTAGON;
        }
        return phasedArrayConeTexture(displayData);
    }

    private static ResourceLocation radarCoverageTexture(RadarDisplayCoverage coverage) {
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return RADAR_OVERVIEW_OCTAGON;
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

    private static float coverageRotationDegrees(RadarMonitorDisplayData displayData, double gameTime, float viewYawDegrees) {
        if (isOverviewRadar(displayData)) {
            return 0.0F;
        }
        float radarYaw = displayData.orientationState().yawAt(gameTime);
        return RadarGeometry.relativeDegrees(radarYaw, viewYawDegrees);
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

    private static boolean isOverviewRadar(RadarMonitorDisplayData displayData) {
        return displayData.orientationState().structureType() == RadarStructureType.OVERVIEW;
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
        return Math.max(0.01F, (screenBackgroundMax(size) - screenBackgroundMin(size)) / 2.0F - MAX_SELECTED_BLIP_HALF_SIZE);
    }

    private static void drawRadarGrid(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Direction facing,
            BlockPos relativeOrigin,
            int size,
            int packedLight
    ) {
        for (GridQuad quad : IN_WORLD_GRID_GEOMETRY.getOrDefault(size, buildInWorldGridGeometry(size))) {
            drawMatrixQuad(poseStack, bufferSource, RADAR_GRID_LINE, facing, relativeOrigin, size,
                    quad.minU(), quad.minV(), quad.maxU(), quad.maxV(),
                    216, 255, 232, GRID_ALPHA, packedLight, BASE_FACE_OFFSET + 0.001F);
        }
    }

    private static Map<Integer, List<GridQuad>> buildInWorldGridGeometryCache() {
        HashMap<Integer, List<GridQuad>> cache = new HashMap<>();
        for (int size = com.limbo2136.powerradar.block.RadarDisplayStructureResolver.MIN_SIZE;
             size <= com.limbo2136.powerradar.block.RadarDisplayStructureResolver.MAX_SIZE;
             size++) {
            cache.put(size, buildInWorldGridGeometry(size));
        }
        return Map.copyOf(cache);
    }

    private static List<GridQuad> buildInWorldGridGeometry(int size) {
        int cells = Math.max(1, RadarDisplayProjector.MONITOR_GRID_CELLS);
        float contentMin = screenContentMin(size);
        float contentMax = screenContentMax(size);
        float contentSize = contentMax - contentMin;
        float lineHalfWidth = Math.max(0.0008F, SCREEN_SIZE / 256.0F);
        ArrayList<GridQuad> quads = new ArrayList<>((cells + 1) * 2);
        for (int i = 0; i <= cells; i++) {
            float coordinate = contentMin + contentSize * i / cells;
            quads.add(new GridQuad(
                    coordinate - lineHalfWidth, contentMin,
                    coordinate + lineHalfWidth, contentMax));
            quads.add(new GridQuad(
                    contentMin, coordinate - lineHalfWidth,
                    contentMax, coordinate + lineHalfWidth));
        }
        return List.copyOf(quads);
    }

    private static ResourceLocation blipTexture(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> BLIP_PLAYER;
            case HOSTILE_MOB -> BLIP_HOSTILE;
            case PROJECTILE -> BLIP_PROJECTILE;
            case PASSIVE_MOB, SABLE_STRUCTURE, UNKNOWN -> BLIP_PASSIVE;
        };
    }

    private static ResourceLocation selectedBlipTexture(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> BLIP_PLAYER_SELECTED;
            case HOSTILE_MOB -> BLIP_HOSTILE_SELECTED;
            case PROJECTILE -> BLIP_PROJECTILE_SELECTED;
            case PASSIVE_MOB -> BLIP_PASSIVE_SELECTED;
            case SABLE_STRUCTURE, UNKNOWN -> BLIP_OTHER_SELECTED;
        };
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

    private static void drawRotatedMatrixQuad(
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
            float rotationDegrees,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float faceOffset
    ) {
        ScreenPoint bottomLeft = rotatePoint(minU, maxV, rotationDegrees);
        ScreenPoint bottomRight = rotatePoint(maxU, maxV, rotationDegrees);
        ScreenPoint topRight = rotatePoint(maxU, minV, rotationDegrees);
        ScreenPoint topLeft = rotatePoint(minU, minV, rotationDegrees);
        drawTexturedMatrixQuad(poseStack, bufferSource, texture, facing, relativeOrigin, size,
                bottomLeft, bottomRight, topRight, topLeft,
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void drawClippedMatrixQuad(
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
        ArrayList<TexturedScreenVertex> polygon = new ArrayList<>(4);
        polygon.add(new TexturedScreenVertex(minU, maxV, 0.0F, 1.0F));
        polygon.add(new TexturedScreenVertex(maxU, maxV, 1.0F, 1.0F));
        polygon.add(new TexturedScreenVertex(maxU, minV, 1.0F, 0.0F));
        polygon.add(new TexturedScreenVertex(minU, minV, 0.0F, 0.0F));
        polygon = clipPolygon(polygon, size, 0);
        polygon = clipPolygon(polygon, size, 1);
        polygon = clipPolygon(polygon, size, 2);
        polygon = clipPolygon(polygon, size, 3);
        if (polygon.size() < 3) {
            return;
        }
        drawTexturedMatrixPolygon(poseStack, bufferSource, texture, facing, relativeOrigin, size, polygon,
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
            float faceOffset
    ) {
        ArrayList<TexturedScreenVertex> polygon = new ArrayList<>(4);
        ScreenPoint bottomLeft = rotatePoint(minU, maxV, pivot, rotationDegrees);
        ScreenPoint bottomRight = rotatePoint(maxU, maxV, pivot, rotationDegrees);
        ScreenPoint topRight = rotatePoint(maxU, minV, pivot, rotationDegrees);
        ScreenPoint topLeft = rotatePoint(minU, minV, pivot, rotationDegrees);
        polygon.add(new TexturedScreenVertex(bottomLeft.u(), bottomLeft.v(), textureMinU, textureMaxV));
        polygon.add(new TexturedScreenVertex(bottomRight.u(), bottomRight.v(), textureMaxU, textureMaxV));
        polygon.add(new TexturedScreenVertex(topRight.u(), topRight.v(), textureMaxU, textureMinV));
        polygon.add(new TexturedScreenVertex(topLeft.u(), topLeft.v(), textureMinU, textureMinV));
        polygon = clipPolygon(polygon, size, 0);
        polygon = clipPolygon(polygon, size, 1);
        polygon = clipPolygon(polygon, size, 2);
        polygon = clipPolygon(polygon, size, 3);
        if (polygon.size() < 3) {
            return;
        }
        drawTexturedMatrixPolygon(poseStack, bufferSource, texture, facing, relativeOrigin, size, polygon,
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static void drawRotatedMatrixQuad(
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
            float faceOffset
    ) {
        ScreenPoint bottomLeft = rotatePoint(minU, maxV, pivot, rotationDegrees);
        ScreenPoint bottomRight = rotatePoint(maxU, maxV, pivot, rotationDegrees);
        ScreenPoint topRight = rotatePoint(maxU, minV, pivot, rotationDegrees);
        ScreenPoint topLeft = rotatePoint(minU, minV, pivot, rotationDegrees);
        drawTexturedMatrixQuad(poseStack, bufferSource, texture, facing, relativeOrigin, size,
                bottomLeft, bottomRight, topRight, topLeft,
                textureMinU, textureMaxV,
                textureMaxU, textureMaxV,
                textureMaxU, textureMinV,
                textureMinU, textureMinV,
                red, green, blue, alpha, packedLight, faceOffset);
    }

    private static ScreenPoint rotatePoint(float u, float v, float rotationDegrees) {
        return rotatePoint(u, v, new ScreenPoint(SCREEN_CENTER, SCREEN_CENTER), rotationDegrees);
    }

    private static ScreenPoint rotatePoint(float u, float v, ScreenPoint pivot, float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        float du = u - pivot.u();
        float dv = v - pivot.v();
        float rotatedU = pivot.u() + (float) (du * Math.cos(radians) - dv * Math.sin(radians));
        float rotatedV = pivot.v() + (float) (du * Math.sin(radians) + dv * Math.cos(radians));
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
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
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
            float faceOffset
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f matrix = poseStack.last().pose();
        TexturedScreenVertex origin = polygon.get(0);
        for (int i = 1; i < polygon.size() - 1; i++) {
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, origin, red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i), red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
            matrixVertex(consumer, matrix, facing, relativeOrigin, size, polygon.get(i + 1), red, green, blue, alpha, packedLight, faceOffset);
        }
    }

    private static ArrayList<TexturedScreenVertex> clipPolygon(List<TexturedScreenVertex> input, int size, int edge) {
        ArrayList<TexturedScreenVertex> output = new ArrayList<>(input.size() + 1);
        if (input.isEmpty()) {
            return output;
        }
        TexturedScreenVertex previous = input.get(input.size() - 1);
        boolean previousInside = insideClipEdge(previous, size, edge);
        for (TexturedScreenVertex current : input) {
            boolean currentInside = insideClipEdge(current, size, edge);
            if (currentInside) {
                if (!previousInside) {
                    output.add(intersectClipEdge(previous, current, size, edge));
                }
                output.add(current);
            } else if (previousInside) {
                output.add(intersectClipEdge(previous, current, size, edge));
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean insideClipEdge(TexturedScreenVertex vertex, int size, int edge) {
        float contentMin = screenContentMin(size);
        float contentMax = screenContentMax(size);
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
            int size,
            int edge
    ) {
        float contentMin = screenContentMin(size);
        float contentMax = screenContentMax(size);
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

    private record GridQuad(float minU, float minV, float maxU, float maxV) {
    }

    private record InWorldBlipCache(InWorldBlipCacheKey key, List<InWorldBlip> blips) {
    }

    private record InWorldBlipCacheKey(
            long clientStateVersion,
            long relativeOrigin,
            int size,
            int viewYawBits,
            int targetCount
    ) {
    }

    private record InWorldBlip(ScreenPoint center, RadarTargetCategory category, UUID targetUuid, int displayAgeTicks) {
    }

}
