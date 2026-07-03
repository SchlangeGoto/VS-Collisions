package dev.kkazi.vscollisions.stress;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import dev.kkazi.vscollisions.material.CbcToughnessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class StressPropagator {

    private static final int MAX_DEPTH = 3;
    private static final double MIN_STRESS_THRESHOLD = 10.0;
    private static final double MAX_TOUGHNESS = 100.0;
    private static final Direction[] DIRECTIONS = Direction.values();

    private StressPropagator() {
    }

    public static Map<BlockPos, BlockStressState> propagate(
            LoadedServerShip ship,
            Map<BlockPos, InjectedStress> injected,
            ServerLevel level) {

        if (ship == null || injected == null || injected.isEmpty()) {
            LOGGER.debug("Skipping stress propagation: shipPresent={}, injectedBlocks={}",
                    ship != null, injected != null ? injected.size() : null);
            return Map.of();
        }

        // Step 1 — convert injected stress to initial stress field
        Map<BlockPos, BlockStressState> stressField = StressAccumulator.accumulate(injected);
        if (stressField.isEmpty()) {
            LOGGER.debug("Stress accumulation produced no blocks for ship {}", ship.getId());
            return Map.of();
        }

        // Working accumulator — tracks compression, shear, direction sum, direction weight
        // double[4] = {compression, shear, directionWeightSum, <unused>}
        Map<BlockPos, double[]> working = new HashMap<>();
        Map<BlockPos, Vector3d> directionSums = new HashMap<>();

        // Seed from initial stress field
        for (var entry : stressField.entrySet()) {
            BlockStressState s = entry.getValue();
            working.put(entry.getKey(), new double[]{s.compression, s.shear, s.totalStress});
            directionSums.put(entry.getKey(), new Vector3d(s.direction).mul(s.totalStress));
        }

        // Process depth by depth — ensures all contributions at depth N
        // are known before processing depth N+1
        Set<BlockPos> currentLayer = new HashSet<>(stressField.keySet());

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            if (currentLayer.isEmpty()) break;

            // Accumulate contributions from this layer into next layer
            Map<BlockPos, double[]> nextContributions = new HashMap<>();
            Map<BlockPos, Vector3d> nextDirections = new HashMap<>();

            for (BlockPos pos : currentLayer) {
                double[] current = working.get(pos);
                if (current == null) continue;

                double compression = current[0];
                double shear = current[1];
                double totalStress = Math.sqrt(compression*compression + shear*shear);

                if (totalStress < MIN_STRESS_THRESHOLD) continue;

                Vector3d direction = directionSums.get(pos);
                Vector3d normalizedDir = direction != null && direction.lengthSquared() > 1e-9
                        ? new Vector3d(direction).normalize()
                        : new Vector3d(0, 0, 1);

                BlockState sourceState = level.getBlockState(pos);
                CbcToughnessHelper.BlockProps sourceProps = CbcToughnessHelper.getBlockProps(level, sourceState, pos);
                double transferFactor = Math.min(0.5, sourceProps.toughness() / MAX_TOUGHNESS * 0.5);

                for (Direction dir : DIRECTIONS) {
                    BlockPos neighbor = pos.relative(dir);

                    // Don't propagate back into already-seeded blocks
                    if (stressField.containsKey(neighbor)) continue;
                    if (!ValkyrienSkies.isBlockInShipyard(level, neighbor)) continue;

                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.isAir()) continue;

                    // Directional weighting — no backward propagation
                    Vector3d dirVec = new Vector3d(
                            dir.getStepX(), dir.getStepY(), dir.getStepZ());
                    double alignment = Math.max(0.0, normalizedDir.dot(dirVec));
                    if (alignment < 1e-6) continue; // truly perpendicular or backward, skip

                    double effectiveTransfer = transferFactor * alignment;
                    double transferredCompression = compression * effectiveTransfer;
                    double transferredShear = shear * effectiveTransfer;
                    double transferredWeight = totalStress * effectiveTransfer;

                    // Accumulate — multiple source blocks can contribute to the same neighbor
                    nextContributions.merge(neighbor,
                            new double[]{transferredCompression, transferredShear, transferredWeight},
                            (a, b) -> new double[]{a[0]+b[0], a[1]+b[1], a[2]+b[2]});

                    // Weighted direction accumulation
                    Vector3d weightedDir = new Vector3d(normalizedDir).mul(transferredWeight);
                    nextDirections.merge(neighbor, weightedDir,
                            (a, b) -> new Vector3d(a).add(b));
                }
            }

            // Merge next layer contributions into working map
            Set<BlockPos> nextLayer = new HashSet<>();
            for (var entry : nextContributions.entrySet()) {
                BlockPos neighbor = entry.getKey();
                double[] contrib = entry.getValue();

                working.merge(neighbor, contrib,
                        (a, b) -> new double[]{a[0]+b[0], a[1]+b[1], a[2]+b[2]});

                directionSums.merge(neighbor,
                        nextDirections.getOrDefault(neighbor, new Vector3d()),
                        (a, b) -> new Vector3d(a).add(b));

                nextLayer.add(neighbor);
            }

            currentLayer = nextLayer;
        }

        // Step 3 — convert working map to final BlockStressState
        Map<BlockPos, BlockStressState> result = new HashMap<>();
        for (var entry : working.entrySet()) {
            BlockPos pos = entry.getKey();
            double[] stress = entry.getValue();
            Vector3d dirSum = directionSums.get(pos);
            Vector3d finalDir = (dirSum != null && dirSum.lengthSquared() > 1e-9)
                    ? new Vector3d(dirSum).normalize()
                    : new Vector3d(0, 0, 1);

            result.put(pos, new BlockStressState(stress[0], stress[1], finalDir));
        }

        LOGGER.debug("Stress propagation complete for ship {}: inputBlocks={}, outputBlocks={}",
                ship.getId(), injected.size(), result.size());
        return result;
    }
}
