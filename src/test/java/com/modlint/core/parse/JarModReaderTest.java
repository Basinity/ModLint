package com.modlint.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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

    @Test
    void detectsSingleLoader(@TempDir Path dir) throws IOException {
        Path fabricJar = dir.resolve("fabricmod.jar");
        writeJar(fabricJar, "fabric.mod.json", "{}");
        Path forgeJar = dir.resolve("forgemod.jar");
        writeJar(forgeJar, "META-INF/mods.toml", "modLoader=\"javafml\"");

        assertEquals(Set.of(ModLoader.FABRIC), reader.detectLoaders(fabricJar));
        assertEquals(Set.of(ModLoader.FORGE), reader.detectLoaders(forgeJar));
    }

    @Test
    void detectsMultiLoaderJar(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("multiloader.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : new String[] {"fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml"}) {
                out.putNextEntry(new JarEntry(entry));
                out.write("{}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        assertEquals(Set.of(ModLoader.FABRIC, ModLoader.FORGE, ModLoader.NEOFORGE), reader.detectLoaders(jar));
    }

    @Test
    void detectsNoLoaderInPlainJar(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("library.jar");
        writeJar(jar, "com/example/Library.class", "");
        assertEquals(Set.of(), reader.detectLoaders(jar));
    }

    @Test
    void refusesEntriesDecompressingPastTheCap(@TempDir Path dir) throws IOException {
        JarModReader cappedReader = new JarModReader(1024);
        Path jar = dir.resolve("bomb.jar");
        writeJar(jar, "fabric.mod.json", "x".repeat(4096));

        assertThrows(IOException.class, () -> cappedReader.readFabricMetadata(jar));
        assertThrows(IOException.class, () -> cappedReader.readEntry(jar, "fabric.mod.json"));
        assertThrows(IOException.class,
                () -> cappedReader.readEntry(Files.readAllBytes(jar), "fabric.mod.json"));
    }

    private static void writeJar(Path jar, String entryName, String content) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
