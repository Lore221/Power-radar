package com.limbo2136.powerradar.compat.createbigcannons;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Лениво разрешает CBC-классы, сохраняя безопасный пустой результат при несовместимой версии. */
public final class RadarCbcProjectileCompat {
    private static final String ABSTRACT_CANNON_PROJECTILE =
            "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile";
    private static final String ABSTRACT_AUTOCANNON_PROJECTILE =
            "rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile";
    private static final String ABSTRACT_BIG_CANNON_PROJECTILE =
            "rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile";
    private static boolean initialized;
    private static Class<? extends Entity> cannonProjectileClass;
    private static Class<?> autocannonProjectileClass;
    private static Class<?> bigCannonProjectileClass;

    private RadarCbcProjectileCompat() {
    }

    public static Optional<Class<? extends Entity>> projectileClass() {
        initialize();
        return Optional.ofNullable(cannonProjectileClass);
    }

    public static boolean isCbcProjectile(Entity entity) {
        initialize();
        return cannonProjectileClass != null && cannonProjectileClass.isInstance(entity);
    }

    public static boolean isAutocannonProjectile(Entity entity) {
        initialize();
        return autocannonProjectileClass != null && autocannonProjectileClass.isInstance(entity);
    }

    public static boolean isBigCannonProjectile(Entity entity) {
        initialize();
        return bigCannonProjectileClass != null && bigCannonProjectileClass.isInstance(entity);
    }

    @SuppressWarnings("unchecked")
    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded("createbigcannons")) {
            return;
        }
        try {
            Class<?> base = Class.forName(ABSTRACT_CANNON_PROJECTILE);
            if (Entity.class.isAssignableFrom(base)) {
                cannonProjectileClass = (Class<? extends Entity>) base;
            }
            autocannonProjectileClass = Class.forName(ABSTRACT_AUTOCANNON_PROJECTILE);
            bigCannonProjectileClass = Class.forName(ABSTRACT_BIG_CANNON_PROJECTILE);
        } catch (ClassNotFoundException ignored) {
            cannonProjectileClass = null;
            autocannonProjectileClass = null;
            bigCannonProjectileClass = null;
        }
    }
}
