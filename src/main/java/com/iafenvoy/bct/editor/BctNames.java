package com.iafenvoy.bct.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Derives a group's id, Chinese name and English name from the very items the group holds, so the three agree
 * with each other by construction instead of being three unrelated guesses.
 *
 * <p><b>The trick is the common part.</b> Five swords with different prefixes - {@code wooden_sword},
 * {@code stone_sword}, {@code iron_sword}, ... - share the trailing id word {@code sword} and the trailing
 * display-name word {@code 剑}; a family of {@code *_drawer} items shares {@code drawer(s)} and {@code 抽屉}.
 * Reading that common tail gives all three answers from one source: id, Chinese and English.
 *
 * <p><b>Chinese comes from the game, not a word list.</b> The item's own localized name ({@code getHoverName})
 * is already the player's language, so the common Chinese suffix is taken from real display names - which works
 * for any mod, including ones no dictionary knows. The built-in dictionary is only the fallback for when the
 * display names share no common tail.
 *
 * <p>No AUI, no screen, no world mutation: pure functions over item ids and stacks, so the naming can be tested
 * and reused from the picker and the candidate generator alike.
 */
public final class BctNames {
    /**
     * The result of a naming pass: a suggested id path plus the two names. Any field may be blank when no basis
     * was found, and the caller falls back - the id path is never blank.
     */
    public record Named(String path, String nameZh, String nameEn) {
    }

    private static final String FALLBACK_PATH = "custom_group";
    private static final String FALLBACK_EN = "Custom Group";
    private static final String FALLBACK_ZH = "自定义分组";

    /**
     * The studio's word table, ported verbatim, plus a handful of mod-common words the table lacked observably
     * (drawer, and friends). Used only when the display-name tail says nothing.
     */
    private static final Map<String, String> ZH = new LinkedHashMap<>();

