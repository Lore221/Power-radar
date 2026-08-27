package com.limbo2136.powerradar.client.ponder;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.config.PowerRadarClientConfig;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.Random;

public class RadarDisplayElement extends AnimatedSceneElementBase {
    private static final ResourceLocation OVERVIEW_TEXTURE =
            PowerRadar.id("textures/gui/radar_monitor/radar_overview_octagon.png");
    private static final ResourceLocation ICONS_TEXTURE = PowerRadar.id("textures/gui/radar_ui/icons.png");
    private static final float PANEL_MIN_U = 1.0F / 256.0F;
    private static final float PANEL_MAX_U = 111.0F / 256.0F;
    private static final float PANEL_MIN_V = 113.0F / 256.0F;
    private static final float PANEL_MAX_V = 175.0F / 256.0F;
    private static final float PANEL_ASPECT_RATIO = 62.0F / 110.0F;
    private static final float DISPLAY_FACE_INSET = 5.0F / 16.0F;
    private static final float ONBOARD_DISPLAY_FACE_PLANE = 8.0F / 16.0F;
    private static final float DISPLAY_OFFSET = 0.002F;
    private static final int COVERAGE_ALPHA = 145;
    private static final float ONBOARD_PANEL_HEIGHT = 11.0F / 16.0F;
    private static final float ONBOARD_SCREEN_SIDE = 8.0F / 16.0F;
    private static final float ONBOARD_TOP_ROTATION_DEGREES = 67.5F;
    private static final double ONBOARD_SCREEN_FACE_TRANSLATION = 0.496D;

    final BlockPos monitorOrigin;
    final int monitorWidth, monitorHeight;
    final Direction facing;
    final float size;
    final ResourceLocation texture;
    final float minU, maxU, minV, maxV;
    final float heightScale;
    final float rotationDegrees;
    final boolean onboard;
    RadarTargetCategory blipCategory;
    RadarStructureType blipRadarType;
    int blipCount;

    private RadarDisplayElement(
            MonitorBounds bounds,
            Direction facing,
            float size,
            ResourceLocation texture,
            float minU,
            float maxU,
            float minV,
            float maxV,
            float heightScale,
            float rotationDegrees
    ) {
        this(bounds.origin(), bounds.width(), bounds.height(), facing, size,
                texture, minU, maxU, minV, maxV, heightScale, rotationDegrees, false);
    }

    private RadarDisplayElement(
            BlockPos monitorOrigin,
            int monitorWidth,
            int monitorHeight,
            Direction facing,
            float size,
            ResourceLocation texture,
            float minU,
            float maxU,
            float minV,
            float maxV,
            float heightScale,
            float rotationDegrees,
            boolean onboard
    ) {
        this.monitorOrigin = monitorOrigin;
        this.monitorWidth = Math.max(0, monitorWidth);
        this.monitorHeight = Math.max(0, monitorHeight);
        this.facing = facing;
        this.size = Math.max(0.0F, Math.min(1.0F, size));
        this.texture = texture;
        this.minU = minU;
        this.maxU = maxU;
        this.minV = minV;
        this.maxV = maxV;
        this.heightScale = heightScale;
        this.rotationDegrees = rotationDegrees;
        this.onboard = onboard;
        this.blipCategory = null;
        this.blipRadarType = null;
        this.blipCount = 0;
    }

    public static RadarDisplayElement overview(
            Selection monitor,
            Direction facing,
            float size
    ) {
        return new RadarDisplayElement(
                MonitorBounds.from(monitor, facing), facing, size,
                OVERVIEW_TEXTURE, 0.0F, 1.0F, 0.0F, 1.0F,
                1.0F, 0.0F);
    }

    public static RadarDisplayElement radarPanel(
            Selection monitor,
            Direction facing,
            float size,
            float rotationDegrees
    ) {
        return new RadarDisplayElement(
                MonitorBounds.from(monitor, facing), facing, size,
                ICONS_TEXTURE, PANEL_MIN_U, PANEL_MAX_U, PANEL_MIN_V, PANEL_MAX_V,
                PANEL_ASPECT_RATIO, rotationDegrees);
    }

    public static RadarDisplayElement blips(
            Selection monitor,
            Direction facing,
            int count,
            RadarTargetCategory category,
            RadarStructureType radarType,
            float areaSize,
            float rotationDegrees
    ) {
        RadarDisplayElement element = new RadarDisplayElement(
                MonitorBounds.from(monitor, facing), facing, areaSize,
                ICONS_TEXTURE, 0.0F, 0.0F, 0.0F, 0.0F,
                1.0F, rotationDegrees);
        element.blipCategory = category;
        element.blipRadarType = radarType;
        element.blipCount = Math.max(0, count);
        return element;
    }

