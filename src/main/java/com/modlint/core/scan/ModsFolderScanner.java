package com.modlint.core.scan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.core.model.AccessWidenerEntry;
import com.modlint.core.model.JarContents;
import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.core.parse.AccessWidenerParser;
import com.modlint.core.parse.FabricModMetadataParser;
import com.modlint.core.parse.ForgeModMetadataParser;
import com.modlint.core.parse.JarModReader;
import com.modlint.core.parse.MixinScanner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/** Scans a mods folder into one {@link ScannedJar} per jar file, ignoring everything else. */
public final class ModsFolderScanner {

    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
    private static final String JARJAR_METADATA_ENTRY = "META-INF/jarjar/metadata.json";
    private static final String FORGE_METADATA_ENTRY = "META-INF/mods.toml";
    private static final String NEOFORGE_METADATA_ENTRY = "META-INF/neoforge.mods.toml";

    private final JarModReader reader;
    private final FabricModMetadataParser fabricParser;
    private final ForgeModMetadataParser forgeParser = new ForgeModMetadataParser();
    private final MixinScanner mixinScanner;
    private final AccessWidenerParser accessWidenerParser = new AccessWidenerParser();

    public ModsFolderScanner() {
        this(new JarModReader(), new FabricModMetadataParser());
    }

    public ModsFolderScanner(JarModReader reader, FabricModMetadataParser fabricParser) {
        this.reader = reader;
        this.fabricParser = fabricParser;
        this.mixinScanner = new MixinScanner(reader);
    }

    /**
     * Scans every {@code .jar} directly in {@code modsFolder}, sorted by file name. Jars
     * also have their nested jar-in-jar mods collected, so provided ids are complete.
     *
     * @throws IOException if the folder or a jar cannot be read, or a jar's mod metadata is invalid
     */
    public List<ScannedJar> scan(Path modsFolder) throws IOException {
        try (Stream<Path> files = Files.list(modsFolder)) {
            List<ScannedJar> jars = new ArrayList<>();
            for (Path jar : files.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                jars.add(scanJar(jar));
            }
            return List.copyOf(jars);
        }
    }

    private ScannedJar scanJar(Path jar) throws IOException {
        Set<ModLoader> loaders = EnumSet.noneOf(ModLoader.class);
        loaders.addAll(reader.detectLoaders(jar));
        Optional<Manifest> manifest = readManifest(entry -> reader.readEntry(jar, entry));
        List<String> entries = reader.listEntries(jar);
        List<ModInfo> mods = new ArrayList<>();
        List<ModInfo> nestedMods = new ArrayList<>();
        Set<String> mixinConfigs = new LinkedHashSet<>(manifestMixinConfigs(manifest));
        Set<String> nestedPaths = new LinkedHashSet<>();
        if (loaders.contains(ModLoader.FABRIC)) {
            try {
                ModInfo mod = fabricParser.parse(reader.readFabricMetadata(jar));
                mods.add(mod);
                mixinConfigs.addAll(mod.mixinConfigs());
                nestedPaths.addAll(mod.nestedJars());
            } catch (RuntimeException e) {
                throw new IOException(jar + " has invalid fabric.mod.json: " + e.getMessage(), e);
            }
        }
        for (ModLoader forgeLoader : List.of(ModLoader.FORGE, ModLoader.NEOFORGE)) {
            if (!loaders.contains(forgeLoader)) {
                continue;
            }
            String entry = forgeLoader == ModLoader.FORGE ? FORGE_METADATA_ENTRY : NEOFORGE_METADATA_ENTRY;
            try {
                String toml = new String(reader.readEntry(jar, entry).orElseThrow(), StandardCharsets.UTF_8);
                ForgeModMetadataParser.Parsed parsed =
                        forgeParser.parse(toml, forgeLoader, implementationVersion(manifest));
                mods.addAll(parsed.mods());
                mixinConfigs.addAll(parsed.mixinConfigs());
            } catch (RuntimeException e) {
                // Multiloader jars ship broken mods.toml files in the wild; the flavor just
                // doesn't count, the same as if the metadata entry weren't there.
                loaders.remove(forgeLoader);
            }
        }
        reader.readEntry(jar, JARJAR_METADATA_ENTRY).ifPresent(metadata ->
                nestedPaths.addAll(jarjarPaths(metadata)));
        // A jar with no mod metadata and no declared nesting can still be a carrier whose
        // loader plugin locates bundled jars itself (e.g. Sinytra Connector); those bundled
        // mods are really present, so their ids count.
        if (mods.isEmpty() && nestedPaths.isEmpty()) {
            entries.stream().filter(ModsFolderScanner::looksNested).forEach(nestedPaths::add);
        }
        for (String nestedPath : nestedPaths) {
            reader.readEntry(jar, nestedPath).ifPresent(bytes -> collectNested(bytes, nestedMods, 1));
        }
        JarContents contents = mods.isEmpty() ? JarContents.empty()
                : readContents(jar, entries, mods, List.copyOf(mixinConfigs));
        return new ScannedJar(jar, Set.copyOf(loaders), List.copyOf(mods), List.copyOf(nestedMods), contents);
    }

    private static boolean looksNested(String entry) {
        return (entry.startsWith("META-INF/jarjar/") || entry.startsWith("META-INF/jars/"))
                && entry.endsWith(".jar");
    }