    static {
        put("planks", "木板");
        put("log", "原木");
        put("logs", "原木");
        put("wood", "木");
        put("stripped", "去皮");
        put("stairs", "楼梯");
        put("slab", "台阶");
        put("slabs", "台阶");
        put("wall", "墙");
        put("walls", "墙");
        put("fence", "栅栏");
        put("fences", "栅栏");
        put("gate", "门");
        put("fence_gate", "栅栏门");
        put("door", "门");
        put("doors", "门");
        put("trapdoor", "活板门");
        put("button", "按钮");
        put("buttons", "按钮");
        put("pressure_plate", "压力板");
        put("bars", "栏杆");
        put("pane", "板");
        put("panes", "板");
        put("glass", "玻璃");
        put("glass_pane", "玻璃板");
        put("ore", "矿石");
        put("ores", "矿石");
        put("ingot", "锭");
        put("ingots", "锭");
        put("nugget", "粒");
        put("nuggets", "粒");
        put("dust", "粉");
        put("gem", "宝石");
        put("raw", "粗");
        put("block", "块");
        put("blocks", "方块");
        put("storage", "存储");
        put("brick", "砖");
        put("bricks", "砖");
        put("stone", "石");
        put("cobblestone", "圆石");
        put("deepslate", "深板岩");
        put("sandstone", "砂岩");
        put("terracotta", "陶瓦");
        put("glazed", "带釉");
        put("concrete", "混凝土");
        put("concrete_powder", "混凝土粉末");
        put("wool", "羊毛");
        put("wools", "羊毛");
        put("carpet", "地毯");
        put("carpets", "地毯");
        put("dye", "染料");
        put("dyes", "染料");
        put("sword", "剑");
        put("swords", "剑");
        put("axe", "斧");
        put("axes", "斧");
        put("pickaxe", "镐");
        put("shovel", "锹");
        put("hoe", "锄");
        put("helmet", "头盔");
        put("chestplate", "胸甲");
        put("leggings", "护腿");
        put("boots", "靴子");
        put("horse_armor", "马铠");
        put("shield", "盾牌");
        put("bow", "弓");
        put("crossbow", "弩");
        put("arrow", "箭");
        put("arrows", "箭");
        put("tipped_arrow", "药箭");
        put("trident", "三叉戟");
        put("fishing_rod", "钓鱼竿");
        put("shears", "剪刀");
        put("apple", "苹果");
        put("bread", "面包");
        put("seed", "种子");
        put("seeds", "种子");
        put("sapling", "树苗");
        put("leaves", "树叶");
        put("flower", "花");
        put("flowers", "花");
        put("coral", "珊瑚");
        put("coral_block", "珊瑚块");
        put("coral_fan", "珊瑚扇");
        put("amethyst", "紫水晶");
        put("bud", "芽");
        put("copper", "铜");
        put("iron", "铁");
        put("gold", "金");
        put("diamond", "钻石");
        put("emerald", "绿宝石");
        put("netherite", "下界合金");
        put("redstone", "红石");
        put("coal", "煤");
        put("charcoal", "木炭");
        put("lapis", "青金石");
        put("quartz", "石英");
        put("nether", "下界");
        put("end", "末地");
        put("chest", "箱子");
        put("chests", "箱子");
        put("boat", "船");
        put("boats", "船");
        put("chest_boat", "箱子船");
        put("minecart", "矿车");
        put("rail", "铁轨");
        put("rails", "铁轨");
        put("sign", "告示牌");
        put("hanging_sign", "悬挂告示牌");
        put("lantern", "灯笼");
        put("chain", "锁链");
        put("candle", "蜡烛");
        put("banner", "旗帜");
        put("bed", "床");
        put("shulker_box", "潜影盒");
        put("bundle", "收纳袋");
        put("bucket", "桶");
        put("potion", "药水");
        put("music_disc", "唱片");
        put("painting", "画");
        put("goat_horn", "山羊角");
        put("pottery_sherd", "陶片");
        put("smithing_template", "升级模板");
        put("armor_trim", "盔甲纹饰");
        put("trim", "纹饰");
        put("harness", "挽具");
        put("spear", "长矛");
        put("shelf", "架子");
        put("shelves", "架子");
        put("lightning_rod", "避雷针");
        put("firework", "烟花");
        put("chiseled", "雕纹");
        put("cut", "切制");
        put("grate", "格栅");
        put("grates", "格栅");
        put("bulb", "铜灯");
        put("bulbs", "铜灯");
        put("statue", "雕像");
        put("head", "头颅");
        put("skull", "头颅");
        put("nautilus_armor", "鹦鹉螺盔甲");
        put("armor", "盔甲");
        put("tools", "工具");
        put("tool", "工具");
        put("weapon", "武器");
        put("weapons", "武器");
        put("gear", "装备");
        put("material", "材料");
        put("ingredient", "材料");
        put("alloy", "合金");
        put("casing", "外壳");
        put("pipe", "管道");
        put("cable", "线缆");
        put("machine", "机器");
        put("machines", "机器");
        put("battery", "电池");
        put("template", "模板");
        put("plate", "板");
        put("plates", "板");
        put("rod", "杆");
        put("rods", "杆");
        put("crystal", "水晶");
        put("circuit", "电路");
        put("chip", "芯片");
        put("component", "零件");
        put("upgrade", "升级");
        put("module", "模块");
        // Mod-common words the studio table lacked - the very "drawer" family the user named.
        put("drawer", "抽屉");
        put("drawers", "抽屉");
        put("crate", "板条箱");
        put("crates", "板条箱");
        put("barrel", "木桶");
        put("cabinet", "储物柜");
        put("cabinets", "储物柜");
        put("tank", "储罐");
        put("tanks", "储罐");
        put("generator", "发电机");
        put("generators", "发电机");
        put("drill", "钻头");
        put("drills", "钻头");
        put("wrench", "扳手");
        put("hammer", "锤子");
        put("dusts", "粉");
        put("storage", "存储");
        put("machine_block", "机器方块");
        put("white", "白色");
        put("orange", "橙色");
        put("magenta", "品红");
        put("light_blue", "淡蓝");
        put("yellow", "黄色");
        put("lime", "黄绿");
        put("pink", "粉色");
        put("gray", "灰色");
        put("light_gray", "淡灰");
        put("cyan", "青色");
        put("purple", "紫色");
        put("blue", "蓝色");
        put("brown", "棕色");
        put("green", "绿色");
        put("red", "红色");
        put("black", "黑色");
    }

    private static void put(String key, String value) {
        ZH.put(key, value);
    }

    private BctNames() {
    }

