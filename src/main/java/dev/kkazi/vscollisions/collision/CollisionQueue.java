package dev.kkazi.vscollisions.collision;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import org.valkyrienskies.core.api.events.CollisionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CollisionQueue {
    private static final ConcurrentLinkedQueue<CollisionEvent> EVENTS = new ConcurrentLinkedQueue<>();
    private static final HashMap<Long, CollisionEvent> BEST_BY_PAIR = new HashMap<>();

    private CollisionQueue() {
    }

    public static void enqueue(CollisionEvent event) {
        //TODO: Filter out any non important collisions so we skip everything after
        EVENTS.add(event);
        LOGGER.debug("Collision event enqueued: shipA={}, shipB={}, contacts={}",
                event.getShipIdA(), event.getShipIdB(), event.getContactPoints().size());
    }

    public static void drain() {
        int queued = 0;
        CollisionEvent event;
        while ((event = EVENTS.poll()) != null) {
            queued++;
            long id = pairId(event);
            CollisionEvent existing = BEST_BY_PAIR.get(id);
            if (existing == null || energy(event) > energy(existing)) {
                BEST_BY_PAIR.put(id, event);
            }
        }
        if (queued > 0 || !BEST_BY_PAIR.isEmpty()) {
            LOGGER.debug("Collision queue drained: queued={}, deduped={}", queued, BEST_BY_PAIR.size());
        }
    }

    public static Map<Long, CollisionEvent> getAndClear() {
        Map<Long, CollisionEvent> result = new HashMap<>(BEST_BY_PAIR);
        BEST_BY_PAIR.clear();
        return result;
    }

    private static long pairId(CollisionEvent ev) {
        long a = ev.getShipIdA();
        long b = ev.getShipIdB();
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return (min << 32) | (max & 0xFFFFFFFFL);
    }

    private static double energy(CollisionEvent ev) {
        return ev.getContactPoints().stream()
                .mapToDouble(cp -> cp.getVelocity().lengthSquared())
                .sum();
    }
}