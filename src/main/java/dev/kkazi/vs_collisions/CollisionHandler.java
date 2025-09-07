package dev.kkazi.vs_collisions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4dc;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBi;
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

    private static class CollisionResult {
        public final boolean hasCollision;
        public final Vector3d collisionPoint;
        public final double normalSpeed;

        public CollisionResult(boolean hasCollision, Vector3d collisionPoint, double normalSpeed) {
            this.hasCollision = hasCollision;
            this.collisionPoint = collisionPoint;
            this.normalSpeed = normalSpeed;
        }
        
        public static CollisionResult noCollision() {
            return new CollisionResult(false, null, 0.0);
        }

        public static CollisionResult collision(Vector3d point, double normalSpeed) {
            return new CollisionResult(true, point, normalSpeed);
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
            
            Vector3d velocity = new Vector3d(ship.getVelocity());
            
            double speed = velocity.length();
            
            if (speed < ModConfig.MIN_COLLISION_SPEED.get()) continue;
            
            Vector3d futurePosition = predictShipPosition(ship, velocity);
            
            CollisionResult result = checkForCollisionWithPoint(level, ship, futurePosition);
            if (result.hasCollision) {
                handleCollision(level, (ServerShip) ship, speed, result.collisionPoint);
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
        futurePos.add(velocity.x * ModConfig.PREDICTION_TICKS.get() * 0.05, // 0.05 = 1 tick in seconds
                     velocity.y * ModConfig.PREDICTION_TICKS.get() * 0.05,
                     velocity.z * ModConfig.PREDICTION_TICKS.get() * 0.05);
        
        return futurePos;
    }
    
    private static CollisionResult checkForCollisionWithPoint(ServerLevel level, Ship ship, Vector3d futurePosition) {
        AABBd worldBounds = new AABBd(ship.getWorldAABB());

        Vector3d currentPos = new Vector3d(
            ship.getTransform().getPositionInWorld().x(),
            ship.getTransform().getPositionInWorld().y(),
            ship.getTransform().getPositionInWorld().z()
        );
        Vector3d offset = new Vector3d(futurePosition).sub(currentPos);
        
        AABBd futureBounds = new AABBd(
            worldBounds.minX() + offset.x, worldBounds.minY() + offset.y, worldBounds.minZ() + offset.z,
            worldBounds.maxX() + offset.x, worldBounds.maxY() + offset.y, worldBounds.maxZ() + offset.z
        );
        
        CollisionResult blockResult = checkBlockCollisionsWithPoint(level, futureBounds, new Vector3d(ship.getVelocity()));
        if (blockResult.hasCollision) {
            return blockResult;
        }
        
        return checkShipCollisionsWithPoint(level, ship, futureBounds);
    }
    
    private static CollisionResult checkBlockCollisionsWithPoint(ServerLevel level, AABBd bounds, Vector3d velocity) {
        int minX = (int) Math.floor(bounds.minX());
        int minY = (int) Math.floor(bounds.minY());
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.floor(bounds.maxX());
        int maxY = (int) Math.floor(bounds.maxY());
        int maxZ = (int) Math.floor(bounds.maxZ());

        Vector3d shipCenter = new Vector3d(
                (bounds.minX() + bounds.maxX()) * 0.5,
                (bounds.minY() + bounds.maxY()) * 0.5,
                (bounds.minZ() + bounds.maxZ()) * 0.5
        );

        // Track the closest collision along velocity direction
        Vector3d closestCollisionPoint = null;
        Vector3d closestCollisionNormal = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir() && state.isSolid()) {
                        // Create AABB for this block
                        AABBd blockAABB = new AABBd(x, y, z, x + 1, y + 1, z + 1);

                        // Check for actual overlap
                        if (bounds.intersectsAABB(blockAABB)) {
                            // Calculate collision details
                            Vector3d collisionPoint = computeAABBIntersectionCenter(bounds, blockAABB);
                            Vector3d collisionNormal = computeCollisionNormal(bounds, blockAABB);

                            // Calculate distance along velocity direction to determine which block is hit first
                            double distanceAlongVelocity = calculateDistanceAlongVelocity(shipCenter, collisionPoint, velocity);

                            if (distanceAlongVelocity < closestDistance) {
                                closestDistance = distanceAlongVelocity;
                                closestCollisionPoint = new Vector3d(collisionPoint);
                                closestCollisionNormal = new Vector3d(collisionNormal);
                            }
                        }
                    }
                }
            }
        }

        if (closestCollisionPoint != null) {
            double normalSpeed = Math.abs(velocity.dot(closestCollisionNormal));
            return CollisionResult.collision(closestCollisionPoint, normalSpeed);
        }

        return CollisionResult.noCollision();

    }

    // Calculate distance from ship center to collision point along the velocity vector
    private static double calculateDistanceAlongVelocity(Vector3d shipCenter, Vector3d collisionPoint, Vector3d velocity) {
        if (velocity.lengthSquared() < 1e-10) {
            // If velocity is essentially zero, use euclidean distance
            return shipCenter.distance(collisionPoint);
        }

        Vector3d toCollision = new Vector3d(collisionPoint).sub(shipCenter);
        Vector3d velocityNormalized = new Vector3d(velocity).normalize();

        // Project the collision vector onto the velocity direction
        double projectionLength = toCollision.dot(velocityNormalized);

        // If collision is behind the velocity direction, it's not a "first impact"
        // but we still want to handle it, so use a large positive value
        if (projectionLength < 0) {
            return Double.MAX_VALUE * 0.5; // Large but not MAX to allow comparison
        }

        return projectionLength;
    }
    

    // Helper: compute center point of AABB intersection
    private static Vector3d computeAABBIntersectionCenter(AABBd a, AABBd b) {
        return new Vector3d(
            (Math.max(a.minX(), b.minX()) + Math.min(a.maxX(), b.maxX())) * 0.5,
            (Math.max(a.minY(), b.minY()) + Math.min(a.maxY(), b.maxY())) * 0.5,
            (Math.max(a.minZ(), b.minZ()) + Math.min(a.maxZ(), b.maxZ())) * 0.5
        );

    }

    // Helper methods for AABB center calculations
    private static double centerX(AABBd aabb) {
        return (aabb.minX() + aabb.maxX()) * 0.5;
    }

    private static double centerY(AABBd aabb) {
        return (aabb.minY() + aabb.maxY()) * 0.5;
    }

    private static double centerZ(AABBd aabb) {
        return (aabb.minZ() + aabb.maxZ()) * 0.5;
    }

    // Helper: compute collision normal from minimal overlap axis (points from 'a' toward 'b')
    private static Vector3d computeCollisionNormal(AABBd a, AABBd b) {
        double dx = Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX());
        double dy = Math.min(a.maxY(), b.maxY()) - Math.max(a.minY(), b.minY());
        double dz = Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ());

        // Default normal if no overlap (shouldn't happen if called correctly)
        if (dx < 0 || dy < 0 || dz < 0) {
            return new Vector3d(0, 1, 0);
        }

        if (dx <= dy && dx <= dz) {
            double dir = centerX(a) < centerX(b) ? -1.0 : 1.0;
            return new Vector3d(dir, 0, 0);
        } else if (dy <= dx && dy <= dz) {
            double dir = centerY(a) < centerY(b) ? -1.0 : 1.0;
            return new Vector3d(0, dir, 0);
        } else {
            double dir = centerZ(a) < centerZ(b) ? -1.0 : 1.0;
            return new Vector3d(0, 0, dir);
        }
    }

    private static CollisionResult checkShipCollisionsWithPoint(ServerLevel level, Ship currentShip, AABBd bounds) {
        Vector3d currentVel = new Vector3d(currentShip.getVelocity());
        Vector3d shipCenter = new Vector3d(
                (bounds.minX() + bounds.maxX()) * 0.5,
                (bounds.minY() + bounds.maxY()) * 0.5,
                (bounds.minZ() + bounds.maxZ()) * 0.5
        );

        var intersectingShips = VSGameUtilsKt.getShipsIntersecting(level, bounds);
        // Track the closest collision along velocity direction
        Vector3d closestCollisionPoint = null;
        Vector3d closestCollisionNormal = null;
        Ship closestCollidingShip = null;  // Add this
        double closestDistance = Double.MAX_VALUE;

        for (Ship otherShip : intersectingShips) {
            if (otherShip.getId() == currentShip.getId()) continue; // Skip self

            AABBd otherWorldAABB = new AABBd(otherShip.getWorldAABB());

            // Predict other ship movement
            Vector3d otherVel = new Vector3d(otherShip.getVelocity());
            Vector3d predictedOffsetOther = new Vector3d(otherVel).mul(ModConfig.PREDICTION_TICKS.get() * 0.05);
            AABBd predictedOtherAABB = new AABBd(
                    otherWorldAABB.minX() + predictedOffsetOther.x, otherWorldAABB.minY() + predictedOffsetOther.y, otherWorldAABB.minZ() + predictedOffsetOther.z,
                    otherWorldAABB.maxX() + predictedOffsetOther.x, otherWorldAABB.maxY() + predictedOffsetOther.y, otherWorldAABB.maxZ() + predictedOffsetOther.z
            );

            // Broadphase test: only continue if predicted bounds intersect
            if (!bounds.intersectsAABB(predictedOtherAABB)) continue;

            // Block-level collision detection
            CollisionResult blockCollision = checkShipBlockCollisions(level, currentShip, otherShip, bounds, otherWorldAABB, currentVel, shipCenter, closestDistance);

            if (blockCollision.hasCollision) {
                double distanceAlongVelocity = calculateDistanceAlongVelocity(shipCenter, blockCollision.collisionPoint, currentVel);
                if (distanceAlongVelocity < closestDistance) {
                    closestDistance = distanceAlongVelocity;
                    closestCollisionPoint = new Vector3d(blockCollision.collisionPoint);
                    closestCollisionNormal = computeCollisionNormal(bounds, predictedOtherAABB);
                    closestCollidingShip = otherShip;  // Store the colliding ship
                }
            }
        }

        if (closestCollisionPoint != null) {
            Vector3d otherVel = new Vector3d(closestCollidingShip.getVelocity());
            Vector3d relativeVel = new Vector3d(currentVel).sub(otherVel);  // Current - Other
            double normalSpeed = Math.abs(relativeVel.dot(closestCollisionNormal));
            return CollisionResult.collision(closestCollisionPoint, normalSpeed);
        }

        return CollisionResult.noCollision();
    }

    private static CollisionResult checkShipBlockCollisions(ServerLevel level, Ship currentShip, Ship otherShip, AABBd bounds, AABBd otherBounds, Vector3d currentVel, Vector3d shipCenter, double currentBestDistance) {
        Vector3d closestCollisionPoint = null;
        Vector3d closestCollisionNormal = null;
        double closestDistance = Double.MAX_VALUE;

        AABBi otherShipAABB = new AABBi(otherShip.getShipAABB());

        int minX = otherShipAABB.minX();
        int minY = otherShipAABB.minY();
        int minZ = otherShipAABB.minZ();
        int maxX = otherShipAABB.maxX();
        int maxY = otherShipAABB.maxY();
        int maxZ = otherShipAABB.maxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(blockPos);

                    if (!state.isAir() && state.isSolid()) {
                        // Create AABB for this block in world space
                        Vector3d blockPosWorld = VSGameUtilsKt.toWorldCoordinates(otherShip, blockPos);

                        AABBd blockAABB = new AABBd(
                                blockPosWorld.x, blockPosWorld.y, blockPosWorld.z,   // min corner
                                blockPosWorld.x+1, blockPosWorld.y+1, blockPosWorld.z+1   // max corner
                        );
                        // Check if current ship's bounds intersect with this block
                        if (bounds.intersectsAABB(blockAABB)) {
                            Vector3d collisionPoint = computeAABBIntersectionCenter(bounds, blockAABB);
                            Vector3d collisionNormal = computeCollisionNormal(bounds, blockAABB);

                            double distanceAlongVelocity = calculateDistanceAlongVelocity(shipCenter, collisionPoint, currentVel);
                            if (distanceAlongVelocity < closestDistance && distanceAlongVelocity < currentBestDistance) {
                                closestDistance = distanceAlongVelocity;
                                closestCollisionPoint = new Vector3d(collisionPoint);
                                closestCollisionNormal = new Vector3d(collisionNormal);
                            }
                        }
                    }
                }
            }
        }

        if (closestCollisionPoint != null) {
            return CollisionResult.collision(closestCollisionPoint, 0.0); // Speed calculated later
        }

        return CollisionResult.noCollision();
    }

    private static void handleCollision(ServerLevel level, ServerShip ship, double speed, Vector3d collisionPoint) {
        LOGGER.info("Ship collision detected! Ship ID: {}, Speed: {}", ship.getId(), speed);

        double shipMass = ship.getInertiaData().getMass();
        double impactEnergy = 0.5 * shipMass * speed * speed;
        float explosionStrength = (float) Math.min(impactEnergy * ModConfig.SCALING_FACTOR.get(), ModConfig.MAX_EXPLOSION_POWER.get());
        sendDebugMessage(level.getServer(), "energy: "+impactEnergy+" explosion: "+explosionStrength);

        Explosion explosion = new Explosion(
                level,
                null,
                collisionPoint.x,
                collisionPoint.y,
                collisionPoint.z,
                explosionStrength,
                false,
                Explosion.BlockInteraction.DESTROY
        );

        explosion.explode();
        explosion.finalizeExplosion(true);

        LOGGER.info("Explosion created at collision point ({}, {}, {}) with power {}",
            collisionPoint.x, collisionPoint.y, collisionPoint.z, explosionStrength);
    }

    public static void sendDebugMessage(MinecraftServer server, String message) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[DEBUG] " + message),
                    false
            );
        }
    }
}