    /**
     * Names a group from its member stacks. The id path and both names come from the same common tail, so they
     * agree; anything that cannot be derived falls back rather than producing a machine name.
     */
    public static Named fromMembers(List<ItemStack> members) {
        List<String> paths = new ArrayList<>();
        List<String> displayNames = new ArrayList<>();
        for (ItemStack stack : members) {
            if (stack.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            paths.add(key.getPath());
            displayNames.add(stack.getHoverName().getString());
        }
        if (paths.isEmpty()) return new Named(FALLBACK_PATH, FALLBACK_ZH, FALLBACK_EN);
        String tail = commonIdTail(paths);
        String path = tail.isEmpty() ? deterministicSlug(paths) : tail;
        String en = tail.isEmpty() ? FALLBACK_EN : titleCase(tail);
        String zh = commonZhTail(displayNames);
        if (zh.isEmpty()) zh = zhFromDict(path);
        if (zh.isEmpty()) zh = en;
        return new Named(path, zh, en);
    }

    /**
     * Names a group from a tag path and its members: the tag gives the id and English, while Chinese still prefers
     * what the items' own names share (a tag can be a mod word no dictionary knows).
     */
    public static Named fromTag(String tagPath, List<ItemStack> members) {
        List<String> displayNames = new ArrayList<>();
        for (ItemStack stack : members) {
            if (!stack.isEmpty()) displayNames.add(stack.getHoverName().getString());
        }
        String leaf = tagPath.contains("/") ? tagPath.substring(tagPath.lastIndexOf('/') + 1) : tagPath;
        String zh = commonZhTail(displayNames);
        if (zh.isEmpty()) zh = zhFromDict(leaf);
        if (zh.isEmpty()) zh = titleCase(leaf);
        return new Named(leaf, zh, titleCase(leaf));
    }

    /**
     * The id-path fragment repeated across the most members - the words every member's id shares count highest.
     * {@code [wooden_sword, stone_sword, iron_sword]} -&gt; {@code sword}; a drawer family -&gt; {@code drawer}.
     * Unlike a strict common tail this tolerates one straggler: coverage decides, so a lone {@code oak_log} in a
     * pile of {@code *_planks} does not erase {@code planks}. Ties prefer a fragment that sits at the end of an id
     * (the family word usually does) and then the longer one.
     */
    static String commonIdTail(List<String> paths) {
        if (paths.isEmpty()) return "";
        if (paths.size() == 1) return paths.getFirst().replace('_', '_');
        Map<String, Integer> coverage = new LinkedHashMap<>();
        Map<String, Integer> endsWith = new LinkedHashMap<>();
        for (String path : paths) {
            List<String> words = new ArrayList<>();
            for (String word : path.split("_")) if (!word.isEmpty()) words.add(word);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (!seen.add(word)) continue;
                coverage.merge(word, 1, Integer::sum);
                if (i == words.size() - 1) endsWith.merge(word, 1, Integer::sum);
            }
        }
        int majority = Math.max(2, (paths.size() + 1) / 2); // at least half, and never just one item
        String best = null;
        for (Map.Entry<String, Integer> entry : coverage.entrySet()) {
            if (entry.getValue() < majority) continue;
            if (best == null || betterIdFragment(entry.getKey(), best, coverage, endsWith)) best = entry.getKey();
        }
        return best == null ? "" : best;
    }

    private static boolean betterIdFragment(String candidate, String best, Map<String, Integer> coverage,
                                            Map<String, Integer> endsWith) {
        int byCount = coverage.get(candidate) - coverage.get(best);
        if (byCount != 0) return byCount > 0;
        int byEnd = endsWith.getOrDefault(candidate, 0) - endsWith.getOrDefault(best, 0);
        if (byEnd != 0) return byEnd > 0;
        return candidate.length() > best.length();
    }

