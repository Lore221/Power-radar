package com.limbo2136.powerradar.compat.createbigcannons;

import com.limbo2136.powerradar.mixin.FuzedBigCannonProjectileInvoker;
import net.minecraft.world.entity.Entity;
import rbasamoyai.createbigcannons.munitions.big_cannon.ap_shell.APShellProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.drop_mortar_shell.DropMortarShellProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.he_shell.HEShellProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.smoke_shell.SmokeShellProjectile;

/** Вызывает боевую детонацию только у явно поддержанных снарядов CBC. */
public final class InterceptedProjectileCbcCompat {
    private InterceptedProjectileCbcCompat() {
    }

    public static boolean detonateIfSupported(Entity projectile) {
        if (!(projectile instanceof HEShellProjectile)
                && !(projectile instanceof APShellProjectile)
                && !(projectile instanceof SmokeShellProjectile)
                && !(projectile instanceof DropMortarShellProjectile)) {
            return false;
        }
        ((FuzedBigCannonProjectileInvoker) projectile).powerRadar$detonate();
        // CBC удаляет снаряд отдельным этапом после detonate(); прямой вызов
        // системы перехвата должен завершить тот же жизненный цикл явно.
        projectile.discard();
        return true;
    }
}
