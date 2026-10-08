package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.data.CreativeGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

/**
 * The two components an icon carries. Both are needed by the icon alone, and both are what keep two groups' icons
 * apart: a tab's entry set compares item and components, so two icons of the same item without these would collapse
 * into one entry.
 *
 * <p>Neither is <b>persistent</b>, and that is a deliberate consequence of being a client mod rather than an oversight.
 * A persistent component is one an item stack can be saved and sent with, and these two are the client's own reading
 * of a resource pack: the group they name does not exist on a server, so an icon that could be written to disk, put in
 * a container or sent to a server would be a stack referring to something the other side cannot resolve. The icon
 * cannot leave the client in the first place - {@code CREATIVE_SLOT_LOCK} refuses every click path - and leaving the
 * codec off makes that structural: there is no way to encode one even if a path were found.
 */
public final class BctDataComponents {
    public static final DataComponents REGISTRY = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BedrockCreativeTabs.MOD_ID);

    /**
     * The group an icon stands for.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreativeGroup>> CREATIVE_GROUP =
            register("creative_group", CreativeGroup.CODEC);
    /**
     * Which tab this icon was folded in. A group may name several tabs, so the icon has to say which list a click
     * opens and which list the mark reads.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<CreativeModeTab>>> CREATIVE_GROUP_TAB =
            register("creative_group_tab", ResourceKey.codec(Registries.CREATIVE_MODE_TAB));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String id, Codec<T> codec) {
        return REGISTRY.registerComponentType(id, b -> b.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec)));
    }

    private BctDataComponents() {
    }
}
