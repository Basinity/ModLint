package com.modlint.core.parse;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a {@code META-INF/mods.toml} (Forge) or {@code META-INF/neoforge.mods.toml}
 * (NeoForge) into {@link ModInfo}s. One file can declare several mods; the file-level
 * {@code [[dependencies.<modId>]]} tables are attributed to the mod they name.
 *
 * <p>Only dependencies for both sides count: a CLIENT- or SERVER-only relation may be
 * legitimately unmet in a folder whose side this scan cannot know, so it never becomes
 * a finding. Version ranges stay raw Maven range strings.
 */
public final class ForgeModMetadataParser {

    /** FML's substitute when the manifest carries no Implementation-Version. */
    private static final String NO_JAR_VERSION = "0.0NONE";

    /** The parsed mods plus the file-level {@code [[mixins]]} config entries (NeoForge). */
    public record Parsed(List<ModInfo> mods, List<String> mixinConfigs) {
    }

    /**
     * Parses a mods.toml document. {@code jarVersion} is the jar manifest's
     * Implementation-Version, substituted where a mod declares {@code ${file.jarVersion}}.
     *
     * @throws IllegalArgumentException if the TOML is malformed or a mod entry has no modId
     */
    public Parsed parse(String modsToml, ModLoader loader, Optional<String> jarVersion) {
        Config root;
        try {
            root = new TomlParser().parse(new StringReader(modsToml));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed TOML: " + e.getMessage(), e);
        }
        List<ModInfo> mods = new ArrayList<>();
        for (Config modEntry : configList(root.get("mods"))) {
            mods.add(parseMod(root, modEntry, loader, jarVersion));
        }
        return new Parsed(List.copyOf(mods), mixinConfigs(root));
    }

    private static ModInfo parseMod(Config root, Config modEntry, ModLoader loader, Optional<String> jarVersion) {
        String id = string(modEntry, "modId");
        if (id == null) {
            throw new IllegalArgumentException("a [[mods]] entry is missing its modId");
        }
        String version = string(modEntry, "version");
        if (version == null) {
            version = NO_JAR_VERSION;
        } else if (version.contains("${file.jarVersion}")) {
            version = version.replace("${file.jarVersion}", jarVersion.orElse(NO_JAR_VERSION));
        }
        String name = string(modEntry, "displayName");
        Map<String, List<String>> depends = new LinkedHashMap<>();
        Map<String, List<String>> breaks = new LinkedHashMap<>();
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        Config dependencies = root.get("dependencies") instanceof Config all ? all : null;
        if (dependencies != null && dependencies.get(List.of(id)) != null) {
            for (Config dependency : configList(dependencies.get(List.of(id)))) {
                collectDependency(dependency, depends, breaks, conflicts);
            }
        }
        return new ModInfo(loader, id, version, name == null ? id : name,
                List.of(),
                Collections.unmodifiableMap(depends),
                Collections.unmodifiableMap(breaks),
                Collections.unmodifiableMap(conflicts),
                List.of(), List.of(), null);
    }

    private static void collectDependency(Config dependency, Map<String, List<String>> depends,
                                          Map<String, List<String>> breaks, Map<String, List<String>> conflicts) {
        String depId = string(dependency, "modId");
        if (depId == null || !"BOTH".equalsIgnoreCase(string(dependency, "side", "BOTH"))) {
            return;
        }
        String range = string(dependency, "versionRange", "");
        List<String> ranges = range.isBlank() ? List.of() : List.of(range);
        // Forge marks a hard requirement with mandatory=true; NeoForge with type="required",
        // and its type="incompatible"/"discouraged" match Fabric's breaks/conflicts.
        String type = string(dependency, "type", "");
        Boolean mandatory = dependency.get("mandatory") instanceof Boolean flag ? flag : null;
        if ("incompatible".equalsIgnoreCase(type)) {
            breaks.put(depId, ranges);
        } else if ("discouraged".equalsIgnoreCase(type)) {
            conflicts.put(depId, ranges);
        } else if (Boolean.TRUE.equals(mandatory) || "required".equalsIgnoreCase(type)) {
            depends.put(depId, ranges);
        }
    }

    /** The file-level {@code [[mixins]]} tables' config entries, a NeoForge addition. */
    private static List<String> mixinConfigs(Config root) {
        List<String> configs = new ArrayList<>();
        for (Config mixinEntry : configList(root.get("mixins"))) {
            String config = string(mixinEntry, "config");
            if (config != null) {
                configs.add(config);
            }
        }
        return List.copyOf(configs);
    }

    private static List<Config> configList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Config> configs = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Config config) {
                configs.add(config);
            }
        }
        return configs;
    }

    private static String string(Config config, String key) {
        return config.get(List.of(key)) instanceof String value ? value : null;
    }

    private static String string(Config config, String key, String fallback) {
        String value = string(config, key);
        return value == null ? fallback : value;
    }
}
