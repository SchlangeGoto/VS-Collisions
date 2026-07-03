package dev.kkazi.vscollisions;

import dev.kkazi.vscollisions.collision.CollisionEventHandler;
import dev.kkazi.vscollisions.damage.DamageApplicator;
import dev.kkazi.vscollisions.damage.ShipDamageData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;


@Mod(VSCollisions.MOD_ID)
public class VSCollisions {
    public static final String MOD_ID = "vs_collisions";
    public static final Logger LOGGER = LogUtils.getLogger();


    public VSCollisions() {
        //ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, ModConfig.SPEC);
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::init);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DamageApplicator());
        LOGGER.info("VSCollisions started");
    }
    private void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Registering Valkyrien Skies attachment {}", ShipDamageData.class.getName());
            var api = ValkyrienSkiesMod.getApi();
            var registration = api.newAttachmentRegistrationBuilder(ShipDamageData.class)
                    .useTransientSerializer()
                    .build();
            api.registerAttachment(registration);
            LOGGER.info("Registered attachment {}", ShipDamageData.class.getName());
        });
        LOGGER.info("Registering collision handlers");
        CollisionEventHandler.registerCollisionEvent();
    }
}
