package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record NameCardOpenPayload(InteractionHand hand, String name) implements CustomPacketPayload {
    public static final Type<NameCardOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "name_card_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NameCardOpenPayload> STREAM_CODEC =
            StreamCodec.ofMember(NameCardOpenPayload::write, NameCardOpenPayload::new);

    private NameCardOpenPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readEnum(InteractionHand.class), buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
        buffer.writeUtf(name, 64);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
