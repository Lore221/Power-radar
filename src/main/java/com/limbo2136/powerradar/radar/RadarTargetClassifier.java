package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;

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
        if (isVanillaProjectileLike(entity) || RadarCbcProjectileCompat.isCbcProjectile(entity)) {
            return profile.detectProjectiles() ? RadarTargetCategory.PROJECTILE : null;
        }
        MobCategory mobCategory = entity.getType().getCategory();
        if (mobCategory == MobCategory.MONSTER) {
            return profile.detectHostileMobs() ? RadarTargetCategory.HOSTILE_MOB : null;
        }
        if (isPassiveMobCategory(mobCategory) || isVanillaPassiveNpc(entity)) {
            return profile.detectPassiveMobs() ? RadarTargetCategory.PASSIVE_MOB : null;
        }
        return profile.detectUnknown() ? RadarTargetCategory.UNKNOWN : null;
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
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.equals("villager") || path.equals("wandering_trader");
    }

    private static boolean isVanillaProjectileLike(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        return switch (id.getPath()) {
            case "arrow",
                    "spectral_arrow",
                    "trident",
                    "snowball",
                    "egg",
                    "fireball",
                    "small_fireball",
                    "dragon_fireball",
                    "wither_skull",
                    "wind_charge",
                    "breeze_wind_charge",
                    "firework_rocket",
                    "llama_spit",
                    "shulker_bullet",
                    "fishing_bobber",
                    "experience_bottle",
                    "ender_pearl",
                    "eye_of_ender",
                    "potion" -> true;
            default -> false;
        };
    }
}
