package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class RadarTargetClassifier {
    private RadarTargetClassifier() {
    }

    public static RadarTargetCategory classify(Entity entity) {
        if (entity instanceof ItemEntity) {
            return null;
        }
        if (entity instanceof RadarStructureEntity) {
            return RadarTargetCategory.RADAR;
        }
        if (entity instanceof LivingEntity living && living.hasEffect(MobEffects.INVISIBILITY)) {
            return RadarTargetCategory.UNKNOWN;
        }
        if (entity.getType() == EntityType.PLAYER) {
            return RadarTargetCategory.PLAYER;
        }
        if (entity instanceof Projectile || RadarCbcProjectileCompat.isCbcProjectile(entity)) {
            return RadarTargetCategory.PROJECTILE;
        }
        MobCategory mobCategory = entity.getType().getCategory();
        if (mobCategory == MobCategory.MONSTER) {
            return RadarTargetCategory.HOSTILE_MOB;
        }
        if (isPassiveMobCategory(mobCategory) || isVanillaPassiveNpc(entity)) {
            return RadarTargetCategory.PASSIVE_MOB;
        }
        return null;
    }

    private static boolean isPassiveMobCategory(MobCategory category) {
        return category == MobCategory.CREATURE
                || category == MobCategory.AMBIENT
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.AXOLOTLS;
    }

    private static boolean isVanillaPassiveNpc(Entity entity) {
        return entity.getType() == EntityType.VILLAGER || entity.getType() == EntityType.WANDERING_TRADER;
    }
}
