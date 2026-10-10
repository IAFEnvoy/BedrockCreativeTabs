package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * One item, by id: {@code {"type": "item", "id": "minecraft:oak_planks"}} or the bare id as shorthand. Claims every
 * stack of it, whatever components it carries.
 */
public record ItemEntry(Item item) implements GroupEntry {
    public static final Codec<ItemEntry> CODEC = ForgeRegistries.ITEMS.getCodec().xmap(ItemEntry::new, ItemEntry::item).fieldOf("id").codec();

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.item);
    }

    @Override
    public String describe() {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(this.item)).toString();
    }

    @Override
    public Codec<ItemEntry> codec() {
        return CODEC;
    }
}
