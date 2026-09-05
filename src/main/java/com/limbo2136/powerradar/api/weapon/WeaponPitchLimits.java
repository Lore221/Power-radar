package com.limbo2136.powerradar.api.weapon;

/** Физические ограничения установки, в локальных градусах возвышения. */
public record WeaponPitchLimits(double minimumDegrees, double maximumDegrees) {
    public boolean contains(double pitchDegrees) {
        return Double.isFinite(pitchDegrees)
                && pitchDegrees >= this.minimumDegrees && pitchDegrees <= this.maximumDegrees;
    }
}
