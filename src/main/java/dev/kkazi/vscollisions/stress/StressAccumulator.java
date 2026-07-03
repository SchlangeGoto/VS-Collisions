package dev.kkazi.vscollisions.stress;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class StressAccumulator {

    private StressAccumulator() {
    }

    public static Map<BlockPos, BlockStressState> accumulate(
            Map<BlockPos, InjectedStress> injected) {

        Map<BlockPos, BlockStressState> result = new HashMap<>();

        for (var entry : injected.entrySet()) {
            InjectedStress s = entry.getValue();
            result.put(entry.getKey(),
                    new BlockStressState(s.compression, s.shear, s.direction));
        }

        return result;
    }
}
