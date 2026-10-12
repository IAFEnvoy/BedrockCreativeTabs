package com.iafenvoy.bct.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.bct.BedrockCreativeTabs;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The writable side of the group definitions: the directory the editor owns.
 *
 * <p>Laid out exactly like the resource packs BCT already reads, so "export" is a zip of this directory and nothing
 * has to be rewritten:
 * <pre>
 * config/bedrock_creative_tabs/groups/&lt;namespace&gt;/&lt;path&gt;.json
 * config/bedrock_creative_tabs/lang/zh_cn.json      keys: creative_group.&lt;namespace&gt;.&lt;path&gt;
 * config/bedrock_creative_tabs/lang/en_us.json
 * </pre>
 *
 * <p>The name lives in the same language files a pack would ship. That is deliberate: the pack layout is the format
 * the rest of the ecosystem uses, so a group made here can be shared without translation, and a group taken from a
 * pack can be dropped in untouched.
 *
 * <p>Writes go through a temporary file and are moved into place, so a crash mid-write cannot leave a half-written
 * definition for the loader to choke on.
 */
public final class BctGroupStore {
    private static final String ROOT = "config/bedrock_creative_tabs";
    private static final String DEFAULT_NAMESPACE = "bct_user";
    private static final GsonBuilder GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping();

