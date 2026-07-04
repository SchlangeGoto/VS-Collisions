package dev.kkazi.vscollisions.stress;

import org.joml.Vector3d;

public class InjectedStress {
    //TODO: Maybe add more energys to bring it closer to reality
    public double compression;
    public double shear;
    public Vector3d direction;
    private double accumulatedWeight;

    public InjectedStress(double compression, double shear, Vector3d direction) {
        this.compression = compression;
        this.shear = shear;
        this.direction = new Vector3d(direction);
        this.accumulatedWeight = compression + shear;
    }

    public void add(double compression, double shear, Vector3d direction) {
        double incomingWeight = compression + shear;
        double totalWeight = accumulatedWeight + incomingWeight;

        if (totalWeight > 1e-6) {
            this.direction.mul(accumulatedWeight)
                    .add(new Vector3d(direction).mul(incomingWeight))
                    .div(totalWeight);
        }
        this.compression += compression;
        this.shear += shear;
        this.accumulatedWeight = totalWeight;
    }
}
