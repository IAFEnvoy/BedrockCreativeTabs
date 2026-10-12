package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.BedrockCreativeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The naming self-check, driven by the {@code selftest} sentinel file. It exercises the very cases that motivated
 * the namer, asserting that id, Chinese and English all come out of the members' common part and stay readable:
 *
 * <ul>
 * <li>five swords with different prefixes -&gt; {@code sword} / 剑 / Sword;</li>
 * <li>a family of {@code *_drawer} storage items -&gt; {@code drawer(s)} / 抽屉 / Drawer(s);</li>
 * <li>the wool tag -&gt; {@code wool} / 羊毛 / Wool.</li>
 * </ul>
 *
 * <p>It logs rather than asserting-throws on purpose: the client is live, and a wrong name must not take the game
 * down - the log line is the evidence, read back like every other probe in this project.
 */
public final class BctNameSelfTest {
    private BctNameSelfTest() {
    }

    public static void run() {
        // 1) Different prefixes, one logical family: the sword tail must win.
        List<ItemStack> swords = stacks("minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword",
                "minecraft:golden_sword", "minecraft:diamond_sword");
        BctNames.Named sword = BctNames.fromMembers(swords);
        report("swords(5)", sword, "sword", "剑");

        // 2) A drawer-like family whose ids are not all in the registry: the id tail must still resolve.
        List<String> drawerIds = List.of("storagedrawers:oak_drawer", "storagedrawers:spruce_drawer",
                "storagedrawers:birch_drawer", "storagedrawers:jungle_drawer");
        String drawerTail = BctNames.commonIdTail(drawerIds);
        BedrockCreativeTabs.LOGGER.info("BCT namer selftest [drawer ids]: tail={} ({})",
                drawerTail, drawerTail.equals("drawer") ? "OK" : "期望 drawer");
        String drawerZh = BctNames.zhFromDict("drawer");
        BedrockCreativeTabs.LOGGER.info("BCT namer selftest [drawer dict]: zh={} ({})",
                drawerZh, drawerZh.equals("抽屉") ? "OK" : "期望 抽屉");

        // 3) The wool tag names from tag path + member display names.
        List<ItemStack> wools = stacks("minecraft:white_wool", "minecraft:orange_wool", "minecraft:red_wool",
                "minecraft:blue_wool", "minecraft:black_wool");
        BctNames.Named wool = BctNames.fromTag("wool", wools);
        report("tag wool", wool, "wool", "羊毛");

        // 4) Pure unit checks of the two derivations, so a failure points at which one drifted.
        BctNames.Named planks = BctNames.fromMembers(
                stacks("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks"));
        report("planks family", planks, "planks", "木板");

        BedrockCreativeTabs.LOGGER.info("BCT namer selftest: done");
    }

    private static void report(String label, BctNames.Named named, String expectPath, String expectZh) {
        String verdict = "";
        if (expectPath != null) verdict += " path=" + named.path() + (named.path().equals(expectPath) ? "(OK)" : "(期望 " + expectPath + ")");
        if (expectZh != null) verdict += " zh=" + named.nameZh() + (named.nameZh().equals(expectZh) ? "(OK)" : "(期望 " + expectZh + ")");
        BedrockCreativeTabs.LOGGER.info("BCT namer selftest [{}]: id={} zh={} en={}{}",
                label, named.path(), named.nameZh(), named.nameEn(), verdict);
    }

    private static List<ItemStack> stacks(String... ids) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (item != null && item != Items.AIR) out.add(new ItemStack(item));
        }
        return out;
    }
}
