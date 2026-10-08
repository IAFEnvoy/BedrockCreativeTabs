package com.iafenvoy.bct;

import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.registry.BctEntryTypes;
import com.iafenvoy.bct.registry.BctItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BedrockCreativeTabs.MOD_ID)
public final class BedrockCreativeTabs {
    public static final String MOD_ID = "bedrock_creative_tabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BedrockCreativeTabs(IEventBus bus) {
        BctDataComponents.REGISTRY.register(bus);
        BctEntryTypes.REGISTRY.register(bus);
        BctItems.REGISTRY.register(bus);
    }
}
