package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.limbo2136.powerradar.bridge.LogicDockPanelRenderBridge;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.logic.LogicDockCardInventory;
import com.limbo2136.powerradar.logic.LogicDockPolicySource;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.MultiBufferSource;

/** Половинный Logic Dock использует те же карты и сетевую политику, что и обычный блок. */
public final class LogicDockPanelAttachment extends AbstractPoweredPanelAttachment
        implements LogicDockPolicySource {
    private static final long LINK_CACHE_VALIDATION_INTERVAL_TICKS = 20L;

    private final LogicDockCardInventory cards = new LogicDockCardInventory();
    private long lastLinkCacheValidationGameTime = Long.MIN_VALUE;
    private boolean previousOperational;
    @Nullable
    private UUID registeredNetworkId;

    public LogicDockPanelAttachment(PanelAttachmentType type) {
        super(type);
    }

    @Override
    protected double nominalPowerWatts() {
        return PowerRadarElectricalParameters.Ratings.logicDockPowerWatts();
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.logicDock();
    }

    @Override
    protected void afterElectricalTick(SimulationResults results) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean operational = isElectricallyOperational();
        if (operational != this.previousOperational && this.registeredNetworkId != null) {
            RadarNetworkManager.get(serverLevel.getServer())
                    .invalidateLogicDockCache(this.registeredNetworkId);
        }
        this.previousOperational = operational;

        refreshNetworkIfDue(serverLevel);
        if (this.registeredNetworkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).touchPanelLogicDock(
                    this.registeredNetworkId,
                    GlobalPos.of(serverLevel.dimension(), this.pos),
                    this.slot.ordinal(),
                    this,
                    serverLevel.getGameTime());
        }
    }

    @Override
    public ItemInteractionResult onInteract(
            ItemStack stack,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (stack.getItem() instanceof RadarFilterCardItem card) {
            if (this.level != null && this.level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (this.cards.insert(card.kind(), stack, player)) {
                cardsChanged();
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.CONSUME;
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (this.level != null && this.level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack extracted = this.cards.extract(cardSlotFromHit(hitResult));
        if (!extracted.isEmpty()) {
            if (!player.addItem(extracted)) {
                player.drop(extracted, false);
            }
            cardsChanged();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getDrops() {
        ArrayList<ItemStack> drops = new ArrayList<>();
        drops.add(defaultDroppedStack());
        for (int slot = 0; slot < LogicDockCardInventory.SLOT_COUNT; slot++) {
            ItemStack card = this.cards.card(slot);
            if (!card.isEmpty()) {
                drops.add(card.copy());
            }
        }
        return List.copyOf(drops);
    }

    @Override
    public void onRemoved(Player player) {
        unregisterFromNetwork();
        super.onRemoved(player);
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
        LogicDockPanelRenderBridge.render(
                this, panel, poseStack, buffers, packedLight, packedOverlay);
    }

    public boolean hasCard(int slot) {
        return this.cards.hasCard(slot);
    }

    @Override
    public int targetingMask() {
        return this.cards.targetingMask();
    }

    @Override
    public int displayMask() {
        return this.cards.displayMask();
    }

    @Override
    public boolean allowlistIsWhitelist() {
        return this.cards.allowlistIsWhitelist();
    }

    @Override
    public List<String> allowlistPlayerNames() {
        return this.cards.allowlistPlayerNames();
    }

    @Override
    public List<String> allowlistSableNames() {
        return this.cards.allowlistSableNames();
    }

    @Override
    public List<String> allowlistedPlayers() {
        return this.cards.allowlistedPlayers();
    }

    @Override
    public List<String> allowlistedSableNames() {
        return this.cards.allowlistedSableNames();
    }

    @Override
    public boolean isAvailableForNetwork(UUID networkId) {
        if (!Objects.equals(this.registeredNetworkId, networkId)
                || !(this.level instanceof ServerLevel serverLevel)
                || !serverLevel.isLoaded(this.pos)
                || !(serverLevel.getBlockEntity(this.pos) instanceof ElectricalPanelBlockEntity panel)) {
            return false;
        }
        for (PanelAttachment attachment : panel.getAttachments()) {
            if (attachment == this) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        this.cards.read(tag, registries);
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.write(tag, clientPacket, registries);
        this.cards.write(tag, registries);
    }

    // Карты расположены вертикально с центрами Y = 11, 8 и 5 пикселей.
    private int cardSlotFromHit(BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(this.pos));
        local = VecHelper.rotateCentered(
                local,
                this.panelFacing.toYRot() + 180.0F,
                Direction.Axis.Y);
        double modelY = local.y - this.slot.topOffset;
        if (modelY >= 9.5D / 16.0D) {
            return LogicDockCardInventory.TARGETING_SLOT;
        }
        if (modelY >= 6.5D / 16.0D) {
            return LogicDockCardInventory.DISPLAY_SLOT;
        }
        return LogicDockCardInventory.ALLOWLIST_SLOT;
    }

    private void cardsChanged() {
        sendData();
        if (this.registeredNetworkId != null && this.level instanceof ServerLevel serverLevel) {
            RadarNetworkManager.get(serverLevel.getServer())
                    .invalidateLogicDockCache(this.registeredNetworkId);
        }
    }

    private void refreshNetworkIfDue(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (this.lastLinkCacheValidationGameTime != Long.MIN_VALUE
                && gameTime - this.lastLinkCacheValidationGameTime
                < LINK_CACHE_VALIDATION_INTERVAL_TICKS) {
            return;
        }
        UUID resolvedNetworkId = RadarPanelMonitorRuntime
                .resolvePanelNetwork(level, this.pos)
                .networkId();
        this.lastLinkCacheValidationGameTime = gameTime;
        if (Objects.equals(this.registeredNetworkId, resolvedNetworkId)) {
            return;
        }

        unregisterFromNetwork();
        this.registeredNetworkId = resolvedNetworkId;
        if (resolvedNetworkId != null) {
            RadarNetworkManager.get(level.getServer()).touchPanelLogicDock(
                    resolvedNetworkId,
                    GlobalPos.of(level.dimension(), this.pos),
                    this.slot.ordinal(),
                    this,
                    gameTime);
        }
    }

    private void unregisterFromNetwork() {
        if (this.registeredNetworkId == null || !(this.level instanceof ServerLevel serverLevel)) {
            this.registeredNetworkId = null;
            return;
        }
        RadarNetworkManager.get(serverLevel.getServer()).releasePanelLogicDock(
                this.registeredNetworkId,
                GlobalPos.of(serverLevel.dimension(), this.pos),
                this.slot.ordinal(),
                this);
        this.registeredNetworkId = null;
    }
}
