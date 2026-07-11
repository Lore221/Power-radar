package com.limbo2136.powerradar.registry;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, PowerRadar.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MECHANICAL_SIREN =
            SOUNDS.register("mechanical_siren", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "mechanical_siren")));

    private ModSounds() {
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