    /** Paths under these prefixes are merged by the game rather than overridden, so they never collide. */
    private static boolean mergedByDesign(String path) {
        return path.contains("/lang/") || (path.startsWith("data/") && path.contains("/tags/"))
                // Forge/NeoForge read every mod's global loot modifier index and merge them.
                || path.equals("data/forge/loot_modifiers/global_loot_modifiers.json")
                || path.equals("data/neoforge/loot_modifiers/global_loot_modifiers.json");
    }

    private JarContents readContents(Path jar, List<String> entries, List<ModInfo> mods,
                                     List<String> mixinConfigs) throws IOException {
        List<String> resourcePaths = entries.stream()
                .filter(entry -> entry.startsWith("assets/") || entry.startsWith("data/"))
                .filter(entry -> !mergedByDesign(entry))
                .toList();
        List<AccessWidenerEntry> accessWideners = List.of();
        for (ModInfo mod : mods) {
            if (mod.accessWidener() == null) {
                continue;
            }
            Optional<byte[]> widener = reader.readEntry(jar, mod.accessWidener());
            if (widener.isPresent()) {
                accessWideners = accessWidenerParser.parse(new String(widener.get(), StandardCharsets.UTF_8));
                break;
            }
        }
        return new JarContents(mixinScanner.scan(jar, mixinConfigs), resourcePaths, accessWideners);
    }

    /** Real jar-in-jar chains are this shallow; deeper means a crafted jar (e.g. nesting itself). */
    private static final int MAX_NESTING_DEPTH = 5;

    /** Collects the in-memory jar's mods and recurses into its own nested jars. */
    private void collectNested(byte[] jarBytes, List<ModInfo> out, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            return;
        }
        try {
            Set<String> nestedPaths = new LinkedHashSet<>();
            Optional<String> fabricMetadata = reader.readFabricMetadata(jarBytes);
            if (fabricMetadata.isPresent()) {
                ModInfo mod = fabricParser.parse(fabricMetadata.get());
                out.add(mod);
                nestedPaths.addAll(mod.nestedJars());
            }
            // Every flavor is collected (a multiloader nested jar must count under either
            // target); the analysis filters by the loaders the target actually loads.
            for (ModLoader forgeLoader : List.of(ModLoader.NEOFORGE, ModLoader.FORGE)) {
                String entry = forgeLoader == ModLoader.FORGE ? FORGE_METADATA_ENTRY : NEOFORGE_METADATA_ENTRY;
                Optional<byte[]> toml = reader.readEntry(jarBytes, entry);
                if (toml.isEmpty()) {
                    continue;
                }
                Optional<Manifest> manifest = readManifest(name -> reader.readEntry(jarBytes, name));
                try {
                    out.addAll(forgeParser.parse(new String(toml.get(), StandardCharsets.UTF_8),
                            forgeLoader, implementationVersion(manifest)).mods());
                    break; // One Forge-family flavor per nested jar, so its ids count once.
                } catch (RuntimeException e) {
                    // A malformed flavor just doesn't count, same as at the top level.
                }
            }
            reader.readEntry(jarBytes, JARJAR_METADATA_ENTRY).ifPresent(metadata ->
                    nestedPaths.addAll(jarjarPaths(metadata)));
            for (String nestedPath : nestedPaths) {
                reader.readEntry(jarBytes, nestedPath).ifPresent(bytes -> collectNested(bytes, out, depth + 1));
            }
        } catch (IOException | RuntimeException e) {
            // A broken nested jar never fails the scan; its ids simply don't count as present.
        }
    }

    /** The nested jar paths listed in Forge's {@code META-INF/jarjar/metadata.json}. */
    private static List<String> jarjarPaths(byte[] metadataJson) {
        try {
            JsonObject root = JsonParser
                    .parseString(new String(metadataJson, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("jars")) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (JsonElement entry : root.getAsJsonArray("jars")) {
                JsonObject jar = entry.getAsJsonObject();
                if (jar.has("path")) {
                    paths.add(jar.get("path").getAsString());
                }
            }
            return List.copyOf(paths);
        } catch (RuntimeException e) {
            return List.of(); // Malformed jarjar metadata never fails the scan.
        }
    }

    private interface EntryReader {
        Optional<byte[]> read(String entryPath) throws IOException;
    }

    private static Optional<Manifest> readManifest(EntryReader entries) throws IOException {
        Optional<byte[]> bytes = entries.read(MANIFEST_ENTRY);
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Manifest(new ByteArrayInputStream(bytes.get())));
        } catch (IOException e) {
            return Optional.empty(); // A malformed manifest never fails the scan.
        }
    }

    /** The manifest's Implementation-Version, what Forge substitutes for {@code ${file.jarVersion}}. */
    private static Optional<String> implementationVersion(Optional<Manifest> manifest) {
        return manifest.map(m -> m.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
    }

    /** The manifest's MixinConfigs attribute, how Forge-family mods register Mixin configs. */
    private static List<String> manifestMixinConfigs(Optional<Manifest> manifest) {
        String value = manifest.map(m -> m.getMainAttributes().getValue("MixinConfigs")).orElse(null);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Stream.of(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
