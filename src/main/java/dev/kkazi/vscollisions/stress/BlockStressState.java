package dev.kkazi.vscollisions.stress;

import org.joml.Vector3d;

public class BlockStressState {
    public final double compression;
    public final double shear;
    public final Vector3d direction;
    public final double totalStress;

    public BlockStressState(double compression, double shear, Vector3d direction) {
        this.compression = compression;
        this.shear = shear;
        this.direction = new Vector3d(direction);
        // Von Mises-inspired scalar — compression and shear combined into one severity value
        this.totalStress = Math.sqrt(compression * compression + shear * shear);
    }
}