    public static RadarDisplayElement onboardOverview(BlockPos computerPos, Direction facing, float size) {
        return new RadarDisplayElement(computerPos, 1, 1, facing, size,
                OVERVIEW_TEXTURE, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, true);
    }

    public static RadarDisplayElement onboardRadarPanel(
            BlockPos computerPos, Direction facing, float size, float rotationDegrees
    ) {
        return new RadarDisplayElement(computerPos, 1, 1, facing, size,
                ICONS_TEXTURE, PANEL_MIN_U, PANEL_MAX_U, PANEL_MIN_V, PANEL_MAX_V,
                PANEL_ASPECT_RATIO, rotationDegrees, true);
    }

    public static RadarDisplayElement onboardBlips(
            BlockPos computerPos,
            Direction facing,
            int count,
            RadarTargetCategory category,
            RadarStructureType radarType,
            float areaSize,
            float rotationDegrees
    ) {
        RadarDisplayElement element = new RadarDisplayElement(computerPos, 1, 1, facing, areaSize,
                ICONS_TEXTURE, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, rotationDegrees, true);
        element.blipCategory = category;
        element.blipRadarType = radarType;
        element.blipCount = Math.max(0, count);
        return element;
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        if (!this.facing.getAxis().isHorizontal() || this.size <= 0.0F) {
            return;
        }

        if (this.onboard) {
            renderOnboard(buffer, graphics, fade);
            return;
        }

        Direction right = RadarDisplayStructureResolver.right(this.facing);
        int monitorSize = Math.min(this.monitorWidth, this.monitorHeight);
        if (monitorSize <= 0) {
            return;
        }
        double horizontalOffset = (this.monitorWidth - monitorSize) * 0.5D;
        double verticalOffset = (this.monitorHeight - monitorSize) * 0.5D;
        double originX = this.monitorOrigin.getX() + right.getStepX() * horizontalOffset;
        double originY = this.monitorOrigin.getY() + verticalOffset;
        double originZ = this.monitorOrigin.getZ() + right.getStepZ() * horizontalOffset;
        if (this.blipCategory != null) {
            renderBlips(buffer, graphics, fade, originX, originY, originZ, monitorSize);
            return;
        }
        float areaWidth = monitorSize * this.size;
        float areaHeight = areaWidth * this.heightScale;
        float halfWidth = areaWidth / 2.0F;
        float halfHeight = areaHeight / 2.0F;
        float rotationRadians = (float) Math.toRadians(this.rotationDegrees);
        float sin = (float) Math.sin(rotationRadians);
        float cos = (float) Math.cos(rotationRadians);

        PoseStack poseStack = graphics.pose();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.texture));
        Matrix4f matrix = poseStack.last().pose();
        int alpha = Math.round(COVERAGE_ALPHA * fade);
        int color = PowerRadarClientConfig.radarRenderPalette().cone();
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;

        addVertex(vertexConsumer, matrix, originX, originY, originZ, monitorSize,
                -halfWidth, -halfHeight, this.minU, this.maxV, sin, cos, alpha, red, green, blue);
        addVertex(vertexConsumer, matrix, originX, originY, originZ, monitorSize,
                halfWidth, -halfHeight, this.maxU, this.maxV, sin, cos, alpha, red, green, blue);
        addVertex(vertexConsumer, matrix, originX, originY, originZ, monitorSize,
                halfWidth, halfHeight, this.maxU, this.minV, sin, cos, alpha, red, green, blue);
        addVertex(vertexConsumer, matrix, originX, originY, originZ, monitorSize,
                -halfWidth, halfHeight, this.minU, this.minV, sin, cos, alpha, red, green, blue);
    }

    private void renderOnboard(MultiBufferSource buffer, GuiGraphics graphics, float fade) {
        PoseStack poseStack = graphics.pose();
        Direction right = this.facing.getClockWise();
        float pivotX = 0.5F + this.facing.getStepX() * 0.5F;
        float pivotZ = 0.5F + this.facing.getStepZ() * 0.5F;

        poseStack.pushPose();
        poseStack.translate(this.monitorOrigin.getX(), this.monitorOrigin.getY(), this.monitorOrigin.getZ());
        poseStack.translate(pivotX, ONBOARD_PANEL_HEIGHT, pivotZ);
        poseStack.mulPose(new Quaternionf().rotateAxis(
                (float) Math.toRadians(ONBOARD_TOP_ROTATION_DEGREES),
                right.getStepX(), 0.0F, right.getStepZ()));
        poseStack.translate(-pivotX, -ONBOARD_PANEL_HEIGHT, -pivotZ);
        poseStack.translate(this.facing.getStepX() * ONBOARD_SCREEN_FACE_TRANSLATION,
                ONBOARD_PANEL_HEIGHT + (ONBOARD_PANEL_HEIGHT - ONBOARD_SCREEN_SIDE) / 2.0F + ONBOARD_SCREEN_SIDE,
                this.facing.getStepZ() * ONBOARD_SCREEN_FACE_TRANSLATION);
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.scale(ONBOARD_SCREEN_SIDE, -ONBOARD_SCREEN_SIDE, ONBOARD_SCREEN_SIDE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        if (this.blipCategory != null) {
            renderOnboardBlips(buffer, poseStack.last().pose(), fade);
        } else {
            renderOnboardArea(buffer, poseStack.last().pose(), fade);
        }
        poseStack.popPose();
    }

    private void renderOnboardArea(MultiBufferSource buffer, Matrix4f matrix, float fade) {
        float areaWidth = this.size;
        float areaHeight = areaWidth * this.heightScale;
        float halfWidth = areaWidth / 2.0F;
        float halfHeight = areaHeight / 2.0F;
        float rotationRadians = (float) Math.toRadians(this.rotationDegrees);
        float sin = (float) Math.sin(rotationRadians);
        float cos = (float) Math.cos(rotationRadians);
        int color = PowerRadarClientConfig.radarRenderPalette().cone();
        int alpha = Math.round(COVERAGE_ALPHA * fade);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.texture));

        addOnboardVertex(consumer, matrix, -halfWidth, -halfHeight, this.minU, this.maxV, sin, cos, alpha, color);
        addOnboardVertex(consumer, matrix, halfWidth, -halfHeight, this.maxU, this.maxV, sin, cos, alpha, color);
        addOnboardVertex(consumer, matrix, halfWidth, halfHeight, this.maxU, this.minV, sin, cos, alpha, color);
        addOnboardVertex(consumer, matrix, -halfWidth, halfHeight, this.minU, this.minV, sin, cos, alpha, color);
    }

    private void renderOnboardBlips(MultiBufferSource buffer, Matrix4f matrix, float fade) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(ICONS_TEXTURE));
        int color = PowerRadarClientConfig.radarRenderPalette().blip(this.blipCategory);
        int alpha = Math.round(255.0F * fade);
        float[] uv = blipUv(this.blipCategory);
        Random random = new Random(seed());
        for (int index = 0; index < this.blipCount; index++) {
            float radius = (float) Math.sqrt(random.nextFloat()) * this.size * 0.45F;
            float angle = this.blipRadarType == RadarStructureType.OVERVIEW
                    ? random.nextFloat() * 360.0F
                    : this.rotationDegrees + (random.nextFloat() - 0.5F) * 120.0F;
            float radians = (float) Math.toRadians(angle);
            float horizontal = (float) Math.sin(radians) * radius;
            float vertical = (float) Math.cos(radians) * radius;
            float half = 0.0325F;
            addOnboardVertex(consumer, matrix, horizontal - half, vertical - half, uv[0], uv[3], 0.0F, 1.0F, alpha, color);
            addOnboardVertex(consumer, matrix, horizontal + half, vertical - half, uv[2], uv[3], 0.0F, 1.0F, alpha, color);
            addOnboardVertex(consumer, matrix, horizontal + half, vertical + half, uv[2], uv[1], 0.0F, 1.0F, alpha, color);
            addOnboardVertex(consumer, matrix, horizontal - half, vertical + half, uv[0], uv[1], 0.0F, 1.0F, alpha, color);
        }
    }

    private static void addOnboardVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float localHorizontal,
            float localVertical,
            float u,
            float v,
            float sin,
            float cos,
            int alpha,
            int color
    ) {
        float horizontal = localHorizontal * cos - localVertical * sin;
        float vertical = localHorizontal * sin + localVertical * cos;
        consumer.addVertex(matrix, 0.5F + horizontal, 0.5F - vertical,
                ONBOARD_DISPLAY_FACE_PLANE - DISPLAY_OFFSET)
                .setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
    }

    private void renderBlips(
            MultiBufferSource buffer,
            GuiGraphics graphics,
            float fade,
            double originX,
            double originY,
            double originZ,
            int monitorSize
    ) {
        PoseStack poseStack = graphics.pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(ICONS_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int color = PowerRadarClientConfig.radarRenderPalette().blip(this.blipCategory);
        int alpha = Math.round(255.0F * fade);
        float iconSize = Math.max(0.07F, monitorSize * 0.065F);
        float[] uv = blipUv(this.blipCategory);
        Random random = new Random(seed());
        for (int index = 0; index < this.blipCount; index++) {
            float radius = (float) Math.sqrt(random.nextFloat()) * monitorSize * this.size * 0.45F;
            float angle = this.blipRadarType == RadarStructureType.OVERVIEW
                    ? random.nextFloat() * 360.0F
                    : this.rotationDegrees + (random.nextFloat() - 0.5F) * 120.0F;
            float radians = (float) Math.toRadians(angle);
            float horizontal = (float) Math.sin(radians) * radius;
            float vertical = (float) Math.cos(radians) * radius;
            float half = iconSize / 2.0F;
            addVertex(consumer, matrix, originX, originY, originZ, monitorSize, horizontal - half, vertical - half,
                    uv[0], uv[3], 0.0F, 1.0F, alpha,
                    color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
            addVertex(consumer, matrix, originX, originY, originZ, monitorSize, horizontal + half, vertical - half,
                    uv[2], uv[3], 0.0F, 1.0F, alpha,
                    color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
            addVertex(consumer, matrix, originX, originY, originZ, monitorSize, horizontal + half, vertical + half,
                    uv[2], uv[1], 0.0F, 1.0F, alpha,
                    color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
            addVertex(consumer, matrix, originX, originY, originZ, monitorSize, horizontal - half, vertical + half,
                    uv[0], uv[1], 0.0F, 1.0F, alpha,
                    color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
        }
    }

    private long seed() {
        return 31L * this.monitorOrigin.asLong()
                + 17L * this.monitorWidth
                + 13L * this.monitorHeight
                + this.blipCategory.ordinal() * 7L + this.blipCount;
    }

    private static float[] blipUv(RadarTargetCategory category) {
        return switch (category) {
            case PROJECTILE -> new float[] {249.0F / 256.0F, 252.0F / 256.0F,
                    252.0F / 256.0F, 1.0F};
            case RADAR -> new float[] {239.0F / 256.0F, 252.0F / 256.0F,
                    243.0F / 256.0F, 1.0F};
            case SABLE_STRUCTURE, UNKNOWN -> new float[] {253.0F / 256.0F, 253.0F / 256.0F,
                    1.0F, 1.0F};
            default -> new float[] {244.0F / 256.0F, 252.0F / 256.0F,
                    248.0F / 256.0F, 1.0F};
        };
    }

    private void addVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            double originX,
            double originY,
            double originZ,
            int monitorSize,
            float localHorizontal,
            float localVertical,
            float u,
            float v,
            float sin,
            float cos,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        float rotatedHorizontal = localHorizontal * cos - localVertical * sin;
        float rotatedVertical = localHorizontal * sin + localVertical * cos;
        float screenU = 0.5F + rotatedHorizontal / monitorSize;
        float screenV = 0.5F - rotatedVertical / monitorSize;
        float screenDistanceU = screenU * monitorSize;
        float screenDistanceV = (1.0F - screenV) * monitorSize;
        Direction right = RadarDisplayStructureResolver.right(this.facing);
        double x = originX;
        double y = originY + screenDistanceV;
        double z = originZ;

        if (right.getStepX() > 0) {
            x += screenDistanceU;
        } else if (right.getStepX() < 0) {
            x += 1.0F - screenDistanceU;
        } else if (right.getStepZ() > 0) {
            z += screenDistanceU;
        } else if (right.getStepZ() < 0) {
            z += 1.0F - screenDistanceU;
        }

        switch (this.facing) {
            // Экранная поверхность модели находится на глубине 5/16 блока.
            case NORTH -> z += DISPLAY_FACE_INSET - DISPLAY_OFFSET;
            case SOUTH -> z += 1.0F - DISPLAY_FACE_INSET + DISPLAY_OFFSET;
            case WEST -> x += DISPLAY_FACE_INSET - DISPLAY_OFFSET;
            case EAST -> x += 1.0F - DISPLAY_FACE_INSET + DISPLAY_OFFSET;
            default -> {
                return;
            }
        }

        consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(this.facing.getStepX(), 0.0F, this.facing.getStepZ());
    }

    private record MonitorBounds(BlockPos origin, int width, int height) {
        private static MonitorBounds from(Selection selection, Direction facing) {
            Direction right = RadarDisplayStructureResolver.right(facing);
            BlockPos anchor = null;
            int minU = 0;
            int maxU = 0;
            int minV = 0;
            int maxV = 0;

            for (BlockPos pos : selection) {
                if (anchor == null) {
                    anchor = pos.immutable();
                    continue;
                }
                int dx = pos.getX() - anchor.getX();
                int dz = pos.getZ() - anchor.getZ();
                int u = dx * right.getStepX() + dz * right.getStepZ();
                int v = pos.getY() - anchor.getY();
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }

            if (anchor == null) {
                return new MonitorBounds(BlockPos.ZERO, 0, 0);
            }

            BlockPos origin = anchor.relative(right, minU).relative(Direction.UP, minV);
            return new MonitorBounds(origin, maxU - minU + 1, maxV - minV + 1);
        }
    }
}
