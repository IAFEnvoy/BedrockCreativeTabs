package com.iafenvoy.bct.api;

import com.iafenvoy.bct.data.entry.ItemEntry;
import com.iafenvoy.bct.data.entry.ItemTagEntry;
import com.iafenvoy.bct.registry.BctRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Function;

/**
 * One way of saying "this stack belongs to the group". A group folds every stack any of its entries accepts.
 *
 * <p>Five types ship: {@code item} (field {@code id}), {@code item_tag} (field {@code tag}), {@code block_tag} (field
 * {@code tag}), {@code has_nbt_keys} (field {@code keys}) and {@code regex} (field {@code pattern}). The first
 * two also accept a shorthand - a bare item id, or a {@code #}-prefixed item tag - so a pack need not write an object
 * for the common cases.
 *
 * <p><b>Adding a type:</b> register a {@link MapCodec} under {@code bedrock_creative_tabs:group_entry_type}. The
 * registered id is what a pack writes in {@code type}, and it is namespaced by the registering mod - so
 * {@code "bedrock_creative_tabs:item_tag"}, never bare {@code "item_tag"}. Registrations are first-wins per id, and
 * an entry naming a type nobody provides is dropped with a log line rather than failing the file.
 */
public interface GroupEntry {
    /**
     * The typed form, dispatched on the {@code type} field.
     *
     * <p>The registry is read on every call rather than captured: it does not exist yet when this interface is
     * initialised, so a codec built from it eagerly would dispatch against nothing.
     */
    Codec<GroupEntry> TYPED_CODEC = BctRegistries.groupEntryTypes().getCodec().dispatch("type", GroupEntry::codec, Function.identity());
    /**
     * The shorthand the two item-side built-ins accept: a bare item id, or a hashed item tag.
     */
    Codec<GroupEntry> SHORTCUT_CODEC = Codec.either(ForgeRegistries.ITEMS.getCodec(), TagKey.hashedCodec(Registries.ITEM)).xmap(
            either -> either.map(ItemEntry::new, ItemTagEntry::new),
            entry -> {
                if (entry instanceof ItemEntry itemEntry) return Either.left(itemEntry.item());
                if (entry instanceof ItemTagEntry itemTagEntry) return Either.right(itemTagEntry.tag());
                throw new IllegalArgumentException("Only the item and item tag entries have a shorthand form");
            });
    /**
     * Either form. A JSON object cannot read as a string, so the shorthand never shadows the typed form.
     */
    Codec<GroupEntry> CODEC = Codec.either(SHORTCUT_CODEC, TYPED_CODEC).xmap(either -> either.map(Function.identity(), Function.identity()), Either::right);

    /**
     * Whether this entry claims the stack.
     */
    boolean matches(ItemStack stack);

    /**
     * This entry as one token for a log line, in the shorthand a pack would have written where there is one
     * ({@code "#minecraft:planks"}), and otherwise as the type and its field ({@code block_tag{#c:natural_logs}}).
     */
    String describe();

    /**
     * This entry's own codec, which is what the {@code type} field dispatches on.
     */
    Codec<? extends GroupEntry> codec();
}
