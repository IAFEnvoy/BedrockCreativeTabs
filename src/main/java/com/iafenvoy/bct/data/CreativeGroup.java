package com.iafenvoy.bct.data;

import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.util.BctCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record CreativeGroup(List<GroupEntry> entries, Optional<ItemStack> icon,
                            List<ResourceKey<CreativeModeTab>> creativeTabs, int priority) {
    public static final int DEFAULT_PRIORITY = 0;
    public static final Codec<List<ResourceKey<CreativeModeTab>>> TABS_CODEC = ResourceKey.codec(Registries.CREATIVE_MODE_TAB).listOf();
    public static final Codec<CreativeGroup> CODEC = RecordCodecBuilder.<CreativeGroup>create(i -> i.group(
            BctCodecs.singleOrList(GroupEntry.CODEC).fieldOf("entries").forGetter(CreativeGroup::entries),
            ItemStack.CODEC.optionalFieldOf("icon").forGetter(CreativeGroup::icon),
            TABS_CODEC.optionalFieldOf("creative_tabs", List.of()).forGetter(CreativeGroup::creativeTabs),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(CreativeGroup::priority)
    ).apply(i, CreativeGroup::new)).validate(CreativeGroup::validate);

    /**
     * Whether any of this group's entries claims the stack.
     */
    public boolean matches(ItemStack stack) {
        return this.entries.stream().anyMatch(entry -> entry.matches(stack));
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
