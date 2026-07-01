package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.model.ModInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarModReaderTest {

    private final JarModReader reader = new JarModReader();
    private final FabricModMetadataParser parser = new FabricModMetadataParser();

    @Test
    void readsAndParsesMetadataFromJar(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("examplemod.jar");
        writeJar(jar, "fabric.mod.json", """
            { "schemaVersion": 1, "id": "examplemod", "version": "1.2.3", "name": "Example Mod" }
            """);

        ModInfo mod = parser.parse(reader.readFabricMetadata(jar));

        assertEquals("examplemod", mod.id());
        assertEquals("1.2.3", mod.version());
        assertEquals("Example Mod", mod.name());
    }

    @Test
    void failsWhenJarHasNoFabricMetadata(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("notamod.jar");
        writeJar(jar, "some/other/file.txt", "not metadata");
        assertThrows(IOException.class, () -> reader.readFabricMetadata(jar));
    }

    private static void writeJar(Path jar, String entryName, String content) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
