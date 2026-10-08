package com.iafenvoy.bct.registry;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.data.entry.BlockTagEntry;
import com.iafenvoy.bct.data.entry.HasComponentEntry;
import com.iafenvoy.bct.data.entry.ItemEntry;
import com.iafenvoy.bct.data.entry.ItemTagEntry;
import com.iafenvoy.bct.data.entry.RegexEntry;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The entry types this mod ships. Item and item tag are also what the shorthand forms in
 * {@link GroupEntry#SHORTCUT_CODEC} build, so a pack that never writes a {@code type} field still ends up with those
 * two; the block tag, has component and regex types have no shorthand, because a bare JSON string is already spoken
 * for and a bare {@code "#"} is an item tag.
 */
public final class BctEntryTypes {
    public static final DeferredRegister<MapCodec<? extends GroupEntry>> REGISTRY =
            DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, BedrockCreativeTabs.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<ItemEntry>> ITEM =
            REGISTRY.register("item", () -> ItemEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<ItemTagEntry>> ITEM_TAG =
            REGISTRY.register("item_tag", () -> ItemTagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<BlockTagEntry>> BLOCK_TAG =
            REGISTRY.register("block_tag", () -> BlockTagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<HasComponentEntry>> HAS_COMPONENT =
            REGISTRY.register("has_component", () -> HasComponentEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends GroupEntry>, MapCodec<RegexEntry>> REGEX =
            REGISTRY.register("regex", () -> RegexEntry.CODEC);

    private BctEntryTypes() {
    }
}
