package dev.kkazi.vscollisions.damage;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import dev.kkazi.vscollisions.collision.CollisionQueue;
import dev.kkazi.vscollisions.impact.ImpactZone;
import dev.kkazi.vscollisions.impact.ImpactZoneClusterer;
import dev.kkazi.vscollisions.impact.ImpactZoneResult;
import dev.kkazi.vscollisions.impact.ZoneLoadCalculator;
import dev.kkazi.vscollisions.stress.BlockImpactInjector;
import dev.kkazi.vscollisions.stress.BlockStressState;
import dev.kkazi.vscollisions.stress.InjectedStress;
import dev.kkazi.vscollisions.stress.StressPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DamageApplicator {
    private static final double MIN_DAMAGE_IMPULSE = 50.0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        CollisionQueue.drain();
        for (CollisionEvent collision : CollisionQueue.getAndClear().values()) {
            processCollision(collision, server);
        }
    }

    private void processCollision(CollisionEvent collision, MinecraftServer server) {
        if (collision == null) return;

        LOGGER.debug(
                "Processing collision: dim={}, shipA={}, shipB={}, contacts={}",
                collision.getDimensionId(),
                collision.getShipIdA(),
                collision.getShipIdB(),
                collision.getContactPoints().size());

        String dimId = collision.getDimensionId();
        ServerLevel level = VSGameUtilsKt.getLevelFromDimensionId(server, dimId);
        if (level == null) {
            LOGGER.warn("Skipping collision because dimension could not be resolved: {}", dimId);
            return;
        }

        VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        LoadedServerShip shipA = shipWorld.getLoadedShips().getById(collision.getShipIdA());
        LoadedServerShip shipB = shipWorld.getLoadedShips().getById(collision.getShipIdB());

        if (shipA == null && shipB == null) {
            LOGGER.warn("Skipping collision because neither ship could be resolved in {}", dimId);
            return;
        }

        double massA = shipA != null ? shipA.getInertiaData().getMass() : 0.0;
        double massB = shipB != null ? shipB.getInertiaData().getMass() : 0.0;
        LOGGER.debug("Resolved collision ships: massA={}, massB={}, shipAResolved={}, shipBResolved={}",
                massA, massB, shipA != null, shipB != null);

        List<ImpactZone> impactZones = ImpactZoneClusterer.cluster(collision.getContactPoints());
        LOGGER.debug("Clustered collision into {} impact zones", impactZones.size());

        Map<BlockPos, InjectedStress> stressOnA = new HashMap<>();
        Map<BlockPos, InjectedStress> stressOnB = new HashMap<>();
        int injectedZonesA = 0;
        int injectedZonesB = 0;

        for (ImpactZone zone : impactZones) {

            if (shipA != null) {
                boolean aIsWorldCollision = (shipB == null);
                ImpactZoneResult resultA = ZoneLoadCalculator.compute(zone, massA, massB, aIsWorldCollision, shipA);
                if (resultA != null && resultA.impulse >= MIN_DAMAGE_IMPULSE) {
                    injectedZonesA++;
                    Map<BlockPos, InjectedStress> zoneStressA =
                            BlockImpactInjector.inject(zone, resultA, shipA, level);
                    mergeInto(stressOnA, zoneStressA);
                } else {
                    LOGGER.debug("Zone skipped for shipA: impulse={}", resultA != null ? resultA.impulse : null);
                }
            }

            if (shipB != null) {
                boolean bIsWorldCollision = (shipA == null);
                ImpactZone invertedZone = zone.inverted();
                ImpactZoneResult resultB = ZoneLoadCalculator.compute(invertedZone, massA, massB, bIsWorldCollision, shipB);
                if (resultB != null && resultB.impulse >= MIN_DAMAGE_IMPULSE) {
                    injectedZonesB++;
                    Map<BlockPos, InjectedStress> zoneStressB =
                            BlockImpactInjector.inject(invertedZone, resultB, shipB, level);
                    mergeInto(stressOnB, zoneStressB);
                } else {
                    LOGGER.debug("Zone skipped for shipB: impulse={}", resultB != null ? resultB.impulse : null);
                }
            }
        }
        LOGGER.debug("Injected stress from zones: shipAZones={}, shipBZones={}, stressA={}, stressB={}",
                injectedZonesA, injectedZonesB, stressOnA.size(), stressOnB.size());

        Map<BlockPos, BlockStressState> propagatedA =
                StressPropagator.propagate(shipA, stressOnA, level);
        Map<BlockPos, BlockStressState> propagatedB =
                StressPropagator.propagate(shipB, stressOnB, level);
        LOGGER.debug("Propagated stress fields: shipABlocks={}, shipBBlocks={}",
                propagatedA.size(), propagatedB.size());

        // Compare against material strength, apply damage / destroy blocks
        ShipDamageData damageDataA = getOrCreateDamageData(shipA);
        ShipDamageData damageDataB = getOrCreateDamageData(shipB);

        BlockDamageResolver.evaluate(propagatedA, shipA, level, damageDataA);
        BlockDamageResolver.evaluate(propagatedB, shipB, level, damageDataB);
        LOGGER.debug("Processed collision: dim={}, shipA={}, shipB={}", dimId, collision.getShipIdA(), collision.getShipIdB());
    }

    private void mergeInto(Map<BlockPos, InjectedStress> total, Map<BlockPos, InjectedStress> incoming) {
        for (var entry : incoming.entrySet()) {
            total.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                a.add(b.compression, b.shear, b.direction);
                return a;
            });
        }
    }

    private static ShipDamageData getOrCreateDamageData(LoadedServerShip ship) {
        if (ship == null) return null;
        return ship.getOrPutAttachment(ShipDamageData.class, ShipDamageData::new);
    }
}
