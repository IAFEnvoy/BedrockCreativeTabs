package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.data.entry.*;
import com.mojang.serialization.Codec;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The entry types this mod ships. {@code item} and {@code item_tag} are also what the shorthand forms in
 * {@link GroupEntry#SHORTCUT_CODEC} produce; the rest are typed-only.
 */
@SuppressWarnings("unused")
public final class BctEntryTypes {
    public static final DeferredRegister<Codec<? extends GroupEntry>> REGISTRY = DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, BedrockCreativeTabs.MOD_ID);

    public static final RegistryObject<Codec<ItemEntry>> ITEM = REGISTRY.register("item", () -> ItemEntry.CODEC);
    public static final RegistryObject<Codec<ItemTagEntry>> ITEM_TAG = REGISTRY.register("item_tag", () -> ItemTagEntry.CODEC);
    public static final RegistryObject<Codec<BlockTagEntry>> BLOCK_TAG = REGISTRY.register("block_tag", () -> BlockTagEntry.CODEC);
    public static final RegistryObject<Codec<HasNbtKeysEntry>> HAS_NBT_KEYS = REGISTRY.register("has_nbt_keys", () -> HasNbtKeysEntry.CODEC);
    public static final RegistryObject<Codec<RegexEntry>> REGEX = REGISTRY.register("regex", () -> RegexEntry.CODEC);

    private BctEntryTypes() {
    }
}
