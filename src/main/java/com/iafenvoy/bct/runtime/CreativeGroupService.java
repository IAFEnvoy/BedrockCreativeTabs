package com.iafenvoy.bct.runtime;

import com.iafenvoy.bct.data.CreativeGroup;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.registry.BctDataComponents;
import com.iafenvoy.bct.registry.BctItems;
import com.iafenvoy.bct.util.BctText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * The creative tabs' groups: which entries a definition folds into one icon, and what that icon does when it is
 * clicked. This is the mod's public runtime, so another mod asking "what is inside this group", "is it open" or
 * "open it" needs nothing else.
 *
 * <p><b>This is a client mod.</b> A server never builds a creative tab - the only call path into
 * {@code CreativeModeTab#buildContents} runs from the creative screen - so a server has nothing to fold and nothing to
 * say about folding. Everything here is therefore client only: the definitions come from the client's own resource
 * packs (see {@link CreativeGroupLoader}), the tab lists are the client's, and no part of this reads or writes
 * anything the server owns. Nothing needs syncing, and nothing breaks when the server has never heard of this mod.
 *
 * <p>Two hard constraints decide the shape of everything here. First, a tab's own entry set compares item and
 * components only, so two groups' icons must differ by a component and the members must leave the tab's list rather
 * than merely be skipped while drawing. Second, the search page collects each tab's <em>search</em> list, so the
 * members are removed from the parent list alone: that is the whole reason they stay searchable, and why nothing here
 * ever touches the search page.
 *
 * <p>Everything is per tab. A definition names the tabs it folds in, so the same stack can be folded on one tab and
 * left alone on another, and a tab's members are remembered against that tab - the icon carries the tab it was
 * written for, which is what makes a click open the right list.
 *
 * <p>The fold is the last listener on the build event, so it sees what every other contributor put in.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CreativeGroupService {
    // Tab -> group id -> the stacks that tab's fold took off it, in tab order. Rebuilt whenever that tab is built, so
    // nothing in here can outlive the contents it describes; a group whose definition went away drops out with them.
    private static final Map<ResourceKey<CreativeModeTab>, Map<Identifier, List<ItemStack>>> MEMBERS = new LinkedHashMap<>();

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

        // Before anything is folded, and against the contents as the game built them: an entry that matches nothing
        // here matched nothing, and the fold is about to take the matches out from under it. Once per reload, so it
        // happens on whichever tab is built first.
        CreativeGroupLoader.checkAgainst(tabKey, event.getParentEntries());

        // Only the groups that fold on this tab are candidates. Filtering before the winner is picked, rather than
        // after, is what keeps a group belonging to another tab from shadowing one that belongs to this one: the two
        // would otherwise compete on priority alone, the winner would then be refused for being on the wrong tab, and
        // the stack would be dropped instead of folded into the group that could actually take it.
        List<CreativeGroup> candidates = CreativeGroupLoader.groups()
                .filter(group -> group.creativeTabs().contains(tabKey))
                .toList();
        Map<Identifier, CreativeGroup> groups = new LinkedHashMap<>();
        Map<Identifier, List<ItemStack>> folded = new LinkedHashMap<>();
        for (ItemStack stack : List.copyOf(event.getParentEntries())) {
            // An icon is never a member: a rebuild must not fold the icon a previous build wrote.
            if (stack.is(BctItems.GROUP.get())) continue;
            Optional<CreativeGroup> found = groupOf(candidates, stack);
            if (found.isEmpty()) continue;
            Identifier id = found.get().id();
            groups.putIfAbsent(id, found.get());
            folded.computeIfAbsent(id, key -> new ArrayList<>()).add(stack);
        }

        for (Map.Entry<Identifier, List<ItemStack>> entry : folded.entrySet()) {
            List<ItemStack> members = entry.getValue();
            // The icon is inserted before the members are taken out: moving an entry refuses a target that is gone.
            event.insertBefore(members.getFirst(), icon(tabKey, groups.get(entry.getKey())), TabVisibility.PARENT_TAB_ONLY);
            Set<ItemStack> removing = Collections.newSetFromMap(new IdentityHashMap<>());
            removing.addAll(members);
            event.removeIf(removing::contains, TabVisibility.PARENT_TAB_ONLY);
            MEMBERS.get(tabKey).put(entry.getKey(), List.copyOf(members));
        }
    }

    /**
     * The group a stack belongs to, or empty when none claims it. Only used by the fold; anything that already holds
     * an icon reads the group off it instead.
     */
    public static Optional<CreativeGroup> groupOf(ItemStack stack) {
        return CreativeGroupLoader.groupOf(stack);
    }

    private static Optional<CreativeGroup> groupOf(List<CreativeGroup> groups, ItemStack stack) {
        return groups.stream().filter(group -> group.matches(stack)).min(CreativeGroupLoader.ORDER);
    }

    /**
     * The icon a group is shown as on one tab. The lock is the platform's own answer to "this entry is not a thing you
     * can take": every click path in the creative screen asks {@code Slot#mayPickup} first, and the quick-craft drag
     * asks it too, so the icon cannot be picked up even when nothing here runs.
     */
    public static ItemStack icon(ResourceKey<CreativeModeTab> tab, CreativeGroup group) {
        ItemStack icon = new ItemStack(BctItems.GROUP.get());
        icon.set(BctDataComponents.CREATIVE_GROUP, group);
        icon.set(BctDataComponents.CREATIVE_GROUP_TAB, tab);
        icon.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        icon.set(DataComponents.ITEM_NAME, BctText.name(group));
        return icon;
    }

    /**
     * Opens or closes one group in place, returning whether anything changed. The list is what a screen is showing -
     * its own copy of the folded tab - and not the tab's list itself: expanding is a property of the visit, so a page
     * that is opened again (or a tab that is switched back to) starts folded, because vanilla refills the screen from
     * the tab. The members are the stacks the fold kept, which is what makes the two directions the same operation.
     */
    public static boolean toggle(Collection<ItemStack> display, ResourceKey<CreativeModeTab> tab,
                                 CreativeGroup group, List<ItemStack> members) {
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
            // Found by the components rather than by object identity: a rebuild replaces the icon, and the new one
            // still stands for the same group on the same tab.
            if (!opened && isIconOf(stack, tab, group)) {
                rebuilt.addAll(members);
                opened = true;
            }
        }
        // An icon that is not on this list is a group from another tab, or one the list never had: nothing to open.
        if (!opened) return false;
        display.clear();
        display.addAll(rebuilt);
        return true;
    }

    /**
     * What a group is drawn as on the tab its icon carries: its own icon, or - when the definition writes none - the
     * first member the fold found there. A template that no longer builds a valid stack counts as "writes none",
     * because that is what {@code ItemStackTemplate#create} answers for one whose components no longer fit its item.
     */
    public static ItemStack displayIcon(ItemStack icon) {
        CreativeGroup group = icon.get(BctDataComponents.CREATIVE_GROUP);
        if (group == null) return ItemStack.EMPTY;
        ItemStack written = group.icon().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
        if (!written.isEmpty()) return written;
        List<ItemStack> members = members(icon.get(BctDataComponents.CREATIVE_GROUP_TAB), group);
        return members.isEmpty() ? ItemStack.EMPTY : members.getFirst();
    }

    /**
     * The stacks one tab's fold took off it for one group, in tab order. Empty for a tab that has not been built since
     * the last reload.
     */
    public static List<ItemStack> members(@Nullable ResourceKey<CreativeModeTab> tab, @Nullable CreativeGroup group) {
        if (tab == null || group == null) return List.of();
        return MEMBERS.getOrDefault(tab, Map.of()).getOrDefault(group.id(), List.of());
    }

    /**
     * Whether the group an icon stands for is open in the list a screen is showing. That list is the screen's own view
     * rather than the tab's, because that is where expanding happens: the tab itself always holds the folded shape, so
     * a page always opens folded.
     */
    public static boolean isOpen(List<ItemStack> view, ItemStack icon) {
        CreativeGroup group = icon.get(BctDataComponents.CREATIVE_GROUP);
        ResourceKey<CreativeModeTab> tab = icon.get(BctDataComponents.CREATIVE_GROUP_TAB);
        if (group == null || tab == null) return false;
        List<ItemStack> members = members(tab, group);
        return !members.isEmpty() && new HashSet<>(view).containsAll(members);
    }

    /**
     * Whether a stack is the icon of one group on one tab, which is how a click finds the entry to open next to.
     */
    public static boolean isIconOf(ItemStack stack, ResourceKey<CreativeModeTab> tab, CreativeGroup group) {
        return stack.is(BctItems.GROUP.get())
                && tab.equals(stack.get(BctDataComponents.CREATIVE_GROUP_TAB))
                && group.equals(stack.get(BctDataComponents.CREATIVE_GROUP));
    }

    /**
     * Throws away the member tables and rebuilds every tab, which a resource reload needs: a tab list is cached, so a
     * definition that has just been read - or just been taken away - is not folded into anything until the tab is
     * built again. Dropping the tables first matters because a member list is only meaningful next to the contents it
     * was taken from, and those contents are about to be replaced.
     */
    public static void onGroupsReloaded() {
        MEMBERS.clear();
        CreativeTabRebuild.rebuild();
    }

    private CreativeGroupService() {
    }
}
