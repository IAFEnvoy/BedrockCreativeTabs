package com.iafenvoy.bct.api;

import com.iafenvoy.bct.data.entry.ItemEntry;
import com.iafenvoy.bct.data.entry.ItemTagEntry;
import com.iafenvoy.bct.registry.BctRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * One way of saying "this stack belongs to the group": a creative group folds every stack any one of its entries
 * accepts, so an entry answers exactly one question and nothing about what a group means.
 *
 * <p>This mod ships five of them - {@code bedrock_creative_tabs:item} (by item id, field {@code id}),
 * {@code bedrock_creative_tabs:item_tag} (by item tag, field {@code tag}), {@code bedrock_creative_tabs:block_tag}
 * (by block tag, same field), {@code bedrock_creative_tabs:has_component} (by a component the stack carries, field
 * {@code component}) and {@code bedrock_creative_tabs:regex}. The first two also have a shorthand form a pack can
 * write instead of the typed object: a bare item id ({@code "minecraft:oak_planks"}) or a hashed item tag
 * ({@code "#minecraft:planks"}). A block tag has none, because a bare {@code "#"} already means an item tag.
 *
 * <p><b>Adding a type.</b> An entry type is a {@link MapCodec} registered under
 * {@code bedrock_creative_tabs:group_entry_type}, which any mod can do with its own
 * {@code DeferredRegister.create(BctRegistries.GROUP_ENTRY_TYPE, "yourmod")}; the id it is registered as is what a
 * pack writes in the {@code type} field, and that id is namespaced by the registering mod - this mod's own are
 * {@code bedrock_creative_tabs:item} and {@code bedrock_creative_tabs:item_tag}, not bare {@code item} and
 * {@code item_tag}. Registrations are per mod and first-wins, so two mods cannot claim one id.
 *
 * <p>The registry is a <b>code</b> registry rather than a resource one, and that is what makes it work with no server:
 * every side builds it from the mods it has loaded, so the set of known types needs nothing synced. Because a
 * definition is now read from the client's own resource pack, only the decode direction is ever used - a definition
 * is never handed anywhere that would need it re-encoded.
 *
 * <p>An entry naming a type nobody provides is dropped with a log line, which is why a pack that ships groups for an
 * optional mod still loads.
 */
public interface GroupEntry {
    /**
     * The typed form, dispatched on the {@code type} field.
     *
     * <p>The {@code type} value is an {@link net.minecraft.resources.Identifier}, so a bare name resolves against
     * {@code minecraft}: a pack must write {@code "bedrock_creative_tabs:item_tag"}, not {@code "item_tag"}, or the
     * lookup asks for {@code minecraft:item_tag} and fails with "Unknown registry key". An unknown type is only ever a
     * dropped entry with a log line, so this shows up as a group parsing to nothing rather than as a crash.
     *
     * <p>Resolved per call rather than captured once, because the type registry is created empty by
     * {@code NewRegistryEvent} and filled in later by {@code RegisterEvent}; a codec built or cached before that would
     * read an empty registry. The cost is one lookup per decode, and decoding happens on resource reloads.
     */
    Codec<GroupEntry> TYPED_CODEC = new Codec<>() {
        private Codec<GroupEntry> current() {
            return BctRegistries.GROUP_ENTRY_TYPE.byNameCodec()
                    .dispatch("type", GroupEntry::codec, Function.identity());
        }

        @Override
        public <T> DataResult<Pair<GroupEntry, T>> decode(DynamicOps<T> ops, T input) {
            return this.current().decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(GroupEntry input, DynamicOps<T> ops, T prefix) {
            return this.current().encode(input, ops, prefix);
        }
    };
    /**
     * The shorthand the two item-side built-ins accept: a bare item id, or a hashed item tag.
     */
    Codec<GroupEntry> SHORTCUT_CODEC = Codec.either(BuiltInRegistries.ITEM.byNameCodec(), TagKey.hashedCodec(Registries.ITEM)).xmap(
            either -> either.map(ItemEntry::new, ItemTagEntry::new),
            entry -> switch (entry) {
                case ItemEntry(Item item) -> Either.left(item);
                case ItemTagEntry(TagKey<Item> tag) -> Either.right(tag);
                default -> throw new IllegalArgumentException("Only the item and item tag entries have a shorthand form");
            });
    /**
     * What an {@code entries} array holds: either form, tried in that order. A JSON object cannot read as a string,
     * so the shorthand never shadows the typed form.
     */
    Codec<GroupEntry> CODEC = Codec.either(SHORTCUT_CODEC, TYPED_CODEC).xmap(either -> either.map(Function.identity(), Function.identity()), Either::right);

    /**
     * Whether this entry claims the stack. Called while a tab is built, once per entry per stack on the tab.
     */
    boolean matches(ItemStack stack);

    /**
     * This entry written back as text, for a log line to name the entry it is talking about. Not JSON - it is one
     * token rather than a document, so it is written the way a pack is most likely to have written it: the shorthand
     * where there is one, and otherwise the type's own field ({@code block_tag{c:natural_logs}}).
     *
     * <p>Deliberately not derived from the codec. What a pack wrote and what this reads back can differ - a bare
     * {@code "minecraft:oak_planks"} and a written-out {@code {"type": "item", ...}} both decode to an
     * {@link ItemEntry} - and the shorthand is the shorter and more common of the two, so it is what a reader wants to
     * see. The codec only ever runs here in the decode direction anyway.
     */
    String describe();

    /**
     * This entry's own codec, which is what the type registry stores and what the {@code type} field dispatches on.
     */
    MapCodec<? extends GroupEntry> codec();
}
