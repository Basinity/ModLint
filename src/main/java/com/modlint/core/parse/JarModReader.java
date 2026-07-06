package com.modlint.core.parse;

import com.modlint.core.model.ModLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads mod metadata out of a mod jar. */
public final class JarModReader {

    private static final String FABRIC_METADATA_ENTRY = "fabric.mod.json";

    /** No legitimate metadata or nested-jar entry comes close; past this it's a zip bomb. */
    private static final int MAX_ENTRY_BYTES = 128 * 1024 * 1024;

    private final int maxEntryBytes;

    public JarModReader() {
        this(MAX_ENTRY_BYTES);
    }

    JarModReader(int maxEntryBytes) {
        this.maxEntryBytes = maxEntryBytes;
    }

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
                return new String(readCapped(in, FABRIC_METADATA_ENTRY), StandardCharsets.UTF_8);
            }
        }
    }

    /** Returns the entry names of a jar on disk, directories excluded. */
    public List<String> listEntries(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            for (var entries = jarFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
            return List.copyOf(names);
        }
    }

    /** Returns the bytes of one entry of a jar on disk, or empty if the jar has no such entry. */
    public Optional<byte[]> readEntry(Path jar, String entryPath) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry(entryPath);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return Optional.of(readCapped(in, entryPath));
            }
        }
    }

    /**
     * Returns the bytes of one entry of an in-memory jar (a nested jar-in-jar), or empty if
     * the jar has no such entry.
     */
    public Optional<byte[]> readEntry(byte[] jarBytes, String entryPath) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals(entryPath)) {
                    return Optional.of(readCapped(in, entryPath));
                }
            }
        }
        return Optional.empty();
    }

    /** Returns the UTF-8 {@code fabric.mod.json} of an in-memory jar, or empty if it has none. */
    public Optional<String> readFabricMetadata(byte[] jarBytes) throws IOException {
        return readEntry(jarBytes, FABRIC_METADATA_ENTRY)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private byte[] readCapped(InputStream in, String entryName) throws IOException {
        byte[] bytes = in.readNBytes(maxEntryBytes + 1);
        if (bytes.length > maxEntryBytes) {
            throw new IOException(entryName + " decompresses past " + maxEntryBytes + " bytes; refusing to read it");
        }
        return bytes;
    }
}
