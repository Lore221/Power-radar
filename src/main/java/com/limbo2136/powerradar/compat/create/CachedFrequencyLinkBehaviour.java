package com.limbo2136.powerradar.compat.create;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

/** A receive-only link behaviour that reports actual frequency changes to its owner. */
public final class CachedFrequencyLinkBehaviour extends LinkBehaviour {
    private final Consumer<LinkBehaviour> frequencyChanged;

    public CachedFrequencyLinkBehaviour(
            SmartBlockEntity blockEntity,
            Pair<ValueBoxTransform, ValueBoxTransform> slots,
            Consumer<LinkBehaviour> frequencyChanged
    ) {
        super(blockEntity, slots);
        this.frequencyChanged = frequencyChanged;
    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public void setReceivedStrength(int power) {
        // The frequency is used as a Power Radar channel, not as a redstone receiver.
    }

    @Override
    public void setFrequency(boolean first, ItemStack stack) {
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        ItemStack previous = getNetworkKey().get(first).getStack();
        boolean changed = !ItemStack.isSameItemSameComponents(normalized, previous);
        super.setFrequency(first, stack);
        if (changed) {
            this.frequencyChanged.accept(this);
        }
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.frequencyChanged.accept(this);
    }
}
