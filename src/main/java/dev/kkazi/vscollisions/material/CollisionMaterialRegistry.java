package dev.kkazi.vscollisions.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public final class CollisionMaterialRegistry {
    private static final double YIELD_FACTOR = 0.4;
    private static final double ULTIMATE_FACTOR = 1.0;

    private static final Map<ResourceLocation, CollisionMaterialProps> OVERRIDES = new HashMap<>();

    private CollisionMaterialRegistry() {
    }

    public record CollisionMaterialProps(
            double yieldThreshold,
            double ultimateThreshold
    ) {}

    public static CollisionMaterialProps get(
            ServerLevel level, BlockState state, BlockPos pos) {

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && OVERRIDES.containsKey(blockId)) {
            return OVERRIDES.get(blockId);
        }

        CbcToughnessHelper.BlockProps props = CbcToughnessHelper.getBlockProps(level, state, pos);
        double miningHardness = state.getDestroySpeed(level, pos);

        // Indestructible blocks (bedrock etc) have destroySpeed -1
        if (miningHardness < 0) {
            return new CollisionMaterialProps(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        return new CollisionMaterialProps(
                miningHardness  * YIELD_FACTOR,
                props.toughness() * ULTIMATE_FACTOR
        );
    }

    // Called during datapack reload
    public static void registerOverride(
            ResourceLocation block, double yield, double ultimate) {
        OVERRIDES.put(block, new CollisionMaterialProps(yield, ultimate));
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
    }
}
