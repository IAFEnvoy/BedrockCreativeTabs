package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;

/**
 * The one registry this mod owns: the code registry of entry types other mods add to.
 *
 * <p>It is a code registry rather than a resource one on purpose. The values are codecs, so every side builds the
 * same table from the mods it has loaded and nothing about it has to be read from a pack or sent over the network -
 * which is what lets this mod work with no server at all.
 */
public final class BctResourceKeys {
    /**
     * Entry types, keyed by what a pack writes in a {@code type} field. A code registry rather than a resource one:
     * the values are codecs, so every side builds it from the mods it has loaded.
     */
    public static final ResourceKey<Registry<MapCodec<? extends GroupEntry>>> GROUP_ENTRY_TYPE = create("group_entry_type");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, path));
    }

    private BctResourceKeys() {
    }
}
