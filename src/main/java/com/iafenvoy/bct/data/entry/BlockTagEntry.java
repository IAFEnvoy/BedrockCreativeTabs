package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * One <b>block</b> tag: {@code {"type": "block_tag", "tag": "minecraft:mineable/pickaxe"}}. There is no shorthand form
 * - a bare {@code "#"} is an item tag - so this type always has to be written out.
 *
 * <p>A block tag names blocks, but a creative group folds item stacks, so the two are bridged through the stack's item:
 * a stack matches when its item places a block, and that block is in the tag. An item that places nothing (a stick, a
 * sword, a bucket) matches no block tag at all, however the tag is written.
 *
 * <p>That bridge is the whole reason the type exists, and it is not the same thing as an item tag. Vanilla keeps the
 * two families separate and they genuinely differ: {@code #minecraft:mineable/pickaxe} has no item counterpart, while
 * {@code #minecraft:doors} exists on both sides and means slightly different things - the block tag is the door blocks,
 * the item tag is the door <em>items</em>. Where a tag exists on both sides, prefer {@link ItemTagEntry}: it asks the
 * question directly and cannot be thrown off by an item whose block form is not what the pack meant.
 */
public record BlockTagEntry(TagKey<Block> tag) implements GroupEntry {
    public static final MapCodec<BlockTagEntry> CODEC = TagKey.codec(Registries.BLOCK).fieldOf("tag")
            .xmap(BlockTagEntry::new, BlockTagEntry::tag);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().is(this.tag);
    }

    @Override
    public String describe() {
        // Names the type, because the "#" form it would otherwise share with an item tag is taken: which of the two
        // families a tag belongs to is the whole difference here, and it is exactly what a reader is checking.
        return "block_tag{#" + this.tag.location() + "}";
    }

    @Override
    public MapCodec<BlockTagEntry> codec() {
        return CODEC;
    }
}
