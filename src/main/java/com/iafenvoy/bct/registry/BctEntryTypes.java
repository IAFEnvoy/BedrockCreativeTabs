package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.data.entry.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The entry types this mod ships. {@code item} and {@code item_tag} are also what the shorthand forms in
 * {@link GroupEntry#SHORTCUT_CODEC} produce; the rest are typed-only.
 */
@SuppressWarnings("unused")
public final class BctEntryTypes {
    public static final DeferredRegister<MapCodec<? extends GroupEntry>> REGISTRY = DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, BedrockCreativeTabs.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<ItemEntry>> ITEM = REGISTRY.register("item", () -> ItemEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<ItemTagEntry>> ITEM_TAG = REGISTRY.register("item_tag", () -> ItemTagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<BlockTagEntry>> BLOCK_TAG = REGISTRY.register("block_tag", () -> BlockTagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<HasComponentEntry>> HAS_COMPONENT = REGISTRY.register("has_component", () -> HasComponentEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<RegexEntry>> REGEX = REGISTRY.register("regex", () -> RegexEntry.CODEC);

    private BctEntryTypes() {
    }
}
