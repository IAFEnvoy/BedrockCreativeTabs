package com.iafenvoy.bct.data;

import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.util.BctCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * One group: the stacks its entries claim, folded into one icon that opens in place on each tab it names.
 *
 * <p>A group folds only where it says it does, so the same stack can be folded on one tab and left alone on another.
 * {@code entries} and {@code creative_tabs} both have to say something - a group that claims nothing can never be
 * shown, and one that names no tab can never fold - and no group may name {@code minecraft:search}, which is not a
 * tab of its own but every other tab's search list.
 *
 * <p>{@code icon} is what the closed icon draws and falls back to the first member the fold found there. It is an
 * {@link ItemStackTemplate} rather than a stack because a definition is read while the client resource reload runs,
 * where {@code ItemStack.CODEC} fails outright: {@code Item.CODEC_WITH_BOUND_COMPONENTS} refuses an item whose
 * components are not bound yet. A template is built into a stack when it is drawn.
 *
 * <p>{@code priority} decides which group claims a stack two groups both claim: the highest wins, and a tie falls
 * back to load order.
 *
 * <p>A group carries no text of its own: its name is translated under {@code creative_group.bedrock_creative_tabs.<id>},
 * derived from the id in {@link com.iafenvoy.bct.util.BctText}. Text in a definition would be text no pack could
 * translate, and the file name is the one thing a group is already guaranteed to have.
 *
 * <p>A group carries the {@link #id} it was read from. It used to be held as a registry {@code Holder} that answered
 * the id; a group now comes from a client resource pack rather than a synced registry, so it carries its own id and
 * nothing downstream needs a registry to name it.
 */
public record CreativeGroup(Identifier id, List<GroupEntry> entries, Optional<ItemStackTemplate> icon,
                            List<ResourceKey<CreativeModeTab>> creativeTabs, int priority) {
    public static final int DEFAULT_PRIORITY = 0;
    public static final Codec<List<ResourceKey<CreativeModeTab>>> TABS_CODEC =
            ResourceKey.codec(Registries.CREATIVE_MODE_TAB).listOf();
    public static final Codec<CreativeGroup> CODEC = RecordCodecBuilder.<CreativeGroup>create(i -> i.group(
            BctCodecs.singleOrList(GroupEntry.CODEC).fieldOf("entries").forGetter(CreativeGroup::entries),
            ItemStackTemplate.CODEC.optionalFieldOf("icon").forGetter(CreativeGroup::icon),
            TABS_CODEC.optionalFieldOf("creative_tabs", List.of()).forGetter(CreativeGroup::creativeTabs),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(CreativeGroup::priority)
    ).apply(i, (entries, icon, creativeTabs, priority) ->
            new CreativeGroup(BctCodecs.UNKNOWN_ID, entries, icon, creativeTabs, priority)))
            .validate(CreativeGroup::validate);

    /**
     * Whether this group claims the stack, which is what the fold asks of every group it reads.
     */
    public boolean matches(ItemStack stack) {
        return this.entries.stream().anyMatch(entry -> entry.matches(stack));
    }

    /**
     * The same group under the id it was read from. The codec cannot know it - a definition does not write its own id;
     * the loader knows it from the file name - so the loader stamps it on after decoding.
     */
    public CreativeGroup withId(Identifier id) {
        return new CreativeGroup(id, this.entries, this.icon, this.creativeTabs, this.priority);
    }

    private static DataResult<CreativeGroup> validate(CreativeGroup group) {
        if (group.entries().isEmpty())
            return DataResult.error(() -> "bedrock_creative_tabs:creative_group needs at least one entry");
        if (group.creativeTabs().isEmpty())
            return DataResult.error(() -> "bedrock_creative_tabs:creative_group needs at least one tab in creative_tabs");
        // The search tab is not a tab of its own but every other tab's search list: folding it would take entries out
        // of search itself, which is the one thing the fold must never do.
        return group.creativeTabs().contains(CreativeModeTabs.SEARCH)
                ? DataResult.error(() -> "bedrock_creative_tabs:creative_group cannot fold minecraft:search")
                : DataResult.success(group);
    }
}
