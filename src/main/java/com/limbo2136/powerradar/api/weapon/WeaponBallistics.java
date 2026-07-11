package com.limbo2136.powerradar.api.weapon;

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
