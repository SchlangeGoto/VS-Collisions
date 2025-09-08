package dev.kkazi.vs_collisions;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VS_Collisions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Double> MIN_COLLISION_SPEED;
    public static final ForgeConfigSpec.ConfigValue<Double> MAX_EXPLOSION_POWER;
    public static final ForgeConfigSpec.ConfigValue<Double> SCALING_FACTOR;
    public static final ForgeConfigSpec.ConfigValue<Integer> PREDICTION_TICKS;




    static {
        BUILDER.push("General Settings");

        MIN_COLLISION_SPEED = BUILDER
                .comment("Minimum speed that ships need so a collision gets triggered")
                .defineInRange("minCollisionSpeed", 15.0, 0.0, 1000000.0);

        MAX_EXPLOSION_POWER = BUILDER
                .comment("Max strength a explosion can be")
                .defineInRange("maxExplosionPower", 30.0, 0.0, 1000000.0);

        SCALING_FACTOR = BUILDER
                .comment("The Scaling factor desides how big explosions depending on the impact are, higher factor = big explosion, lower factor = small explosion")
                .defineInRange("scalingFactor", 0.00001, 0.0, 100.0);

        PREDICTION_TICKS = BUILDER
                .comment("How many ticks in the future the mod should predict the impact, higher number means less delay on impact, explosions happen faster on impact, lower number means higher precision for collisions, but maybe a delay on impact")
                .defineInRange("predictionTicks", 3, 0, 10);


        BUILDER.pop();
        SPEC = BUILDER.build();
    }

}