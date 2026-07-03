package com.modlint.core.parse;

import com.modlint.core.model.ModLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** Reads mod metadata out of a mod jar. */
public final class JarModReader {

    private static final String FABRIC_METADATA_ENTRY = "fabric.mod.json";

    /** Metadata entry that marks a jar as targeting a loader, keyed by entry path. */
    private static final Map<String, ModLoader> LOADER_METADATA_ENTRIES = loaderMetadataEntries();

    private static Map<String, ModLoader> loaderMetadataEntries() {
        Map<String, ModLoader> entries = new LinkedHashMap<>();
        entries.put(FABRIC_METADATA_ENTRY, ModLoader.FABRIC);
        entries.put("quilt.mod.json", ModLoader.QUILT);
        entries.put("META-INF/mods.toml", ModLoader.FORGE);
        entries.put("META-INF/neoforge.mods.toml", ModLoader.NEOFORGE);
        return entries;
    }

    /**
     * Returns the loaders the jar carries metadata for. Multi-loader jars return several;
     * a jar with no known mod metadata returns an empty set.
     */
    public Set<ModLoader> detectLoaders(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            EnumSet<ModLoader> loaders = EnumSet.noneOf(ModLoader.class);
            for (Map.Entry<String, ModLoader> entry : LOADER_METADATA_ENTRIES.entrySet()) {
                if (jarFile.getEntry(entry.getKey()) != null) {
                    loaders.add(entry.getValue());
                }
            }
            return loaders;
        }
    }

    /**
     * Returns the UTF-8 contents of the jar's {@code fabric.mod.json}.
     *
     * @throws IOException if the jar cannot be read or carries no Fabric metadata
     */
    public String readFabricMetadata(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry(FABRIC_METADATA_ENTRY);
            if (entry == null) {
                throw new IOException(jar + " has no " + FABRIC_METADATA_ENTRY + " (not a Fabric mod?)");
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
