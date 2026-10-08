package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One item tag: {@code {"type": "item_tag", "tag": "minecraft:planks"}}, or the hashed tag as shorthand
 * ({@code "#minecraft:planks"}). The tag is read when the tab is built, so a pack that adds to it sees the new
 * members without touching the definition.
 *
 * <p>This is the type to reach for by default: a creative group folds item stacks, and an item tag names exactly that.
 * The {@code #c:} tags this mod's own definitions prefer are item tags, so they are read by this type.
 */
public record ItemTagEntry(TagKey<Item> tag) implements GroupEntry {
    public static final MapCodec<ItemTagEntry> CODEC = TagKey.codec(Registries.ITEM).fieldOf("tag")
            .xmap(ItemTagEntry::new, ItemTagEntry::tag);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.tag);
    }

    @Override
    public String describe() {
        return "#" + this.tag.location();
    }

    @Override
    public MapCodec<ItemTagEntry> codec() {
        return CODEC;
    }
}
