package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.BedrockCreativeTabs;
import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.data.BctGroupStore;
import com.iafenvoy.bct.data.CreativeGroup;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.data.entry.ItemEntry;
import com.iafenvoy.bct.data.entry.ItemTagEntry;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Turns the shared selection ({@link BctEditorState#CHOSEN}) into a saved, folded group - the one operation every
 * surface (the vanilla picker's G key, the AUI panel's button) funnels into. No AUI imports and no UI of its own:
 * the caller renders the returned message wherever it lives.
 *
 * <p>The inference is the editor's whole point: the entries collapse to the one item tag that claims exactly the
 * chosen items (falling back to enumerating ids), the tabs are the union of pages the items actually appear on,
 * and the priority is planned so the new group cannot tie with one it overlaps.
 */
public final class BctGroupCreator {
    private BctGroupCreator() {
    }

    /**
     * The whole loop in one call; the returned line is fit for an action bar or a panel.
     */
    public static Component createFromSelection() {
        Set<String> chosen = BctEditorState.CHOSEN;
        if (chosen.isEmpty()) return Component.translatable("editor.bedrock_creative_tabs.create_empty");
        Map<String, ItemStack> universe = universeById();

        // The members first, so the name can be derived from the very items being folded.
        List<ItemStack> members = new ArrayList<>();
        for (String id : chosen) {
            ItemStack stack = universe.get(id);
            if (stack != null && !stack.isEmpty()) members.add(stack);
        }

        List<GroupEntry> entries = new ArrayList<>();
        String tag = inferExactTag(universe, chosen);
        BctNames.Named name;
        if (tag != null) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag));
            entries.add(new ItemTagEntry(key));
            name = BctNames.fromTag(key.location().getPath(), members);
        } else {
            for (String id : new TreeSet<>(chosen)) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                entries.add(new ItemEntry(item));
            }
            name = BctNames.fromMembers(members);
        }

        List<ResourceKey<CreativeModeTab>> tabs = tabsContaining(universe, chosen);
        if (tabs.isEmpty()) return Component.translatable("editor.bedrock_creative_tabs.create_no_tabs");

        ResourceLocation id = uniqueId(name.path(), entries);
        int priority = planPriority(id, entries, tabs);
        CreativeGroup group = new CreativeGroup(entries, Optional.empty(), tabs, priority);

        // Name and id travel separately: the id stays unique (has to), the display name stays readable.
        BctGroupStore.save(id, group, name.nameZh(), name.nameEn());
        CreativeGroupLoader.putUser(id, group);
        CreativeGroupService.onGroupsReloaded();
        // The selection is consumed by the create: leaving it behind would silently fold into the next group's
        // name and membership, which is exactly how a key group ended up named after the upgrades selected before it.
        BctEditorState.CHOSEN.clear();

        // One tab being outranked does not mean all of them are; report the tab where the fold actually landed.
        int folded = 0;
        ResourceKey<CreativeModeTab> landed = null;
        for (ResourceKey<CreativeModeTab> tab : tabs) {
            int count = CreativeGroupService.members(tab, id).size();
            if (count > folded) {
                folded = count;
            }
        }
        BedrockCreativeTabs.LOGGER.info("BCT editor: created {} priority={} tabs={} folded={} name={} / {}",
                id, priority, tabs, folded, name.nameZh(), name.nameEn());
        String label = name.nameZh().equals(name.nameEn()) ? name.nameZh() : name.nameZh() + " / " + name.nameEn();
        if (folded > 0)
            return Component.translatable("editor.bedrock_creative_tabs.created", label, folded);
        return Component.translatable("editor.bedrock_creative_tabs.created_conflict",
                label, winner(id, tabs.getFirst(), entries));
    }

    private static Map<String, ItemStack> universeById() {
        LinkedHashMap<String, ItemStack> universe = new LinkedHashMap<>();
        for (ItemStack stack : CreativeGroupService.universe()) {
            if (stack.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            universe.putIfAbsent(key.toString(), stack);
        }
        return universe;
    }

    /**
     * The first id free for this group: the tag's own path when the entries converged to one, else the caller's
     * slug. An id already held by a <em>different</em> group gets a numeric suffix - two selections converging onto
     * tags with the same path but different namespaces would otherwise silently overwrite each other.
     */
    public static ResourceLocation uniqueId(String path, List<GroupEntry> entries) {
        ResourceLocation id = BctGroupStore.id(null, path);
        for (int suffix = 2; occupiedByDifferent(id, entries); suffix++)
            id = BctGroupStore.id(null, path + "-" + suffix);
        return id;
    }

    private static boolean occupiedByDifferent(ResourceLocation id, List<GroupEntry> entries) {
        CreativeGroup existing = CreativeGroupLoader.groups().get(id);
        return existing != null && !existing.entries().equals(entries);
    }

    /**
     * A priority that cannot tie with any group this one overlaps: the more specific group (fewer members) wins,
     * and equally specific counts as ours - the player just asked for this group, and the only alternative is a
     * tie, which the loader resolves by hash order, i.e. not at all. Also what the AUI panel's draft prefill shows.
     */
    public static int planPriority(ResourceLocation id, List<GroupEntry> entries, List<ResourceKey<CreativeModeTab>> tabs) {
        List<ItemStack> mine = membersOf(entries);
        List<Integer> theirSizes = new ArrayList<>();
        int highest = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<ResourceLocation, CreativeGroup> candidate : CreativeGroupLoader.groups().entrySet()) {
            // Re-creating a group must not treat the previous version of itself as a rival: that would raise its
            // priority by one on every attempt, for no reason.
            if (candidate.getKey().equals(id)) continue;
            CreativeGroup other = candidate.getValue();
            if (other.creativeTabs().stream().noneMatch(tabs::contains)) continue;
            List<ItemStack> theirs = membersOf(other.entries());
            boolean overlaps = mine.stream().anyMatch(theirs::contains);
            if (!overlaps) continue;
            theirSizes.add(theirs.size());
            highest = Math.max(highest, other.priority());
            lowest = Math.min(lowest, other.priority());
        }
        if (theirSizes.isEmpty()) return 0;
        boolean wins = true;
        for (int size : theirSizes)
            if (mine.size() > size) {
                wins = false;
                break;
            }
        return wins ? highest + 1 : lowest - 1;
    }

    /**
     * Which other group on this tab already claims something this one wanted, for a readable failure.
     */
    private static Component winner(ResourceLocation mine, ResourceKey<CreativeModeTab> tab, List<GroupEntry> entries) {
        List<ItemStack> wanted = membersOf(entries);
        for (Map.Entry<ResourceLocation, CreativeGroup> entry : CreativeGroupLoader.groups().entrySet()) {
            if (entry.getKey().equals(mine)) continue;
            List<ItemStack> theirs = CreativeGroupService.members(tab, entry.getKey());
            if (theirs.stream().anyMatch(wanted::contains)) return Component.literal(entry.getKey().toString());
        }
        return Component.translatable("editor.bedrock_creative_tabs.another_group");
    }

    private static List<ItemStack> membersOf(List<GroupEntry> entries) {
        List<ItemStack> members = new ArrayList<>();
        for (ItemStack stack : CreativeGroupService.universe()) {
            for (GroupEntry entry : entries) {
                if (entry.matches(stack)) {
                    members.add(stack);
                    break;
                }
            }
        }
        return members;
    }

    /**
     * The one item tag that claims exactly the chosen items and nothing else in the universe, or {@code null}.
     * Only tags shared by every chosen item are considered, so a tag cannot be picked that misses one of them.
     */
    private static String inferExactTag(Map<String, ItemStack> universe, Set<String> chosen) {
        Set<TagKey<Item>> candidates = null;
        for (String id : chosen) {
            ItemStack stack = universe.get(id);
            if (stack == null) return null;
            Set<TagKey<Item>> tags = new HashSet<>();
            BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags().forEach(tags::add);
            if (candidates == null) candidates = tags;
            else candidates.retainAll(tags);
            if (candidates.isEmpty()) return null;
        }
        if (candidates == null) return null;
        for (TagKey<Item> tag : candidates) {
            boolean extra = false;
            for (Map.Entry<String, ItemStack> entry : universe.entrySet()) {
                if (!chosen.contains(entry.getKey()) && entry.getValue().is(tag)) {
                    extra = true;
                    break;
                }
            }
            if (!extra) return tag.location().toString();
        }
        return null;
    }

    /**
     * Every tab that holds at least one of the chosen items.
     */
    private static List<ResourceKey<CreativeModeTab>> tabsContaining(Map<String, ItemStack> universe, Set<String> chosen) {
        List<ResourceKey<CreativeModeTab>> tabs = new ArrayList<>();
        for (ResourceKey<CreativeModeTab> key : CreativeGroupService.builtTabs()) {
            for (ItemStack stack : CreativeGroupService.universe(key)) {
                if (stack.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (chosen.contains(id.toString())) {
                    tabs.add(key);
                    break;
                }
            }
        }
        return tabs;
    }
}
