package dev.kkazi.vscollisions.collision;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

public final class CollisionEventHandler {
    private CollisionEventHandler() {
    }

    public static void registerCollisionEvent() {
        var api = ValkyrienSkiesMod.getApi();
        LOGGER.info("Registering Valkyrien Skies collision callbacks");

        // Krunch + Konstant — only has persistEvent
        api.getCollisionPersistEvent().on(ev -> {
            LOGGER.debug("Collision persist event received: shipA={}, shipB={}", ev.getShipIdA(), ev.getShipIdB());
            CollisionQueue.enqueue(ev);
        });

        /**
        // PhysX — has clean startEvent, prefer it
        api.getCollisionStartEvent().on(ev -> {
            if (!meetsEnergyThreshold(ev)) return;
            CollisionQueue.INSTANCE.enqueue(ev, server);
        });**/
    }
}
