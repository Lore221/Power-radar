package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.limbo2136.powerradar.bridge.AttitudeIndicatorPanelRenderBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Полноразмерный механический авиагоризонт без электрических узлов и нагрузки. */
public final class AttitudeIndicatorPanelAttachment extends PanelAttachment {
    private static final String KAG_MODE_TAG = "KagMode";

    private boolean kagMode;

    public AttitudeIndicatorPanelAttachment(PanelAttachmentType type) {
        super(type);
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
        AttitudeIndicatorPanelRenderBridge.render(
                this, panel, partialTicks, poseStack, buffers, packedLight, packedOverlay);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        // Механический прибор не создаёт электрических ветвей.
    }

    @Override
    public void postTick(SimulationResults results) {
        // Показания вычисляются только клиентом при отрисовке.
    }

    public boolean kagMode() {
        return this.kagMode;
    }

    public void toggleKagMode() {
        this.kagMode = !this.kagMode;
        sendData();
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        this.kagMode = tag.getBoolean(KAG_MODE_TAG);
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.write(tag, clientPacket, registries);
        if (this.kagMode) {
            tag.putBoolean(KAG_MODE_TAG, true);
        }
    }
}
