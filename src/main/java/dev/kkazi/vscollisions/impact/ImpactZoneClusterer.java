package dev.kkazi.vscollisions.impact;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import org.joml.Vector3d;
import org.valkyrienskies.core.api.physics.ContactPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class ImpactZoneClusterer {
    //TODO: Tweak these constants to get better clustering results
    private static final double CLUSTER_RADIUS = 2.0;
    private static final double NORMAL_THRESHOLD = 0.8;
    private static final int MAX_ZONES = 6;

    private ImpactZoneClusterer() {
    }

    public static List<ImpactZone> cluster(Collection<ContactPoint> points) {
        LOGGER.debug("Clustering {} contact points into impact zones", points.size());
        List<ContactPoint> sorted = points.stream()
                .sorted(Comparator.comparingDouble(cp ->
                        -(Math.abs(cp.getSeparation()) * cp.getVelocity().length())))
                .toList();

        List<ImpactZone> zones = new ArrayList<>();

        for (ContactPoint cp : sorted) {
            ImpactZone match = null;

            for (ImpactZone zone : zones) {
                Vector3d currentCenter = new Vector3d(zone.positionSum)
                        .div(zone.contactCount);
                boolean closeEnough = currentCenter
                        .distance(cp.getPosition()) < CLUSTER_RADIUS;
                boolean similarNormal = new Vector3d(zone.normalSum)
                        .normalize()
                        .dot(cp.getNormal()) > NORMAL_THRESHOLD;

                if (closeEnough && similarNormal) {
                    match = zone;
                    break;
                }
            }

            if (match != null) match.addPoint(cp);
            else zones.add(new ImpactZone(cp));
        }

        while (zones.size() > MAX_ZONES) {
            zones.sort(Comparator.comparingDouble(z -> z.severitySum));
            ImpactZone smallest = zones.remove(0);

            // Nearest by position sum weighted center
            ImpactZone nearest = zones.stream()
                    .min(Comparator.comparingDouble(z ->
                            new Vector3d(z.positionSum).div(z.contactCount)
                                    .distance(new Vector3d(smallest.positionSum)
                                            .div(smallest.contactCount))))
                    .orElse(zones.get(0));

            nearest.absorb(smallest);
        }

        double totalSeverity = zones.stream()
                .mapToDouble(z -> z.severitySum)
                .sum();

        for (ImpactZone zone : zones) {
            zone.finalise(totalSeverity);
        }

        LOGGER.debug("Impact clustering complete: contacts={}, zones={}", points.size(), zones.size());
        return zones;
    }
}
