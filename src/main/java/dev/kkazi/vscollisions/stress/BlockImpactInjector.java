package dev.kkazi.vscollisions.stress;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import dev.kkazi.vscollisions.impact.ImpactZone;
import dev.kkazi.vscollisions.impact.ImpactZoneResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.HashMap;
import java.util.Map;

public final class BlockImpactInjector {

    private static final double MIN_RADIUS = 2.0;
    private static final double MAX_RADIUS = 6.0;

    private BlockImpactInjector() {
    }

    public static Map<BlockPos, InjectedStress> inject(
            ImpactZone zone, ImpactZoneResult result,
            LoadedServerShip ship, ServerLevel level) {

        Map<BlockPos, InjectedStress> stressMap = new HashMap<>();

        Vector3d localPos = new Vector3d();
        ship.getTransform().getWorldToShip().transformPosition(zone.position, localPos);
        Vector3d localDir = new Vector3d();
        ship.getTransform().getWorldToShip().transformDirection(result.impactDirection, localDir);
        localDir.normalize();

        BlockPos seed = new BlockPos(
                (int) Math.floor(localPos.x), (int) Math.floor(localPos.y), (int) Math.floor(localPos.z));

        BlockPos impactBlock = findNearestSolidBlock(seed, level, 3);
        if (impactBlock == null) {
            LOGGER.warn("No solid ship block found near impact seed {} for ship {}", seed, ship.getId());
            return stressMap;
        }

        double baseRadius = Math.sqrt(zone.area / Math.PI);
        double torqueBonus = result.torqueFactor * 3.0;
        double radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, baseRadius + torqueBonus));
        double radiusSq = radius * radius;
        int blockRadius = (int) Math.ceil(radius);

        Map<BlockPos, Double> rawWeights = new HashMap<>();
        double totalWeight = 0;

        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dy = -blockRadius; dy <= blockRadius; dy++) {
                for (int dz = -blockRadius; dz <= blockRadius; dz++) {

                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq > radiusSq) continue;

                    BlockPos pos = impactBlock.offset(dx, dy, dz);
                    if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) continue;

                    if (level.getBlockState(pos).isAir()) continue;

                    double distance = Math.sqrt(distSq);
                    double falloff = 1.0 - (distance / radius);
                    if (falloff <= 0) continue;

                    double alignment;
                    if (distSq == 0) {
                        alignment = 1.0; // center block, full alignment
                    } else {
                        Vector3d toBlock = new Vector3d(dx, dy, dz).normalize();
                        alignment = Math.max(0, localDir.dot(toBlock));
                    }
                    double directionalWeight = 0.5 + 0.5 * alignment;

                    double weight = falloff * directionalWeight;
                    rawWeights.put(pos, weight);
                    totalWeight += weight;
                }
            }
        }

        if (totalWeight <= 1e-9) return stressMap;

        double normalShare = result.normalEnergy / (result.kineticEnergy + 1e-9);
        double shearShare = result.shearEnergy / (result.kineticEnergy + 1e-9);

        for (var entry : rawWeights.entrySet()) {
            BlockPos pos = entry.getKey();
            double normalizedWeight = entry.getValue() / totalWeight;
            double compression = result.damageEnergy * normalizedWeight * normalShare;
            double shear = result.damageEnergy * normalizedWeight * shearShare;

            if (stressMap.containsKey(pos)) {
                stressMap.get(pos).add(compression, shear, localDir);
            } else {
                stressMap.put(pos, new InjectedStress(compression, shear, new Vector3d(localDir)));
            }
        }

        LOGGER.debug(
                "Injected stress into ship {}: impactBlock={}, affectedBlocks={}, radius={}",
                ship.getId(), impactBlock, stressMap.size(), radius);
        return stressMap;
    }

    private static BlockPos findNearestSolidBlock(
            BlockPos start, ServerLevel level, int maxSearchRadius) {

        if (!level.getBlockState(start).isAir() && VSGameUtilsKt.isBlockInShipyard(level, start)) {
            return start;
        }

        for (int r = 1; r <= maxSearchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // only check the shell of this radius, not the whole filled cube again
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;

                        BlockPos candidate = start.offset(dx, dy, dz);
                        if (!level.getBlockState(candidate).isAir()
                                && VSGameUtilsKt.isBlockInShipyard(level, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }
}
