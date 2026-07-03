package dev.kkazi.vscollisions.material;

import static dev.kkazi.vscollisions.VSCollisions.LOGGER;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

public final class CbcToughnessHelper {

    private static final String CBC_MOD_ID = "createbigcannons";

    public record BlockProps(double hardness, double toughness, boolean fromCbc) {}

    private CbcToughnessHelper() {}

    public static BlockProps getBlockProps(Level level, BlockState state, BlockPos pos) {
        if (ModList.get().isLoaded(CBC_MOD_ID)) {
            BlockProps cbc = CbcImpl.tryGet(level, state, pos);
            if (cbc != null) {
                LOGGER.debug("CBC block props resolved for {}", pos);
                return cbc;
            }
        }
        LOGGER.debug("Using fallback block props for {}", pos);
        return new BlockProps(1.0, state.getExplosionResistance(level, pos, null), false);
    }

    private static final class CbcImpl {
        static BlockProps tryGet(Level level, BlockState state, BlockPos pos) {
            var provider = rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler.getProperties(state);
            if (provider == null) return null;
            double toughness = provider.toughness(level, state, pos, true);
            double hardness = provider.hardness(level, state, pos, true);
            return new BlockProps(hardness, toughness, true);
        }
    }
}

