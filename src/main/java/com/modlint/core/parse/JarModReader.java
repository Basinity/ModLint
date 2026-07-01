package com.modlint.core.parse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** Reads the raw {@code fabric.mod.json} metadata out of a mod jar. */
public final class JarModReader {

    private static final String FABRIC_METADATA_ENTRY = "fabric.mod.json";

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
