package dev.kkazi.vs_collisions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBic;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipInertiaData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mod.EventBusSubscriber(modid = VS_Collisions.MOD_ID)
public class CollisionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CollisionHandler.class);

    private static final double MIN_COLLISION_SPEED = 0.0; // Minimum speed to trigger collision
    private static final double MAX_EXPLOSION_POWER = 5000.0;
    private static final double SCALING_FACTOR = 25.0;
    private static final int PREDICTION_TICKS = 3; // How many ticks ahead to predict
    
    private static class CollisionResult {
        public final boolean hasCollision;
        public final Vector3d collisionPoint;
        
        public CollisionResult(boolean hasCollision, Vector3d collisionPoint) {
            this.hasCollision = hasCollision;
            this.collisionPoint = collisionPoint;
        }
        
        public static CollisionResult noCollision() {
            return new CollisionResult(false, null);
        }
        
        public static CollisionResult collision(Vector3d point) {
            return new CollisionResult(true, point);
        }
    }
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            checkShipCollisions(level);
        }
    }

    private static void checkShipCollisions(ServerLevel level) {
        var ships = VSGameUtilsKt.getAllShips(level);
        
        for (Ship ship : ships) {
            if (!(ship instanceof ServerShip serverShip)) continue;
            
            Vector3d velocity = new Vector3d(ship.getVelocity().x(), ship.getVelocity().y(), ship.getVelocity().z());
            double speed = velocity.length();
            
            if (speed < MIN_COLLISION_SPEED) continue;
            
            Vector3d futurePosition = predictShipPosition(ship, velocity);
            
            CollisionResult result = checkForCollisionWithPoint(level, ship, futurePosition);
            if (result.hasCollision) {
                handleCollision(level, (ServerShip) ship, speed, result.collisionPoint);
                sendDebugMessage(level.getServer(), "check works");
            }
        }
    }
    
    private static Vector3d predictShipPosition(Ship ship, Vector3d velocity) {
        Vector3d currentPos = new Vector3d(
            ship.getTransform().getPositionInWorld().x(),
            ship.getTransform().getPositionInWorld().y(),
            ship.getTransform().getPositionInWorld().z()
        );
        
        Vector3d futurePos = new Vector3d(currentPos);
        futurePos.add(velocity.x * PREDICTION_TICKS * 0.05, // 0.05 = 1 tick in seconds
                     velocity.y * PREDICTION_TICKS * 0.05,
                     velocity.z * PREDICTION_TICKS * 0.05);
        
        return futurePos;
    }
    
    private static AABBd convertToAABBd(AABBic intAABB) {
        return new AABBd(
            intAABB.minX(), intAABB.minY(), intAABB.minZ(),
            intAABB.maxX(), intAABB.maxY(), intAABB.maxZ()
        );
    }
    
    private static CollisionResult checkForCollisionWithPoint(ServerLevel level, Ship ship, Vector3d futurePosition) {
        AABBic shipBoundsInt = ship.getShipAABB();
        AABBd shipBounds = convertToAABBd(shipBoundsInt);
        
        Vector3d currentPos = new Vector3d(
            ship.getTransform().getPositionInWorld().x(),
            ship.getTransform().getPositionInWorld().y(),
            ship.getTransform().getPositionInWorld().z()
        );
        Vector3d offset = new Vector3d(futurePosition).sub(currentPos);
        
        AABBd futureBounds = new AABBd(
            shipBounds.minX() + offset.x, shipBounds.minY() + offset.y, shipBounds.minZ() + offset.z,
            shipBounds.maxX() + offset.x, shipBounds.maxY() + offset.y, shipBounds.maxZ() + offset.z
        );
        
        CollisionResult blockResult = checkBlockCollisionsWithPoint(level, futureBounds, ship.getVelocity());
        if (blockResult.hasCollision) {
            return blockResult;
        }
        
        return checkShipCollisionsWithPoint(level, ship, futureBounds);
    }
    
    private static CollisionResult checkBlockCollisionsWithPoint(ServerLevel level, AABBd bounds, org.joml.Vector3dc velocity) {
        int minX = (int) Math.floor(bounds.minX());
        int minY = (int) Math.floor(bounds.minY());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = (int) Math.ceil(bounds.maxY());
        int maxZ = (int) Math.ceil(bounds.maxZ());
        
        Vector3d velocityNormalized = new Vector3d(velocity.x(), velocity.y(), velocity.z()).normalize();
        Vector3d closestCollision = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    
                    if (!state.isAir() && state.isSolid()) {
                        Vector3d blockCenter = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                        Vector3d shipCenter = new Vector3d(
                            (bounds.minX() + bounds.maxX()) * 0.5,
                            (bounds.minY() + bounds.maxY()) * 0.5,
                            (bounds.minZ() + bounds.maxZ()) * 0.5
                        );
                        
                        Vector3d collisionPoint = calculateBlockCollisionPoint(shipCenter, blockCenter, velocityNormalized);
                        
                        double distance = shipCenter.distance(collisionPoint);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestCollision = new Vector3d(collisionPoint);
                        }
                    }
                }
            }
        }
        
        return closestCollision != null ? CollisionResult.collision(closestCollision) : CollisionResult.noCollision();
    }
    
    private static Vector3d calculateBlockCollisionPoint(Vector3d shipCenter, Vector3d blockCenter, Vector3d velocityNormalized) {
        Vector3d toBlock = new Vector3d(blockCenter).sub(shipCenter);
        
        Vector3d collisionPoint = new Vector3d(blockCenter);
        
        // Determine which face to place the explosion on based on velocity direction
        if (Math.abs(velocityNormalized.x) > Math.abs(velocityNormalized.y) && Math.abs(velocityNormalized.x) > Math.abs(velocityNormalized.z)) {
            collisionPoint.x = blockCenter.x + (velocityNormalized.x > 0 ? -0.5 : 0.5);
        } else if (Math.abs(velocityNormalized.y) > Math.abs(velocityNormalized.z)) {
            collisionPoint.y = blockCenter.y + (velocityNormalized.y > 0 ? -0.5 : 0.5);
        } else {
            collisionPoint.z = blockCenter.z + (velocityNormalized.z > 0 ? -0.5 : 0.5);
        }
        
        return collisionPoint;
    }
    
    private static CollisionResult checkShipCollisionsWithPoint(ServerLevel level, Ship currentShip, AABBd bounds) {
        var allShips = VSGameUtilsKt.getAllShips(level);
        
        for (Ship otherShip : allShips) {
            if (otherShip.getId() == currentShip.getId()) continue; // Skip self
            
            AABBic otherBoundsInt = otherShip.getShipAABB();
            AABBd otherBounds = convertToAABBd(otherBoundsInt);
            
            Vector3d otherPos = new Vector3d(
                otherShip.getTransform().getPositionInWorld().x(),
                otherShip.getTransform().getPositionInWorld().y(),
                otherShip.getTransform().getPositionInWorld().z()
            );
            
            AABBd worldOtherBounds = new AABBd(
                otherBounds.minX() + otherPos.x, otherBounds.minY() + otherPos.y, otherBounds.minZ() + otherPos.z,
                otherBounds.maxX() + otherPos.x, otherBounds.maxY() + otherPos.y, otherBounds.maxZ() + otherPos.z
            );
            
            if (bounds.intersectsAABB(worldOtherBounds)) {
                Vector3d collisionPoint = new Vector3d(
                    (Math.max(bounds.minX(), worldOtherBounds.minX()) + Math.min(bounds.maxX(), worldOtherBounds.maxX())) * 0.5,
                    (Math.max(bounds.minY(), worldOtherBounds.minY()) + Math.min(bounds.maxY(), worldOtherBounds.maxY())) * 0.5,
                    (Math.max(bounds.minZ(), worldOtherBounds.minZ()) + Math.min(bounds.maxZ(), worldOtherBounds.maxZ())) * 0.5
                );
                return CollisionResult.collision(collisionPoint);
            }
        }
        return CollisionResult.noCollision();
    }

    private static void handleCollision(ServerLevel level, ServerShip ship, double speed, Vector3d collisionPoint) {
        sendDebugMessage(level.getServer(), "pls work");

        LOGGER.info("Ship collision detected! Ship ID: {}, Speed: {}", ship.getId(), speed);

        double shipMass = ship.getInertiaData().getMass();
        double impactEnergy = 0.5 * shipMass * speed * speed;
        float explosionStrength = (float) Math.min(impactEnergy / SCALING_FACTOR, MAX_EXPLOSION_POWER);

        Explosion explosion = new Explosion(
                level,
                null,                // No source entity
                collisionPoint.x,
                collisionPoint.y,
                collisionPoint.z,
                explosionStrength,   // Explosion radius/power
                true,                // Causes fire
                Explosion.BlockInteraction.DESTROY // Correct enum for 1.20.1
        );


        explosion.explode();

        LOGGER.info("Explosion created at collision point ({}, {}, {}) with power {}",
            collisionPoint.x, collisionPoint.y, collisionPoint.z, explosionStrength);
        sendDebugMessage(level.getServer(), "IT WORKED");
    }

    public static void sendDebugMessage(MinecraftServer server, String message) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[DEBUG] " + message),
                    false // false = chat, true = action bar
            );
        }
    }
  /*
    private static double calculateShipVolume(Ship ship) {
        AABBic boundsInt = ship.getShipAABB();
        AABBd bounds = convertToAABBd(boundsInt);
        return (bounds.maxX() - bounds.minX()) * (bounds.maxY() - bounds.minY()) * (bounds.maxZ() - bounds.minZ());
    }*/

}