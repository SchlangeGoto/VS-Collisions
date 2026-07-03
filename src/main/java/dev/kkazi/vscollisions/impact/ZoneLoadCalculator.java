package dev.kkazi.vscollisions.impact;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.physics.ContactPoint;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

public final class ZoneLoadCalculator {

    private static final double SPIN_ENERGY_LOSS_FACTOR = 0.3;
    private static final double FRICTION_FACTOR = 0.4;
    private static final double EPSILON = 1e-9;

    private ZoneLoadCalculator() {
    }

    public static ImpactZoneResult compute(
            ImpactZone zone,
            double massA,
            double massB,
            boolean isWorldCollision,
            LoadedServerShip ship) {

        ZoneVelocityMetrics velocity = computeVelocityMetrics(zone);
        if (velocity == null) {
            LOGGER.debug("Skipping impact zone because velocity metrics could not be computed");
            return null;
        }

        double effectiveMass;
        if (isWorldCollision) {
            effectiveMass = massA > 0 ? massA : massB;
        } else {
            double totalMass = massA + massB;
            if (totalMass <= EPSILON) {
                LOGGER.debug("Skipping collision zone because combined ship mass is zero");
                return null;
            }
            effectiveMass = (massA * massB) / totalMass;
        }
        if (effectiveMass <= 0) {
            LOGGER.debug("Skipping collision zone because effective mass was non-positive");
            return null;
        }

        double impulse = effectiveMass * velocity.normalVelocity;
        double normalEnergy = 0.5 * effectiveMass * velocity.normalVelocity * velocity.normalVelocity;
        double shearEnergy = 0.5 * effectiveMass * velocity.tangentialVelocity * velocity.tangentialVelocity * FRICTION_FACTOR;
        double kineticEnergy = normalEnergy + shearEnergy;

        Vector3d impactDirection = new Vector3d(velocity.impactDirection);

        Vector3d localZonePos = new Vector3d();
        ship.getTransform().getWorldToShip()
                .transformPosition(zone.position, localZonePos);

        Vector3d localForceDir = new Vector3d();
        ship.getTransform().getWorldToShip()
                .transformDirection(impactDirection, localForceDir);
        if (localForceDir.lengthSquared() > EPSILON) localForceDir.normalize();

        Vector3d r = new Vector3d(localZonePos)
                .sub(ship.getInertiaData().getCenterOfMass());
        Vector3d leverArm = new Vector3d(r).cross(localForceDir);

        AABBic aabb = ship.getShipAABB();
        double shipRadius = Math.max(1.0, Math.sqrt(
                Math.pow(aabb.maxX() - aabb.minX(), 2) +
                        Math.pow(aabb.maxY() - aabb.minY(), 2) +
                        Math.pow(aabb.maxZ() - aabb.minZ(), 2)
        ) / 2.0);

        double leverRatio = leverArm.length() / shipRadius;
        double torqueFactor = leverRatio / (leverRatio + 1.0);

        double spinLoss = torqueFactor * SPIN_ENERGY_LOSS_FACTOR;
        double damageEnergy = kineticEnergy * (1.0 - spinLoss);

        LOGGER.debug(
                "Zone load computed: mass={}, impulse={}, damageEnergy={}, torqueFactor={}",
                effectiveMass, impulse, damageEnergy, torqueFactor);

        return new ImpactZoneResult(
                velocity.normalVelocity,
                velocity.tangentialVelocity,
                effectiveMass,
                impulse,
                normalEnergy,
                shearEnergy,
                kineticEnergy,
                damageEnergy,
                torqueFactor,
                impactDirection
        );
    }

    private static ZoneVelocityMetrics computeVelocityMetrics(ImpactZone zone) {
        Vector3d normal = normalizedOrFallback(zone.normal, new Vector3d(0, 0, 1));
        double weightedNormalVelocitySq = 0.0;
        double weightedTangentialVelocitySq = 0.0;
        double totalWeight = 0.0;

        for (ContactPoint contact : zone.contacts) {
            Vector3d velocity = new Vector3d(contact.getVelocity());
            double velocityLengthSq = velocity.lengthSquared();
            if (velocityLengthSq < EPSILON) continue;

            double speed = Math.sqrt(velocityLengthSq);
            double weight = Math.abs(contact.getSeparation()) * speed;
            if (weight < EPSILON) weight = speed;

            double vDotN = velocity.dot(normal);
            double normalVelocitySq = vDotN * vDotN;
            double tangentialVelocitySq = Math.max(0.0, velocityLengthSq - normalVelocitySq);

            weightedNormalVelocitySq += normalVelocitySq * weight;
            weightedTangentialVelocitySq += tangentialVelocitySq * weight;
            totalWeight += weight;
        }

        if (totalWeight <= EPSILON) {
            return computeFallbackVelocityMetrics(zone, normal);
        }

        double normalVelocity = Math.sqrt(weightedNormalVelocitySq / totalWeight);
        double tangentialVelocity = Math.sqrt(weightedTangentialVelocitySq / totalWeight);
        Vector3d impactDirection = normalizedOrFallback(zone.direction, normal);

        return new ZoneVelocityMetrics(normalVelocity, tangentialVelocity, impactDirection);
    }

    private static ZoneVelocityMetrics computeFallbackVelocityMetrics(ImpactZone zone, Vector3d normal) {
        Vector3d velocity = zone.averageVelocity;
        if (velocity == null || velocity.lengthSquared() < EPSILON) return null;

        double vDotN = velocity.dot(normal);
        double normalVelocity = Math.abs(vDotN);
        double tangentialVelocity = new Vector3d(velocity)
                .sub(new Vector3d(normal).mul(vDotN))
                .length();
        Vector3d impactDirection = normalizedOrFallback(zone.direction, velocity);

        return new ZoneVelocityMetrics(normalVelocity, tangentialVelocity, impactDirection);
    }

    private static Vector3d normalizedOrFallback(Vector3d value, Vector3d fallback) {
        if (value != null && value.lengthSquared() > EPSILON) {
            return new Vector3d(value).normalize();
        }
        if (fallback != null && fallback.lengthSquared() > EPSILON) {
            return new Vector3d(fallback).normalize();
        }
        return new Vector3d(0, 0, 1);
    }

    private record ZoneVelocityMetrics(
            double normalVelocity,
            double tangentialVelocity,
            Vector3d impactDirection) {
    }
}