    /**
     * The Chinese fragment repeated across the most members' display names - the game's own words, so it holds for
     * any mod: 木剑/石剑/铁剑 -&gt; 剑, 铜牛排/铁牛排/金牛排 -&gt; 牛排, 存储抽屉/抽屉 -&gt; 抽屉.
     *
     * <p>Coverage is only a <b>gate</b>, and among the fragments that clear it the <b>longest</b> wins. That is the
     * whole trick: an earlier version returned as soon as a fragment was shared by <em>every</em> member, so one
     * outlier collapsed the name to its last character - a set of planks plus the vanilla 竹板 (bamboo planks) is
     * all-shared on 板, and 木板 (shared by the other 94%) never got a chance. Gating by coverage and preferring
     * length returns 木板, 牛排, and friends.
     */
    static String commonZhTail(List<String> displayNames) {
        List<String> names = new ArrayList<>();
        for (String name : displayNames) {
            String trimmed = name == null ? "" : name.trim();
            if (!trimmed.isEmpty()) names.add(trimmed);
        }
        if (names.isEmpty()) return "";
        if (names.size() == 1) return trimColorPrefix(stripConnectives(names.getFirst()));

        Map<String, Integer> coverage = new LinkedHashMap<>();
        for (String name : names) {
            Set<String> seen = new HashSet<>();
            // Long enough for mod family words like 下界合金块/铜傀儡雕像; still short enough not to eat a whole name.
            int max = Math.min(name.length(), 8);
            for (int length = 1; length <= max; length++) {
                String fragment = name.substring(name.length() - length);
                if (!seen.add(fragment)) continue;
                coverage.merge(fragment, 1, Integer::sum);
            }
        }
        int threshold = Math.max(2, (names.size() * 3 + 4) / 5); // ceil(0.6 * n)
        String best = "";
        int bestCoverage = 0;
        for (Map.Entry<String, Integer> entry : coverage.entrySet()) {
            String fragment = entry.getKey();
            if (entry.getValue() < threshold) continue;
            if (fragment.length() == 1 && fragment.charAt(0) < 0x80) continue; // a lone "s" is noise, 排 is not
            if (betterFragment(fragment, entry.getValue(), best, bestCoverage)) {
                best = fragment;
                bestCoverage = entry.getValue();
            }
        }
        // Nothing cleared the gate: fall back to whatever every member shares, if anything.
        if (best.isEmpty()) {
            for (Map.Entry<String, Integer> entry : coverage.entrySet()) {
                if (entry.getValue() < names.size()) continue;
                String fragment = entry.getKey();
                if (fragment.length() == 1 && fragment.charAt(0) < 0x80) continue;
                if (betterFragment(fragment, entry.getValue(), best, bestCoverage)) {
                    best = fragment;
                    bestCoverage = entry.getValue();
                }
            }
        }
        return trimColorPrefix(stripConnectives(best));
    }

    /**
     * Longer wins outright (a real word beats its own tail); equal length falls back to wider coverage.
     */
    private static boolean betterFragment(String candidate, int candidateCoverage, String best, int bestCoverage) {
        if (best.isEmpty()) return true;
        if (candidate.length() != best.length()) return candidate.length() > best.length();
        return candidateCoverage > bestCoverage;
    }

    /**
     * Drops a leading connective that a mod's name left on the shared tail: "的避雷针" -&gt; "避雷针".
     */
    private static String stripConnectives(String tail) {
        String out = tail;
        while (!out.isEmpty() && (out.charAt(0) == '的' || out.charAt(0) == '之' || out.charAt(0) == '和'))
            out = out.substring(1);
        return out;
    }

    /**
     * Strips a leading colour word off a Chinese tail: 白/橙/红...羊毛 share the literal substring 色羊毛, but the
     * colour is a per-item prefix and the family is 羊毛. Only a word the dictionary knows as a colour is removed,
     * so a genuine two-character tail is never touched.
     */
    private static String trimColorPrefix(String tail) {
        if (tail.length() <= 1) return tail;
        String[] colors = {"白色", "橙色", "品红", "淡蓝", "黄色", "黄绿", "粉色", "灰色", "淡灰", "青色",
                "紫色", "蓝色", "棕色", "绿色", "红色", "黑色"};
        for (String color : colors) {
            // The shared tail often starts mid-colour ("色羊毛"), so match the colour's own tail too.
            for (int cut = 1; cut < color.length(); cut++) {
                String fragment = color.substring(cut);
                if (tail.startsWith(fragment))
                    return tail.substring(fragment.length());
            }
        }
        return tail;
    }

    /**
     * Dictionary lookup for a term's words; empty when any word is unknown (never half-translated).
     */
    static String zhFromDict(String term) {
        StringBuilder out = new StringBuilder();
        for (String word : term.split("_")) {
            if (word.isEmpty()) continue;
            String translated = ZH.get(word);
            if (translated == null) return "";
            out.append(translated);
        }
        return out.toString();
    }

    static String titleCase(String term) {
        StringBuilder out = new StringBuilder();
        for (String word : term.split("[_\\s]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? "" : out.toString();
    }

    /**
     * A stable path for a family with no common tail: built from the sorted member ids, so the same selection
     * always yields the same path - never a timestamp, which would change on every create.
     */
    static String deterministicSlug(List<String> paths) {
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String::compareTo);
        String first = sorted.getFirst().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (!first.isEmpty()) return truncate(first, 40);
        return FALLBACK_PATH;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
