package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record AllowlistCardSavePayload(
        InteractionHand hand,
        boolean sableMode,
        int option,
        List<String> storedNames
) implements CustomPacketPayload {
    public static final Type<AllowlistCardSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "allowlist_card_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AllowlistCardSavePayload> STREAM_CODEC =
            StreamCodec.ofMember(AllowlistCardSavePayload::write, AllowlistCardSavePayload::new);

    public AllowlistCardSavePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                buffer.readBoolean(), buffer.readVarInt(), readStrings(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.hand == InteractionHand.OFF_HAND);
        buffer.writeBoolean(this.sableMode);
        buffer.writeVarInt(this.option);
        writeStrings(buffer, this.storedNames);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), 1024);
        ArrayList<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(64));
        return List.copyOf(values);
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
        List<String> safe = values == null ? List.of() : values.stream().limit(1024).toList();
        buffer.writeVarInt(safe.size());
        safe.forEach(value -> buffer.writeUtf(value == null ? "" : value, 64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
