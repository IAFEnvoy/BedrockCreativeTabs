package com.iafenvoy.bct.editor;

import com.iafenvoy.bct.api.GroupEntry;
import com.iafenvoy.bct.data.entry.ItemEntry;
import com.iafenvoy.bct.data.entry.ItemTagEntry;
import com.iafenvoy.bct.data.CreativeGroupLoader;
import com.iafenvoy.bct.runtime.CreativeGroupService;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Candidate groups the editor proposes on its own, so the player starts from suggestions instead of an empty
 * selection. Two sources, both reading the live universe (the real pre-fold tab contents - no jar heuristics):
 *
 * <ul>
 * <li><b>Item tags</b> - every tag holding {@link #MIN_ITEMS}..{@link #MAX_ITEMS} foldable items. This is the
 * high-confidence source: a tag is exactly what a pack would write.</li>
 * <li><b>Id suffixes</b> - the calibrated word list from the external BCT Group Studio ({@code *_stairs},
 * {@code *_sword}, ...), for the families no tag covers.</li>
 * </ul>
 *
 * <p>A candidate whose member set equals a group the loader already holds is marked {@link Candidate#duplicate()}
 * and a candidate is dropped when an earlier one claimed the same set - tags run first, so the suffix pass only
 * fills gaps. Members are the universe intersection, which is what folding would actually take.
 *
 * <p>The Chinese labels come from the same studio's word table, ported verbatim; a term with an unknown word falls
 * back to the English {@code titleCase} rather than shipping half-translated names.
 */
public final class BctCandidates {
    /** A proposed group. {@code path} is the id path it would save under. */
    public record Candidate(String path, String label, String nameZh, String nameEn,
                            List<GroupEntry> entries, List<String> memberIds) {
    }

    private static final int MIN_ITEMS = 4;
    private static final int MAX_ITEMS = 200;

    /** The external studio's calibrated suffix list, verbatim. */
    private static final List<String> SUFFIXES = List.of(
            "planks", "stairs", "slab", "walls", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate",
            "bars", "pane", "chain", "lantern", "candle", "banner", "bed", "shulker_box", "bundle", "sign",
            "hanging_sign", "boat", "chest_boat", "minecart", "rail", "sword", "axe", "pickaxe", "shovel", "hoe",
            "helmet", "chestplate", "leggings", "boots", "horse_armor", "ore", "ingot", "nugget", "dust", "gem",
            "sapling", "leaves", "seed", "coral", "coral_block", "coral_fan", "bud", "pottery_sherd",
            "smithing_template", "music_disc", "painting", "goat_horn", "harness", "shelf", "grate", "bulb",
            "statue", "head", "nautilus_armor", "dye", "concrete", "concrete_powder", "terracotta",
            "glazed_terracotta", "wool", "carpet", "glass", "glass_pane", "brick", "stone", "cobblestone",
            "sandstone", "deepslate");

    private static final Map<String, String> ZH = Map.ofEntries(
            Map.entry("planks", "木板"), Map.entry("log", "原木"), Map.entry("logs", "原木"), Map.entry("wood", "木"),
            Map.entry("stripped", "去皮"), Map.entry("stem", "菌柄"), Map.entry("hyphae", "菌核"),
            Map.entry("stairs", "楼梯"), Map.entry("slab", "台阶"), Map.entry("slabs", "台阶"), Map.entry("wall", "墙"),
            Map.entry("walls", "墙"), Map.entry("fence", "栅栏"), Map.entry("fences", "栅栏"), Map.entry("gate", "门"),
            Map.entry("fence_gate", "栅栏门"), Map.entry("door", "门"), Map.entry("doors", "门"),
            Map.entry("trapdoor", "活板门"), Map.entry("trapdoors", "活板门"), Map.entry("button", "按钮"),
            Map.entry("buttons", "按钮"), Map.entry("pressure_plate", "压力板"), Map.entry("pressure_plates", "压力板"),
            Map.entry("bars", "栏杆"), Map.entry("pane", "板"), Map.entry("panes", "板"), Map.entry("glass", "玻璃"),
            Map.entry("glass_pane", "玻璃板"), Map.entry("ore", "矿石"), Map.entry("ores", "矿石"), Map.entry("ingot", "锭"),
            Map.entry("ingots", "锭"), Map.entry("nugget", "粒"), Map.entry("nuggets", "粒"), Map.entry("dust", "粉"),
            Map.entry("dusts", "粉"), Map.entry("gem", "宝石"), Map.entry("gems", "宝石"), Map.entry("raw", "粗"),
            Map.entry("block", "块"), Map.entry("blocks", "方块"), Map.entry("brick", "砖"), Map.entry("bricks", "砖"),
            Map.entry("stone", "石"), Map.entry("stones", "石头"), Map.entry("cobblestone", "圆石"),
            Map.entry("cobblestones", "圆石"), Map.entry("deepslate", "深板岩"), Map.entry("sandstone", "砂岩"),
            Map.entry("terracotta", "陶瓦"), Map.entry("glazed", "带釉"), Map.entry("glazed_terracottas", "带釉陶瓦"),
            Map.entry("concrete", "混凝土"), Map.entry("concretes", "混凝土"), Map.entry("concrete_powder", "混凝土粉末"),
            Map.entry("concrete_powders", "混凝土粉末"), Map.entry("wool", "羊毛"), Map.entry("wools", "羊毛"),
            Map.entry("carpet", "地毯"), Map.entry("carpets", "地毯"), Map.entry("dye", "染料"), Map.entry("dyes", "染料"),
            Map.entry("sword", "剑"), Map.entry("swords", "剑"), Map.entry("axe", "斧"), Map.entry("axes", "斧"),
            Map.entry("pickaxe", "镐"), Map.entry("pickaxes", "镐"), Map.entry("shovel", "锹"), Map.entry("shovels", "锹"),
            Map.entry("hoe", "锄"), Map.entry("hoes", "锄"), Map.entry("helmet", "头盔"), Map.entry("helmets", "头盔"),
            Map.entry("chestplate", "胸甲"), Map.entry("chestplates", "胸甲"), Map.entry("leggings", "护腿"),
            Map.entry("boots", "靴子"), Map.entry("horse_armor", "马铠"), Map.entry("shield", "盾牌"),
            Map.entry("shields", "盾牌"), Map.entry("bow", "弓"), Map.entry("bows", "弓"), Map.entry("crossbow", "弩"),
            Map.entry("arrow", "箭"), Map.entry("arrows", "箭"), Map.entry("tipped_arrows", "药箭"),
            Map.entry("tipped", "药箭"), Map.entry("trident", "三叉戟"), Map.entry("fishing_rod", "钓鱼竿"),
            Map.entry("shears", "剪刀"), Map.entry("food", "食物"), Map.entry("foods", "食物"), Map.entry("apple", "苹果"),
            Map.entry("bread", "面包"), Map.entry("meat", "肉"), Map.entry("cooked", "熟"), Map.entry("seed", "种子"),
            Map.entry("seeds", "种子"), Map.entry("sapling", "树苗"), Map.entry("saplings", "树苗"),
            Map.entry("leaves", "树叶"), Map.entry("flower", "花"), Map.entry("flowers", "花"),
            Map.entry("small_flowers", "小型花"), Map.entry("coral", "珊瑚"), Map.entry("corals", "珊瑚"),
            Map.entry("coral_block", "珊瑚块"), Map.entry("coral_blocks", "珊瑚块"), Map.entry("coral_fan", "珊瑚扇"),
            Map.entry("coral_fans", "珊瑚扇"), Map.entry("amethyst", "紫水晶"), Map.entry("bud", "芽"),
            Map.entry("buds", "芽"), Map.entry("copper", "铜"), Map.entry("iron", "铁"), Map.entry("gold", "金"),
            Map.entry("diamond", "钻石"), Map.entry("emerald", "绿宝石"), Map.entry("netherite", "下界合金"),
            Map.entry("redstone", "红石"), Map.entry("coal", "煤"), Map.entry("charcoal", "木炭"),
            Map.entry("lapis", "青金石"), Map.entry("quartz", "石英"), Map.entry("nether", "下界"), Map.entry("end", "末地"),
            Map.entry("chest", "箱子"), Map.entry("chests", "箱子"), Map.entry("boat", "船"), Map.entry("boats", "船"),
            Map.entry("chest_boats", "箱子船"), Map.entry("boat_with_chest", "箱子船"), Map.entry("minecart", "矿车"),
            Map.entry("minecarts", "矿车"), Map.entry("rail", "铁轨"), Map.entry("rails", "铁轨"),
            Map.entry("sign", "告示牌"), Map.entry("signs", "告示牌"), Map.entry("hanging_sign", "悬挂告示牌"),
            Map.entry("hanging_signs", "悬挂告示牌"), Map.entry("lantern", "灯笼"), Map.entry("lanterns", "灯笼"),
            Map.entry("chain", "锁链"), Map.entry("chains", "锁链"), Map.entry("candle", "蜡烛"),
            Map.entry("candles", "蜡烛"), Map.entry("banner", "旗帜"), Map.entry("banners", "旗帜"), Map.entry("bed", "床"),
            Map.entry("beds", "床"), Map.entry("shulker_box", "潜影盒"), Map.entry("shulker_boxes", "潜影盒"),
            Map.entry("bundle", "收纳袋"), Map.entry("bundles", "收纳袋"), Map.entry("bucket", "桶"),
            Map.entry("buckets", "桶"), Map.entry("potion", "药水"), Map.entry("potions", "药水"),
            Map.entry("music_disc", "唱片"), Map.entry("music_discs", "唱片"), Map.entry("painting", "画"),
            Map.entry("paintings", "画"), Map.entry("goat_horn", "山羊角"), Map.entry("goat_horns", "山羊角"),
            Map.entry("pottery_sherd", "陶片"), Map.entry("pottery_sherds", "陶片"),
            Map.entry("smithing_template", "升级模板"), Map.entry("smithing_templates", "升级模板"),
            Map.entry("harness", "挽具"), Map.entry("harnesses", "挽具"), Map.entry("shelf", "架子"),
            Map.entry("shelves", "架子"), Map.entry("grate", "格栅"), Map.entry("grates", "格栅"),
            Map.entry("bulb", "铜灯"), Map.entry("bulbs", "铜灯"), Map.entry("statue", "雕像"), Map.entry("head", "头颅"),
            Map.entry("heads", "头颅"), Map.entry("armor", "盔甲"), Map.entry("tools", "工具"), Map.entry("weapon", "武器"),
            Map.entry("weapons", "武器"), Map.entry("white", "白色"), Map.entry("orange", "橙色"),
            Map.entry("magenta", "品红"), Map.entry("light_blue", "淡蓝"), Map.entry("yellow", "黄色"),
            Map.entry("lime", "黄绿"), Map.entry("pink", "粉色"), Map.entry("gray", "灰色"),
            Map.entry("light_gray", "淡灰"), Map.entry("cyan", "青色"), Map.entry("purple", "紫色"),
            Map.entry("blue", "蓝色"), Map.entry("brown", "棕色"), Map.entry("green", "绿色"), Map.entry("red", "红色"),
            Map.entry("black", "黑色"));

    private BctCandidates() {
    }

    /** Generates the full candidate list, tags first, then suffixes; same-set duplicates collapse. */
    public static List<Candidate> generate() {
        Map<String, ItemStack> universe = new LinkedHashMap<>();
        for (ItemStack stack : CreativeGroupService.universe()) {
            if (stack.isEmpty()) continue;
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null) universe.putIfAbsent(key.toString(), stack);
        }
        Map<String, Candidate> out = new LinkedHashMap<>();
        Map<String, String> existing = existingSignatures();
        tagCandidates(universe, existing, out);
        suffixCandidates(universe, existing, out);
        return List.copyOf(out.values());
    }

    private static void tagCandidates(Map<String, ItemStack> universe, Map<String, String> existing,
                                      Map<String, Candidate> out) {
        BuiltInRegistries.ITEM.getTags().forEach(named -> {
            TagKey<Item> tag = named.key();
            List<String> members = new ArrayList<>();
            for (Map.Entry<String, ItemStack> entry : universe.entrySet()) {
                if (entry.getValue().is(tag)) members.add(entry.getKey());
            }
            if (members.size() < MIN_ITEMS || members.size() > MAX_ITEMS) return;
            String path = tag.location().getPath();
            Candidate candidate = new Candidate(path, "#" + tag.location(), zhName(path), titleCase(path),
                    List.of(new ItemTagEntry(tag)), members);
            offer(candidate, existing, out);
        });
    }

    private static void suffixCandidates(Map<String, ItemStack> universe, Map<String, String> existing,
                                         Map<String, Candidate> out) {
        Map<String, List<String>> bySuffix = new LinkedHashMap<>();
        for (String suffix : SUFFIXES) bySuffix.put(suffix, new ArrayList<>());
        for (Map.Entry<String, ItemStack> entry : universe.entrySet()) {
            String path = Identifier.parse(entry.getKey()).getPath();
            for (String suffix : SUFFIXES) {
                if (path.equals(suffix) || path.endsWith("_" + suffix)) {
                    bySuffix.get(suffix).add(entry.getKey());
                    break;
                }
            }
        }
        for (String suffix : SUFFIXES) {
            List<String> members = bySuffix.get(suffix);
            if (members.size() < MIN_ITEMS || members.size() > MAX_ITEMS) continue;
            // Sorted by id, never by Item: items are not Comparable, and a TreeSet of them throws mid-fold.
            List<GroupEntry> entries = new ArrayList<>();
            for (String id : new TreeSet<>(members)) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                if (item != null) entries.add(new ItemEntry(item));
            }
            Candidate candidate = new Candidate("auto_" + suffix, "*_" + suffix, zhName(suffix), titleCase(suffix),
                    entries, members);
            offer(candidate, existing, out);
        }
    }

    private static void offer(Candidate candidate, Map<String, String> existing, Map<String, Candidate> out) {
        String signature = String.join(",", candidate.memberIds());
        if (existing.containsKey(signature) || out.containsKey(signature)) return;
        out.put(signature, candidate);
    }

    /** What the loader's groups already fold, as member-set signatures - a candidate equal to one is redundant. */
    private static Map<String, String> existingSignatures() {
        Map<String, String> signatures = new LinkedHashMap<>();
        for (Map.Entry<Identifier, com.iafenvoy.bct.data.CreativeGroup> entry : CreativeGroupLoader.groups().entrySet()) {
            TreeSet<String> ids = new TreeSet<>();
            for (ResourceKey<CreativeModeTab> tab : entry.getValue().creativeTabs()) {
                for (ItemStack stack : CreativeGroupService.members(tab, entry.getKey())) {
                    Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (key != null) ids.add(key.toString());
                }
            }
            if (!ids.isEmpty()) signatures.put(String.join(",", ids), entry.getKey().toString());
        }
        return signatures;
    }

    /** Term words joined through the table; any unknown word falls back to empty, and the caller uses English. */
    private static String zhName(String term) {
        StringBuilder out = new StringBuilder();
        for (String part : term.split("_")) {
            String word = ZH.get(part);
            if (word == null) return "";
            out.append(word);
        }
        return out.toString();
    }

    private static String titleCase(String term) {
        StringBuilder out = new StringBuilder();
        for (String part : term.split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
