package com.limbo2136.powerradar.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Открывает штатную детонацию фугасных снарядов CBC для системы перехвата. */
@Pseudo
@Mixin(
        targets = "rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile",
        remap = false)
public interface FuzedBigCannonProjectileInvoker {
    @Invoker(value = "detonate", remap = false)
    void powerRadar$detonate();
}
