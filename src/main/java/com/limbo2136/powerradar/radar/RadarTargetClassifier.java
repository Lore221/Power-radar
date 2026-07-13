package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class RadarTargetClassifier {
    private RadarTargetClassifier() {
    }

    public static RadarTargetCategory classify(Entity entity, RadarScanProfile profile) {
        if (profile.ignoreItems() && entity instanceof ItemEntity) {
            return null;
        }
        if (entity.getType() == EntityType.PLAYER) {
            return profile.detectPlayers() ? RadarTargetCategory.PLAYER : null;
        }
        if (entity instanceof Projectile || RadarCbcProjectileCompat.isCbcProjectile(entity)) {
            return profile.detectProjectiles() ? RadarTargetCategory.PROJECTILE : null;
        }
        if (entity instanceof AbstractContraptionEntity || entity instanceof RadarStructureEntity) {
            return profile.detectUnknown() ? RadarTargetCategory.UNKNOWN : null;
        }
        MobCategory mobCategory = entity.getType().getCategory();
        if (mobCategory == MobCategory.MONSTER) {
            return profile.detectHostileMobs() ? RadarTargetCategory.HOSTILE_MOB : null;
        }
        if (isPassiveMobCategory(mobCategory) || isVanillaPassiveNpc(entity)) {
            return profile.detectPassiveMobs() ? RadarTargetCategory.PASSIVE_MOB : null;
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
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return path.equals("villager") || path.equals("wandering_trader");
    }
}
