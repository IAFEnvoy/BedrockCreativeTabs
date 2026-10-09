package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.data.CreativeGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

/**
 * The three components an icon carries. The identity ones keep two groups' icons apart, since a tab's entry set
 * compares item and components - without them, icons of the same item would collapse into one entry.
 *
 * <p>None is persistent. A persistent component could be saved or sent, and the group it names exists only in the
 * client's resource packs, so an icon carrying one would refer to something a server cannot resolve. Leaving the codec
 * off makes that structural: there is no way to encode one even if a path to somewhere else were found.
 */
public final class BctDataComponents {
    public static final DataComponents REGISTRY = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BedrockCreativeTabs.MOD_ID);

    /**
     * The group an icon stands for.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreativeGroup>> CREATIVE_GROUP = register("creative_group", CreativeGroup.CODEC);
    /**
     * The id that group is filed under. It cannot live on {@link CreativeGroup}, which no longer carries one, and the
     * member tables are keyed by it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> CREATIVE_GROUP_ID = register("creative_group_id", Identifier.CODEC);
    /**
     * Which tab this icon was folded in. A group may name several tabs, so the icon says which list a click opens.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<CreativeModeTab>>> CREATIVE_GROUP_TAB = register("creative_group_tab", ResourceKey.codec(Registries.CREATIVE_MODE_TAB));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String id, Codec<T> codec) {
        return REGISTRY.registerComponentType(id, b -> b.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec)));
    }

    private BctDataComponents() {
    }
}
