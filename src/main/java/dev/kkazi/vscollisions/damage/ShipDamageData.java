package dev.kkazi.vscollisions.damage;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class ShipDamageData {

    // Accumulated fatigue per block, 0.0 = pristine, 1.0 = about to fail
    private final Map<BlockPos, Float> accumulated = new HashMap<>();

    public float getAccumulated(BlockPos pos) {
        return accumulated.getOrDefault(pos, 0.0f);
    }

    public void setAccumulated(BlockPos pos, float value) {
        if (value <= 0.0f) accumulated.remove(pos);
        else accumulated.put(pos, Math.min(1.0f, value));
    }

    public void remove(BlockPos pos) {
        accumulated.remove(pos);
    }

    // Clean up entries for blocks that no longer exist
    // Called periodically to prevent memory leak
    public void prune(java.util.Set<BlockPos> existingBlocks) {
        accumulated.keySet().retainAll(existingBlocks);
    }
}
