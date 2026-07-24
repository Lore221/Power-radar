package com.limbo2136.powerradar.api.weapon;

/**
 * Баллистические параметры оружия: блоки, тики, блоки/тик и блоки/тик².
 * {@code drag} — доля потери скорости за тик, а {@code lifetimeTicks <= 0} означает отсутствие лимита.
 */
public record WeaponBallistics(
        boolean available,
        String mode,
        double speedBlocksPerTick,
        double gravityBlocksPerTickSquared,
        double drag,
        boolean quadraticDrag,
        int lifetimeTicks,
        int barrelCount,
        String ammunition,
        boolean highArcEnabled
) {
    public boolean hasLifetimeLimit() {
        return this.lifetimeTicks > 0;
    }
}
