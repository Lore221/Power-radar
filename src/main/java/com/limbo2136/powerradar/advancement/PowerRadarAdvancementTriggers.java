package com.limbo2136.powerradar.advancement;

import com.limbo2136.powerradar.PowerRadar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Серверные триггеры достижений для событий, не представленных ванильными criteria. */
public final class PowerRadarAdvancementTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, PowerRadar.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, SimplePlayerTrigger> RADAR_NETWORK_ONLINE =
            TRIGGERS.register("radar_network_online", SimplePlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimplePlayerTrigger> TARGET_SELECTED =
            TRIGGERS.register("target_selected", SimplePlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimplePlayerTrigger> INCOMING_PROJECTILE =
            TRIGGERS.register("incoming_projectile", SimplePlayerTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimplePlayerTrigger> DANGEROUS_PROJECTILE_INTERCEPTED =
            TRIGGERS.register("dangerous_projectile_intercepted", SimplePlayerTrigger::new);

    private PowerRadarAdvancementTriggers() {
    }

    public static void register(IEventBus eventBus) {
        TRIGGERS.register(eventBus);
    }

    public static final class SimplePlayerTrigger
            extends SimpleCriterionTrigger<SimplePlayerTrigger.TriggerInstance> {
        @Override
        public Codec<TriggerInstance> codec() {
            return TriggerInstance.CODEC;
        }

        public void trigger(ServerPlayer player) {
            trigger(player, instance -> true);
        }

        public record TriggerInstance(Optional<ContextAwarePredicate> player)
                implements SimpleCriterionTrigger.SimpleInstance {
            private static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                            .forGetter(TriggerInstance::player)
            ).apply(instance, TriggerInstance::new));
        }
    }
}