    // Read once with the groups; the icon asks for a name on every tab build, so it cannot touch the disk each time.
    private static final Map<Identifier, String> names = new LinkedHashMap<>();

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace, path);
    }

    public static String nameOf(Identifier id) {
        return names.get(id);
    }

    /** The stored name in one locale, or {@code null} - what the rename panel's fields prefill from. */
    public static String nameIn(Identifier id, String locale) {
        return readLang(locale, id);
    }

    /**
     * Rewrites a group's language entries in place. A {@code null} keeps the existing text; the tabs are not
     * rebuilt here - the caller decides when the new names should reach the icons.
     *
     * <p>The runtime cache is recomputed from the files, not from the argument: changing only the English name,
     * or clearing the Chinese one so the English takes over, has to move {@link #nameOf} with it - otherwise the
     * icon keeps showing the stale name while the file says otherwise.
     */
    public static void rename(Identifier id, String zh, String en) {
        if (zh != null) writeLang("zh_cn", id, zh);
        if (en != null) writeLang("en_us", id, en);
        refreshName(id);
    }

    /** Recomputes one group's cached display name: Chinese first, English next, none means fall back to translation. */
    private static void refreshName(Identifier id) {
        String name = readLang("zh_cn", id);
        if (name == null) name = readLang("en_us", id);
        if (name == null) names.remove(id);
        else names.put(id, name);
    }

    /** Every group in the writable directory, keyed by file name. A file that fails to parse is logged and skipped. */
    public static Map<Identifier, CreativeGroup> load() {
        LinkedHashMap<Identifier, CreativeGroup> loaded = new LinkedHashMap<>();
        Path root = root().resolve("groups");
        if (!Files.isDirectory(root)) return loaded;
        try (var namespaces = Files.list(root)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                String namespace = namespaceDir.getFileName().toString();
                try (var files = Files.walk(namespaceDir)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                        String relative = namespaceDir.relativize(file).toString().replace('\\', '/');
                        String path = relative.substring(0, relative.length() - ".json".length());
                        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                        read(file).ifPresentOrElse(group -> loaded.put(id, group),
                                () -> BedrockCreativeTabs.LOGGER.warn("BCT editor: {} did not parse as a creative group", file));
                    }
                }
            }
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.warn("BCT editor: failed to read {}", root, exception);
        }
        names.clear();
        for (Identifier id : loaded.keySet()) {
            String name = readLang("zh_cn", id);
            if (name == null) name = readLang("en_us", id);
            if (name != null) names.put(id, name);
        }
        BedrockCreativeTabs.LOGGER.info("BCT editor: loaded {} group(s) from {}", loaded.size(), root);
        return loaded;
    }

    /** Writes one group plus its two language names. Returns the file it landed in. */
    public static Path save(Identifier id, CreativeGroup group, String nameZh, String nameEn) {
        Path file = null;
        try {
            JsonElement encoded = CreativeGroup.CODEC.encodeStart(JsonOps.INSTANCE, group).getOrThrow();
            file = groupFile(id);
            write(file, GSON.create().toJson(encoded) + "\n");
            if (nameZh != null) writeLang("zh_cn", id, nameZh);
            if (nameEn != null) writeLang("en_us", id, nameEn);
            refreshName(id);
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.error("BCT editor: could not write {}", id, exception);
        }
        return file;
    }

    public static boolean delete(Identifier id) {
        try {
            boolean deleted = Files.deleteIfExists(groupFile(id));
            if (deleted) {
                writeLang("zh_cn", id, null);
                writeLang("en_us", id, null);
                names.remove(id);
            }
            return deleted;
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.warn("BCT editor: could not delete {}", id, exception);
            return false;
        }
    }

    /** The directory as a resource pack would need it, minus the zip: what an export would copy. */
    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(ROOT);
    }

    /** How many group definitions sit in the writable directory, for a readable export report. */
    public static int countGroups() {
        Path groupsRoot = root().resolve("groups");
        if (!Files.isDirectory(groupsRoot)) return 0;
        try (var stream = Files.walk(groupsRoot)) {
            return (int) stream.filter(path -> path.toString().endsWith(".json")).count();
        } catch (Exception exception) {
            return 0;
        }
    }

    /**
     * Zips the writable directory straight into {@code resourcepacks/BCT_groups.zip}, ready to enable in the pack
     * list - the closing move of the editor: what the player built here becomes a shareable pack untouched.
     *
     * <p>The zip mirrors the on-disk layout into resource-pack space: {@code groups/&lt;ns&gt;/&lt;path&gt;.json}
     * becomes {@code assets/&lt;ns&gt;/bedrock_creative_tabs/creative_group/&lt;path&gt;.json} (what the loader
     * reads), and {@code lang/&lt;locale&gt;.json} becomes {@code assets/bct_user/lang/&lt;locale&gt;.json} - the
     * {@code creative_group.*} keys are global, so one namespace carrying them is enough.
     *
     * <p>{@code pack.mcmeta} declares {@code pack_format: 64} <em>and</em> {@code min_format}/{@code max_format}:
     * 26.1 refuses packs claiming support beyond 64 without an explicit range ("missing mandatory fields
     * min_format and max_format" - the warning the old BCT Studio export tripped). The fields parse as ints.
     *
     * @return the zip, or {@code null} when the write failed
     */
    public static Path exportPack() {
        Path target = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks").resolve("BCT_groups.zip");
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            put(zip, "pack.mcmeta", PACK_META);
            Path groupsRoot = root().resolve("groups");
            if (Files.isDirectory(groupsRoot)) {
                try (var namespaces = Files.list(groupsRoot)) {
                    for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                        String namespace = namespaceDir.getFileName().toString();
                        try (var files = Files.walk(namespaceDir)) {
                            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                                String relative = namespaceDir.relativize(file).toString().replace('\\', '/');
                                put(zip, "assets/" + namespace + "/bedrock_creative_tabs/creative_group/" + relative,
                                        Files.readString(file, StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
            }
            Path langDir = root().resolve("lang");
            if (Files.isDirectory(langDir)) {
                try (var files = Files.list(langDir)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                        put(zip, "assets/" + DEFAULT_NAMESPACE + "/lang/" + file.getFileName(),
                                Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.error("BCT editor: export to {} failed", target, exception);
            return null;
        }
        try {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.error("BCT editor: could not move export into place at {}", target, exception);
            return null;
        }
        BedrockCreativeTabs.LOGGER.info("BCT editor: exported {} group(s) to {}", countGroups(), target);
        return target;
    }

    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 64,
                "min_format": 64,
                "max_format": 64,
                "supported_formats": [64, 64],
                "description": "Bedrock Creative Tabs groups - created in game"
              }
            }
            """;

    private static void put(ZipOutputStream zip, String entryName, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Path groupFile(Identifier id) {
        return root().resolve("groups").resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }

    private static Optional<CreativeGroup> read(Path file) {
        try {
            DataResult<CreativeGroup> result = CreativeGroup.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
            return result.result();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static String key(Identifier id) {
        return id.toLanguageKey("creative_group");
    }

    private static String readLang(String locale, Identifier id) {
        JsonObject table = langTable(locale);
        JsonElement value = table == null ? null : table.get(key(id));
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    /** A null name removes the entry, which is how a deleted group stops naming itself. */
    private static void writeLang(String locale, Identifier id, String name) {
        Path file = root().resolve("lang").resolve(locale + ".json");
        try {
            JsonObject table = langTable(locale);
            if (table == null) table = new JsonObject();
            if (name == null) table.remove(key(id));
            else table.addProperty(key(id), name);
            write(file, GSON.create().toJson(table) + "\n");
        } catch (Exception exception) {
            BedrockCreativeTabs.LOGGER.warn("BCT editor: could not write {}", file, exception);
        }
    }

    private static JsonObject langTable(String locale) {
        Path file = root().resolve("lang").resolve(locale + ".json");
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private BctGroupStore() {
    }
}
