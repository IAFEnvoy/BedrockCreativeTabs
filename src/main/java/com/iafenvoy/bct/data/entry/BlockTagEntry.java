package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * One block tag: {@code {"type": "block_tag", "tag": "minecraft:mineable/pickaxe"}}. No shorthand - a bare {@code "#"}
 * is already an item tag. Matches through the stack's item, so an item that places nothing never matches.
 *
 * <p>Use only where the tag exists on the block side alone; prefer {@link ItemTagEntry} where both sides have it.
 */
public record BlockTagEntry(TagKey<Block> tag) implements GroupEntry {
    public static final MapCodec<BlockTagEntry> CODEC = TagKey.codec(Registries.BLOCK).fieldOf("tag").xmap(BlockTagEntry::new, BlockTagEntry::tag);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().is(this.tag);
    }

    @Override
    public String describe() {
        // Names the type: "#" alone would be indistinguishable from an item tag, which is the distinction being checked.
        return "block_tag{#" + this.tag.location() + "}";
    }

    @Override
    public MapCodec<BlockTagEntry> codec() {
        return CODEC;
    }
}
