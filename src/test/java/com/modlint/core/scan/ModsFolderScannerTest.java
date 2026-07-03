package com.modlint.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.model.ModInfo;
import com.modlint.core.model.ModLoader;
import com.modlint.core.model.ScannedJar;
import com.modlint.testutil.FixtureJars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModsFolderScannerTest {

    private final ModsFolderScanner scanner = new ModsFolderScanner();

    @Test
    void scansFabricAndForeignJarsAndIgnoresOtherFiles(@TempDir Path dir) throws IOException {
        FixtureJars.packJar(FixtureJars.fixture("missing-dependency", "iris-1.7.6+mc1.20.1"),
                dir.resolve("iris-1.7.6+mc1.20.1.jar"));
        FixtureJars.packJar(FixtureJars.fixture("wrong-loader", "jei-1.20.1-forge-15.20.0.130"),
                dir.resolve("jei-1.20.1-forge-15.20.0.130.jar"));
        Files.writeString(dir.resolve("notes.txt"), "not a mod");

        List<ScannedJar> jars = scanner.scan(dir);

        assertEquals(2, jars.size());

        ScannedJar iris = jars.get(0);
        assertEquals(Set.of(ModLoader.FABRIC), iris.loaders());
        ModInfo irisMod = iris.fabricMod().orElseThrow();
        assertEquals("iris", irisMod.id());
        assertEquals(List.of("0.5.x"), irisMod.depends().get("sodium"));

        ScannedJar jei = jars.get(1);
        assertEquals(Set.of(ModLoader.FORGE), jei.loaders());
        assertTrue(jei.fabricMod().isEmpty());
    }
}
