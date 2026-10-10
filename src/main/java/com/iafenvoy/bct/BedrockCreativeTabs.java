package com.iafenvoy.bct;

import com.iafenvoy.bct.registry.BctEntryTypes;
import com.iafenvoy.bct.registry.BctItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 1.20.1 resolves the mod through a no-argument constructor and then reads the bus off the loading context, rather
 * than the constructor being handed one.
 */
@Mod(BedrockCreativeTabs.MOD_ID)
public final class BedrockCreativeTabs {
    public static final String MOD_ID = "bedrock_creative_tabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BedrockCreativeTabs() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BctEntryTypes.REGISTRY.register(bus);
        BctItems.REGISTRY.register(bus);
    }
}
