package com.iafenvoy.bct;

import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.registry.BctEntryTypes;
import com.iafenvoy.bct.registry.BctItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Configurable creative tabs: a resource pack names a set of stacks and the tabs they are folded in, and each of those
 * tabs shows them as one icon that opens where it stands. Everything the mod does is driven by
 * {@code assets/<pack>/bedrock_creative_tabs/creative_group/} definitions; the mod itself ships no group.
 *
 * <p>The entrypoint is client only, which is the design and not a packaging choice: a creative tab is built by the
 * client's creative screen and by nothing else, so a server has no tab list to fold. Saying so here keeps the mod off
 * a dedicated server entirely rather than having it load and find nothing to do.
 */
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
