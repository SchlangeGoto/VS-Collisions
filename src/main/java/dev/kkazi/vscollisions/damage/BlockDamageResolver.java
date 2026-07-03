package dev.kkazi.vscollisions.damage;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import dev.kkazi.vscollisions.material.CollisionMaterialRegistry;
import dev.kkazi.vscollisions.material.CollisionMaterialRegistry.CollisionMaterialProps;
import dev.kkazi.vscollisions.stress.BlockStressState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.Map;

public final class BlockDamageResolver {

    // Von Mises criterion — shear stress is √3 times more damaging than compression
    // for the same magnitude, so shear counts 3x in the combined stress
    private static final double SHEAR_VON_MISES = 3.0;

    // Bridges physics stress units (derived from kg*m²/s²) to CBC toughness scale
    // Tune this via playtesting:
    // too high = everything breaks from gentle contact
    // too low = nothing ever breaks
    // Start conservative and increase until steel armor breaks at realistic tank speeds
    private static final double STRESS_SCALE = 1.0 / 100000.0;

    // How much fatigue accumulates per hit in the plastic zone
    // 0.3 = roughly 3-4 significant hits before a block weakens to failure
    private static final float FATIGUE_RATE = 0.3f;

    private BlockDamageResolver() {
    }

    public static void evaluate(
            Map<BlockPos, BlockStressState> stressField,
            LoadedServerShip ship,
            ServerLevel level,
            ShipDamageData damageData) {

        if (stressField == null || stressField.isEmpty()) return;
        if (ship == null || damageData == null) return;

        int yielded = 0;
        int destroyed = 0;
        int cracked = 0;

        for (var entry : stressField.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockStressState stress = entry.getValue();

            if (!ValkyrienSkies.isBlockInShipyard(level, pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            // Get material properties — CBC + mining hardness, with override support
            CollisionMaterialProps matProps =
                    CollisionMaterialRegistry.get(level, state, pos);

            // Indestructible block (bedrock, etc)
            if (matProps.yieldThreshold() == Double.MAX_VALUE) continue;

            // Apply fatigue — accumulated damage lowers effective strength
            float accumulated = damageData.getAccumulated(pos);
            double fatigueMultiplier = Math.max(0.05, 1.0 - accumulated);
            // floor at 0.05 so a heavily damaged block still has some resistance,
            // preventing instant destruction from any tiny subsequent stress

            double effectiveYield    = matProps.yieldThreshold()    * fatigueMultiplier;
            double effectiveUltimate = matProps.ultimateThreshold()  * fatigueMultiplier;

            // Von Mises stress — combines compression and shear into one scalar
            // Scaled to match CBC toughness value range
            double scaledCompression = stress.compression * STRESS_SCALE;
            double scaledShear       = stress.shear       * STRESS_SCALE;
            double vonMises = Math.sqrt(
                    scaledCompression * scaledCompression
                            + SHEAR_VON_MISES * scaledShear * scaledShear
            );

            if (vonMises < effectiveYield) {
                // Elastic zone — no permanent effect, block springs back
                continue;
            }

            LOGGER.debug(
                    "Damage candidate for ship {} at {}: vonMises={}, yield={}, ultimate={}, accumulated={}",
                    ship.getId(), pos, vonMises, effectiveYield, effectiveUltimate, accumulated);

            if (vonMises >= effectiveUltimate) {
                // Failure — block destroyed
                // VSplit automatically handles structural separation
                level.destroyBlock(pos, true);
                damageData.remove(pos);
                destroyed++;
                continue;
            }

            // Plastic zone — accumulate fatigue, show crack overlay
            // How far through the plastic zone (0.0 = just yielded, 1.0 = about to fail)
            double damageRatio = (vonMises - effectiveYield)
                    / (effectiveUltimate - effectiveYield);

            float newAccumulated = Math.min(1.0f,
                    accumulated + (float)(damageRatio * FATIGUE_RATE));
            damageData.setAccumulated(pos, newAccumulated);
            yielded++;

            // Minecraft crack overlay (0-9)
            // Use overall accumulated damage for visual, not just this hit's ratio
            // so the crack deepens progressively across multiple hits
            int crackStage = (int)(newAccumulated * 9);
            level.destroyBlockProgress(pos.hashCode(), pos, crackStage);
            cracked++;
        }

        LOGGER.debug("Material evaluation complete for ship {}: yielded={}, cracked={}, destroyed={}",
                ship.getId(), yielded, cracked, destroyed);
    }
}
