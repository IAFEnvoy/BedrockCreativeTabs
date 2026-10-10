package com.iafenvoy.bct.data.entry;

import com.iafenvoy.bct.api.GroupEntry;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record HasNbtKeysEntry(List<String> keys) implements GroupEntry {
    public static final Codec<HasNbtKeysEntry> CODEC = Codec.STRING.listOf().fieldOf("keys").codec().xmap(HasNbtKeysEntry::new, HasNbtKeysEntry::keys);

    @Override
    public boolean matches(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && this.keys.stream().allMatch(tag::contains);
    }

    @Override
    public String describe() {
        return "has_nbt_keys[" + String.join(", ", this.keys) + "]";
    }

    @Override
    public Codec<? extends GroupEntry> codec() {
        return CODEC;
    }
}
