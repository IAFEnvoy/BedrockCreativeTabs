package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;

/**
 * The registry keys this mod owns.
 */
public final class BctResourceKeys {
    /**
     * Entry types, keyed by what a pack writes in a {@code type} field. A code registry rather than a resource one:
     * the values are codecs, so every side builds the same table from its own mods and nothing has to be synced.
     */
    public static final ResourceKey<Registry<MapCodec<? extends GroupEntry>>> GROUP_ENTRY_TYPE = create("group_entry_type");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(BedrockCreativeTabs.MOD_ID, path));
    }

    private BctResourceKeys() {
    }
}
