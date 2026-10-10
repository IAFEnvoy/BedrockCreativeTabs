package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.data.CreativeGroup;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.registry.BctItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.*;

/**
 * The creative tabs' groups: which stacks a definition folds into one icon, and what that icon does when clicked.
 * This is the mod's public runtime - another mod asking what is in a group, whether it is open, or to open it needs
 * nothing else.
 *
 * <p>Client only, because a server never builds a creative tab. Two platform constraints shape everything here: a
 * tab's entry set compares item and components only, so icons must differ by a component and members must actually
 * leave the list rather than be skipped while drawing; and the search page reads each tab's <em>search</em> list, so
 * members are removed from the <em>parent</em> list alone - which is the whole reason they stay searchable.
 *
 * <p>Everything is per tab, so the same stack can be folded on one tab and left alone on another. The fold is the
 * last listener on the build event, so it sees what every other contributor put in.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CreativeGroupService {
    // Tab -> group id -> the stacks that tab's fold took off it, in tab order. Rebuilt whenever that tab is built, so
    // nothing in here can outlive the contents it describes; a group whose definition went away drops out with them.
    private static final Map<ResourceKey<CreativeModeTab>, Map<ResourceLocation, List<ItemStack>>> MEMBERS = new LinkedHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        // The search tab is every tab's search list rather than a tab of its own, so folding it would take entries out
        // of search itself. A definition cannot name it (see the codec), so this is the belt to that pair of braces.
        if (event.getTabKey().equals(CreativeModeTabs.SEARCH)) return;
        fold(event);
    }

    private static void fold(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tabKey = event.getTabKey();
        MEMBERS.put(tabKey, new LinkedHashMap<>());

        // Must run before anything is taken out, and against the contents as the game built them.
        CreativeGroupLoader.checkAgainst(tabKey, event.getParentEntries());

        // Filter by tab before picking a winner, not after: a group from another tab would otherwise win on priority,
        // then be refused here, and the stack would be dropped rather than folded into the group that could take it.
        List<Map.Entry<ResourceLocation, CreativeGroup>> candidates = CreativeGroupLoader.groups().entrySet().stream()
                .filter(group -> group.getValue().creativeTabs().contains(tabKey))
                .toList();
        Map<ResourceLocation, CreativeGroup> winners = new LinkedHashMap<>();
        Map<ResourceLocation, List<ItemStack>> folded = new LinkedHashMap<>();
        for (ItemStack stack : event.getParentEntries()) {
            // A rebuild must not fold the icon a previous build wrote.
            if (stack.is(BctItems.GROUP.get())) continue;
            Optional<Map.Entry<ResourceLocation, CreativeGroup>> found = candidates.stream()
                    .filter(group -> group.getValue().matches(stack)).min(CreativeGroupLoader.ORDER);
            if (found.isEmpty()) continue;
            ResourceLocation id = found.get().getKey();
            winners.putIfAbsent(id, found.get().getValue());
            folded.computeIfAbsent(id, key -> new ArrayList<>()).add(stack);
        }

        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : folded.entrySet()) {
            List<ItemStack> members = entry.getValue();
            // Insert before removing: moving an entry refuses a target that is already gone.
            event.insertBefore(members.getFirst(), icon(tabKey, entry.getKey(), winners.get(entry.getKey())), TabVisibility.PARENT_TAB_ONLY);
            for (ItemStack member : members) event.remove(member, TabVisibility.PARENT_TAB_ONLY);
            MEMBERS.get(tabKey).put(entry.getKey(), List.copyOf(members));
        }
    }

    /**
     * The icon a group is shown as on one tab. The slot lock is the platform's own "not a thing you can take": every
     * click path in the creative screen asks {@code Slot#mayPickup} first, so the icon cannot be picked up even when
     * nothing here runs.
     */
    public static ItemStack icon(ResourceKey<CreativeModeTab> tab, ResourceLocation id, CreativeGroup group) {
        ItemStack icon = new ItemStack(BctItems.GROUP.get());
        icon.set(BctDataComponents.CREATIVE_GROUP, group);
        icon.set(BctDataComponents.CREATIVE_GROUP_ID, id);
        icon.set(BctDataComponents.CREATIVE_GROUP_TAB, tab);
        icon.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        icon.set(DataComponents.ITEM_NAME, Component.translatable(id.toLanguageKey("creative_group")));
        return icon;
    }

    /**
     * Opens or closes one group in place, returning whether anything changed. The list is a screen's own copy, not
     * the tab's - expanding is a property of the visit, so a page always opens folded.
     */
    public static boolean toggle(Collection<ItemStack> display, ResourceKey<CreativeModeTab> tab,
                                 ResourceLocation groupId, List<ItemStack> members) {
        if (members.isEmpty()) return false;
        Set<ItemStack> folded = Collections.newSetFromMap(new IdentityHashMap<>());
        folded.addAll(members);
        if (display.containsAll(members)) {
            display.removeIf(folded::contains);
            return true;
        }
        List<ItemStack> rebuilt = new ArrayList<>(display.size() + members.size());
        boolean opened = false;
        for (ItemStack stack : display) {
            rebuilt.add(stack);
            if (!opened && isIconOf(stack, tab, groupId)) {
                rebuilt.addAll(members);
                opened = true;
            }
        }
        // No icon on this list means a group from another tab, or one the list never had.
        if (!opened) return false;
        display.clear();
        display.addAll(rebuilt);
        return true;
    }

    /**
     * What a group is drawn as on the tab its icon carries: its written icon, or the first member the fold found.
     */
    public static ItemStack displayIcon(ItemStack icon) {
        CreativeGroup group = icon.get(BctDataComponents.CREATIVE_GROUP);
        ResourceLocation id = icon.get(BctDataComponents.CREATIVE_GROUP_ID);
        if (group == null || id == null) return ItemStack.EMPTY;
        ItemStack written = group.icon().orElse(ItemStack.EMPTY);
        if (!written.isEmpty()) return written;
        List<ItemStack> members = members(icon.get(BctDataComponents.CREATIVE_GROUP_TAB), id);
        return members.isEmpty() ? ItemStack.EMPTY : members.getFirst();
    }

    /**
     * The stacks one tab's fold took off it for one group, in tab order. Empty for a tab not built since the reload.
     */
    public static List<ItemStack> members(ResourceKey<CreativeModeTab> tab, ResourceLocation groupId) {
        if (tab == null || groupId == null) return List.of();
        return MEMBERS.getOrDefault(tab, Map.of()).getOrDefault(groupId, List.of());
    }

    /**
     * Whether the group an icon stands for is open in the list a screen is showing.
     */
    public static boolean isOpen(List<ItemStack> view, ItemStack icon) {
        ResourceLocation id = icon.get(BctDataComponents.CREATIVE_GROUP_ID);
        ResourceKey<CreativeModeTab> tab = icon.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (id == null || tab == null) return false;
        List<ItemStack> members = members(tab, id);
        return !members.isEmpty() && new HashSet<>(view).containsAll(members);
    }

    /**
     * Whether a stack is the icon of one group on one tab, which is how a click finds the entry to open next to.
     */
    public static boolean isIconOf(ItemStack stack, ResourceKey<CreativeModeTab> tab, ResourceLocation groupId) {
        return stack.is(BctItems.GROUP.get())
                && tab.equals(stack.get(BctDataComponents.CREATIVE_GROUP_TAB))
                && groupId.equals(stack.get(BctDataComponents.CREATIVE_GROUP_ID));
    }

    /**
     * Drops the member tables and rebuilds every tab. A member list only means anything next to the contents it came
     * from, and a cached tab list will not fold a new definition until it is built again.
     */
    public static void onGroupsReloaded() {
        MEMBERS.clear();
        CreativeTabRebuild.rebuild();
    }

    private CreativeGroupService() {
    }
}
