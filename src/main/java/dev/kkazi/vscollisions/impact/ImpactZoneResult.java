package dev.kkazi.vscollisions.impact;

import org.joml.Vector3d;

public class ImpactZoneResult {
    public final double normalVelocity;
    public final double tangentialVelocity;
    public final double effectiveMass;
    public final double impulse;
    public final double normalEnergy;    // 0.5 * mEff * normalV²
    public final double shearEnergy;     // 0.5 * mEff * tangentialV² * friction
    public final double kineticEnergy;   // normalEnergy + shearEnergy
    public final double damageEnergy;    // kineticEnergy after spin loss
    public final double torqueFactor;    // 0-1, already normalized by ship size
    public final Vector3d impactDirection;

    public ImpactZoneResult(
            double normalVelocity, double tangentialVelocity,
            double effectiveMass, double impulse,
            double normalEnergy, double shearEnergy,
            double kineticEnergy, double damageEnergy,
            double torqueFactor, Vector3d impactDirection) {
        this.normalVelocity = normalVelocity;
        this.tangentialVelocity = tangentialVelocity;
        this.effectiveMass = effectiveMass;
        this.impulse = impulse;
        this.normalEnergy = normalEnergy;
        this.shearEnergy = shearEnergy;
        this.kineticEnergy = kineticEnergy;
        this.damageEnergy = damageEnergy;
        this.torqueFactor = torqueFactor;
        this.impactDirection = impactDirection;
    }
}
