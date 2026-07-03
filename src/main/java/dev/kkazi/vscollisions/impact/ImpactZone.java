package dev.kkazi.vscollisions.impact;

import org.joml.Vector3d;
import org.valkyrienskies.core.api.physics.ContactPoint;

import java.util.ArrayList;
import java.util.List;

public class ImpactZone {
    final Vector3d positionSum = new Vector3d();
    final Vector3d normalSum = new Vector3d();
    private final Vector3d velocitySum = new Vector3d();
    double severitySum = 0;

    public Vector3d position;
    public Vector3d normal;
    public Vector3d direction;
    public double impulse;
    public double area;
    public double severity;
    public double averagePenetration;
    public Vector3d averageVelocity;
    public int contactCount;
    public List<ContactPoint> contacts = new ArrayList<>();

    public ImpactZone(ContactPoint first) {
        addPoint(first);
    }

    public ImpactZone(ImpactZone other) {
        this.positionSum.set(other.positionSum);
        this.normalSum.set(other.normalSum);
        this.velocitySum.set(other.velocitySum);
        this.severitySum = other.severitySum;

        this.position = other.position != null ? new Vector3d(other.position) : null;
        this.normal = other.normal != null ? new Vector3d(other.normal) : null;
        this.direction = other.direction != null ? new Vector3d(other.direction) : null;
        this.averageVelocity = other.averageVelocity != null ? new Vector3d(other.averageVelocity) : null;
        this.impulse = other.impulse;
        this.area = other.area;
        this.severity = other.severity;
        this.averagePenetration = other.averagePenetration;
        this.contactCount = other.contactCount;
        this.contacts = new ArrayList<>(other.contacts);
    }

    public void addPoint(ContactPoint cp) {
        contacts.add(cp);
        contactCount++;
        positionSum.add(cp.getPosition());
        normalSum.add(cp.getNormal());
        velocitySum.add(cp.getVelocity());
        severitySum += Math.abs(cp.getSeparation()) * cp.getVelocity().length();
    }

    public void absorb(ImpactZone other) {
        contacts.addAll(other.contacts);
        positionSum.add(other.positionSum);
        normalSum.add(other.normalSum);
        velocitySum.add(other.velocitySum);
        severitySum += other.severitySum;
        contactCount += other.contactCount;
    }

    public void finalise(double totalSeverity) {
        position = new Vector3d(positionSum).div(contactCount);
        normal = new Vector3d(normalSum).normalize();

        averageVelocity = new Vector3d(velocitySum).div(contactCount);
        direction = new Vector3d(averageVelocity).normalize();

        impulse = (totalSeverity > 0)
                ? severitySum / totalSeverity
                : 1.0 / contactCount;
        severity = severitySum;

        double maxDist = 0;
        for (ContactPoint cp : contacts) {
            double d = new Vector3d(cp.getPosition()).distance(position);
            if (d > maxDist) maxDist = d;
        }
        area = Math.max(1.0, Math.PI * maxDist * maxDist);

        averagePenetration = contacts.stream()
                .mapToDouble(cp -> Math.abs(cp.getSeparation()))
                .average()
                .orElse(0.0);
    }
    public ImpactZone inverted() {
        ImpactZone copy = new ImpactZone(this);
        copy.normal = new Vector3d(this.normal).negate();
        copy.averageVelocity = new Vector3d(this.averageVelocity).negate();
        copy.direction = new Vector3d(this.direction).negate();
        return copy;
    }
}