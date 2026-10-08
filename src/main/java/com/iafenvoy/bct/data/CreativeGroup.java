package com.iafenvoy.bct.data;

import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.util.BctCodecs;
import com.iafenvoy.bct.util.BctText;
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
 * <p>{@code entries} and {@code creative_tabs} are both required - a group claiming nothing can never be shown, and
 * one naming no tab can never fold. {@code priority} decides which group wins a stack two groups both claim; a tie
 * keeps load order.
 *
 * <p>{@code icon} is an {@link ItemStackTemplate} rather than a stack because definitions are read during the client
 * resource reload, where {@code ItemStack.CODEC} refuses an item whose components are not bound yet.
 *
 * <p>A group carries no text: its name is translated under {@code creative_group.bedrock_creative_tabs.<id>}, derived
 * from the id in {@link BctText}. Text in a definition would be text no pack could translate.
 */
public record CreativeGroup(Identifier id, List<GroupEntry> entries, Optional<ItemStackTemplate> icon,
                            List<ResourceKey<CreativeModeTab>> creativeTabs, int priority) {
    public static final int DEFAULT_PRIORITY = 0;
    public static final Codec<List<ResourceKey<CreativeModeTab>>> TABS_CODEC = ResourceKey.codec(Registries.CREATIVE_MODE_TAB).listOf();
    public static final Codec<CreativeGroup> CODEC = RecordCodecBuilder.<CreativeGroup>create(i -> i.group(
                    BctCodecs.singleOrList(GroupEntry.CODEC).fieldOf("entries").forGetter(CreativeGroup::entries),
                    ItemStackTemplate.CODEC.optionalFieldOf("icon").forGetter(CreativeGroup::icon),
                    TABS_CODEC.optionalFieldOf("creative_tabs", List.of()).forGetter(CreativeGroup::creativeTabs),
                    Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(CreativeGroup::priority)
            ).apply(i, (entries, icon, creativeTabs, priority) ->
                    new CreativeGroup(BctCodecs.UNKNOWN_ID, entries, icon, creativeTabs, priority)))
            .validate(CreativeGroup::validate);

    /**
     * Whether any of this group's entries claims the stack.
     */
    public boolean matches(ItemStack stack) {
        return this.entries.stream().anyMatch(entry -> entry.matches(stack));
    }

    /**
     * The same group under the id it was read from. The codec cannot know it - a definition does not write its own id,
     * only the file name carries it - so the loader stamps it on after decoding.
     */
    public CreativeGroup withId(Identifier id) {
        return new CreativeGroup(id, this.entries, this.icon, this.creativeTabs, this.priority);
    }

    private static DataResult<CreativeGroup> validate(CreativeGroup group) {
        if (group.entries().isEmpty())
            return DataResult.error(() -> "bedrock_creative_tabs:creative_group needs at least one entry");
        if (group.creativeTabs().isEmpty())
            return DataResult.error(() -> "bedrock_creative_tabs:creative_group needs at least one tab in creative_tabs");
        // Search is every tab's search list, not a tab of its own: folding it would take entries out of search itself.
        return group.creativeTabs().contains(CreativeModeTabs.SEARCH)
                ? DataResult.error(() -> "bedrock_creative_tabs:creative_group cannot fold minecraft:search")
                : DataResult.success(group);
    }
}
