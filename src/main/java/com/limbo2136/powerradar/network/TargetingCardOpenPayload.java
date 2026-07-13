package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record TargetingCardOpenPayload(InteractionHand hand, int cardKind, int filterMask, int option) implements CustomPacketPayload {
    public static final Type<TargetingCardOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "targeting_card_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetingCardOpenPayload> STREAM_CODEC =
            StreamCodec.ofMember(TargetingCardOpenPayload::write, TargetingCardOpenPayload::new);

    public TargetingCardOpenPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.hand == InteractionHand.OFF_HAND);
        buffer.writeVarInt(this.cardKind);
        buffer.writeVarInt(this.filterMask);
        buffer.writeVarInt(this.option);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
