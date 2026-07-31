package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.limbo2136.powerradar.client.panel.PowerRadarPanelAttachmentRenderer;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Панельный Link хранит UUID предмета и транслирует сеть только при корректном питании. */
public final class RadarLinkPanelAttachment extends AbstractPoweredPanelAttachment {
    private static final String INSTALLATION_ORDER_KEY = "InstallationOrder";

    @Nullable
    private UUID networkId;
    private long installationOrder;

    public RadarLinkPanelAttachment(PanelAttachmentType type) {
        super(type);
    }

    @Override
    protected double nominalPowerWatts() {
        return PowerRadarElectricalParameters.Ratings.panelRadarLinkPowerWatts();
    }

    @Nullable
    public UUID networkId() {
        return this.networkId;
    }

    /** Меньший положительный номер означает более раннюю установку. */
    public long installationOrder() {
        return this.installationOrder > 0L ? this.installationOrder : Long.MAX_VALUE;
    }

    @Override
    public void onInserted(ItemStack stack, Player player, InteractionHand hand, BlockHitResult hitResult) {
        this.networkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        if (this.level instanceof ServerLevel serverLevel) {
            this.installationOrder = PanelLinkInstallationOrderSavedData
                    .get(serverLevel.getServer())
                    .allocate();
        }
    }

    @Override
    public void initialize() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        PanelLinkInstallationOrderSavedData orders =
                PanelLinkInstallationOrderSavedData.get(serverLevel.getServer());
        if (this.installationOrder <= 0L) {
            // Старые миры не имеют номера: порядок назначается при первой загрузке после обновления.
            this.installationOrder = orders.allocate();
        } else {
            orders.reserveAfter(this.installationOrder);
        }
    }

    @Override
    public List<ItemStack> getDrops() {
        ItemStack stack = defaultDroppedStack();
        if (this.networkId != null) {
            stack.set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), this.networkId);
        }
        return List.of(stack);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void tickClient(ElectricalPanelBlockEntity panel) {
        PowerRadarPanelAttachmentRenderer.tickLinkClient(this, panel);
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
        PowerRadarPanelAttachmentRenderer.renderLink(
                this, panel, poseStack, buffers, packedLight);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        this.networkId = tag.hasUUID("RadarNetworkId") ? tag.getUUID("RadarNetworkId") : null;
        this.installationOrder = tag.getLong(INSTALLATION_ORDER_KEY);
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.write(tag, clientPacket, registries);
        if (this.networkId != null) {
            tag.putUUID("RadarNetworkId", this.networkId);
        }
        if (this.installationOrder > 0L) {
            tag.putLong(INSTALLATION_ORDER_KEY, this.installationOrder);
        }
    }
}
