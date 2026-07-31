package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.limbo2136.powerradar.client.panel.PowerRadarPanelAttachmentRenderer;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Полноразмерный панельный монитор использует нижнюю пару узлов режима FULL_DOUBLE. */
public final class RadarDisplayPanelAttachment extends AbstractPoweredPanelAttachment {
    private static final long LINK_CACHE_VALIDATION_INTERVAL_TICKS = 20L;

    private long lastPublishedRevision = Long.MIN_VALUE;
    private long lastPublishCheckGameTime = Long.MIN_VALUE;
    private long lastLinkCacheValidationGameTime = Long.MIN_VALUE;
    private RadarPanelMonitorRuntime.PanelNetworkResolution cachedNetworkResolution;

    public RadarDisplayPanelAttachment(PanelAttachmentType type) {
        super(type);
    }

    @Override
    protected double nominalPowerWatts() {
        return PowerRadarElectricalParameters.Ratings.panelRadarDisplayPowerWatts();
    }

    @Override
    public void onInserted(ItemStack stack, Player player, InteractionHand hand, BlockHitResult hitResult) {
        retainBottomTerminalPair();
    }

    @Override
    protected void afterElectricalTick(SimulationResults results) {
        retainBottomTerminalPair();
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        refreshNetworkCacheIfDue(serverLevel, gameTime);
        if (this.cachedNetworkResolution != null && isElectricallyOperational()) {
            RadarPanelMonitorRuntime.sendMovingPoseToNearby(
                    serverLevel, this.pos, this.cachedNetworkResolution);
        }
        if (gameTime == this.lastPublishCheckGameTime || Math.floorMod(gameTime, 5L) != 0L) {
            return;
        }
        this.lastPublishCheckGameTime = gameTime;
        this.cachedNetworkResolution = RadarPanelMonitorRuntime.refreshCachedControllers(
                serverLevel, this.cachedNetworkResolution);
        RadarMonitorSnapshotPayload snapshot = RadarPanelMonitorRuntime.createSnapshot(
                serverLevel, this.pos, this, this.cachedNetworkResolution);
        if (snapshot.revision() == this.lastPublishedRevision && Math.floorMod(gameTime, 20L) != 0L) {
            return;
        }
        this.lastPublishedRevision = snapshot.revision();
        RadarPanelMonitorRuntime.sendSnapshotToNearby(serverLevel, this.pos, snapshot);
    }

    @Override
    public ItemInteractionResult onInteract(
            ItemStack stack,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer && this.level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    RadarPanelMonitorRuntime.createSnapshot(
                            serverLevel, this.pos, this, currentNetworkResolution(serverLevel)));
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Возвращает выбранный Link без постоянного поиска по 3x3.
     * Раз в секунду полный обход одновременно проверяет питание и приоритет старейшей сети.
     */
    public RadarPanelMonitorRuntime.PanelNetworkResolution currentNetworkResolution(ServerLevel level) {
        refreshNetworkCacheIfDue(level, level.getGameTime());
        return this.cachedNetworkResolution;
    }

    private void refreshNetworkCacheIfDue(ServerLevel level, long gameTime) {
        if (this.cachedNetworkResolution != null
                && gameTime - this.lastLinkCacheValidationGameTime
                < LINK_CACHE_VALIDATION_INTERVAL_TICKS) {
            return;
        }
        this.cachedNetworkResolution = RadarPanelMonitorRuntime.resolvePanelNetwork(level, this.pos);
        this.lastLinkCacheValidationGameTime = gameTime;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(
            ElectricalPanelBlockEntity panel,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        PowerRadarPanelAttachmentRenderer.renderDisplay(
                this, panel, partialTicks, poseStack, buffers, packedLight, packedOverlay);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        retainBottomTerminalPair();
    }

    // CEE создаёт четыре узла FULL_DOUBLE; модель использует только нижние левый и правый.
    private void retainBottomTerminalPair() {
        if (this.nodes != null && this.nodes.length == 4) {
            this.nodes = new InWorldNode[]{this.nodes[2], this.nodes[3]};
        }
    }
}
