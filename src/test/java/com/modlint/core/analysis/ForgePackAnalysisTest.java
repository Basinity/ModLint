package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.model.ModLoader;
import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.testutil.SyntheticJars;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/** The metadata passes against synthetic Forge and NeoForge packs. */
class ForgePackAnalysisTest {

    private final ModsFolderScanner scanner = new ModsFolderScanner();

    private ModSet scan(Path dir, Optional<String> mcVersion) throws IOException {
        return new ModSet(scanner.scan(dir), mcVersion);
    }

    @Test
    void missingMandatoryDependencyIsFlagged(@TempDir Path dir) throws IOException {
        SyntheticJars.writeTomlJar(dir.resolve("alexsmobs.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("alexsmobs", "1.22.9",
                        SyntheticJars.forgeDependency("alexsmobs", "citadel", "[2.6.0,)")));
        ModSet mods = scan(dir, Optional.empty());

        List<Finding> findings = new MissingDependencyPass().analyze(mods);

        assertEquals(ModLoader.FORGE, mods.targetLoader());
        assertEquals(1, findings.size());
        assertEquals("missing-dependency", findings.get(0).type());
        assertEquals(List.of("alexsmobs", "citadel"), findings.get(0).mods());
    }

    @Test
    void platformAndSatisfiedDependenciesStayQuiet(@TempDir Path dir) throws IOException {
        SyntheticJars.writeTomlJar(dir.resolve("alexsmobs.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("alexsmobs", "1.22.9",
                        SyntheticJars.forgeDependency("alexsmobs", "citadel", "[2.6.0,)")
                                + SyntheticJars.forgeDependency("alexsmobs", "forge", "[47.1.0,)")
                                + SyntheticJars.forgeDependency("alexsmobs", "minecraft", "[1.20,1.21)")));
        SyntheticJars.writeTomlJar(dir.resolve("citadel.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("citadel", "2.6.3", ""));
        ModSet mods = scan(dir, Optional.of("1.20.1"));

        assertTrue(new MissingDependencyPass().analyze(mods).isEmpty());
        assertTrue(new VersionRangeViolationPass().analyze(mods).isEmpty());
    }

    @Test
    void mavenRangeViolationIsFlagged(@TempDir Path dir) throws IOException {
        SyntheticJars.writeTomlJar(dir.resolve("alexsmobs.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("alexsmobs", "1.22.9",
                        SyntheticJars.forgeDependency("alexsmobs", "citadel", "[2.6.0,)")));
        SyntheticJars.writeTomlJar(dir.resolve("citadel.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("citadel", "2.5.4", ""));
        ModSet mods = scan(dir, Optional.empty());

        List<Finding> findings = new VersionRangeViolationPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals(List.of("alexsmobs", "citadel"), findings.get(0).mods());
        assertTrue(findings.get(0).problem().contains("2.5.4"));
    }

    @Test
    void minecraftRangeIsCheckedAgainstTheGameVersion(@TempDir Path dir) throws IOException {
        SyntheticJars.writeTomlJar(dir.resolve("somemod.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("somemod", "1.0.0",
                        SyntheticJars.forgeDependency("somemod", "minecraft", "[1.20,1.21)")));

        List<Finding> wrongGame = new VersionRangeViolationPass().analyze(scan(dir, Optional.of("1.21.1")));
        assertEquals(1, wrongGame.size());
        assertEquals(List.of("somemod", "minecraft"), wrongGame.get(0).mods());
        assertTrue(new VersionRangeViolationPass().analyze(scan(dir, Optional.of("1.20.1"))).isEmpty());
    }

    @Test
    void jarJarNestedModSatisfiesADependency(@TempDir Path dir) throws IOException {
        SyntheticJars.writeForgeJarWithJarJar(dir.resolve("bundler.jar"), "bundler", "1.0.0",
                "innerlib", "3.2.0");
        SyntheticJars.writeTomlJar(dir.resolve("user.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("usermod", "1.0.0",
                        SyntheticJars.forgeDependency("usermod", "innerlib", "[3.0,)")));
        ModSet mods = scan(dir, Optional.empty());

        assertTrue(new MissingDependencyPass().analyze(mods).isEmpty());
        assertTrue(new VersionRangeViolationPass().analyze(mods).isEmpty());
    }

    @Test
    void neoForgeTargetAcceptsForgeJarsOnlyThrough120(@TempDir Path dir) throws IOException {
        SyntheticJars.writeTomlJar(dir.resolve("neomod.jar"), "META-INF/neoforge.mods.toml",
                SyntheticJars.forgeToml("neomod", "1.0.0", ""));
        SyntheticJars.writeTomlJar(dir.resolve("oldmod.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("oldmod", "1.0.0", ""));

        ModSet transitional = new ModSet(scanner.scan(dir), Optional.of("1.20.1"), ModLoader.NEOFORGE);
        assertTrue(new WrongLoaderPass().analyze(transitional).isEmpty());
        assertEquals(2, transitional.topLevelMods().size());

        ModSet modern = new ModSet(scanner.scan(dir), Optional.of("1.21.1"), ModLoader.NEOFORGE);
        List<Finding> findings = new WrongLoaderPass().analyze(modern);
        assertEquals(1, findings.size());
        assertEquals(List.of("oldmod.jar"), findings.get(0).mods());
        assertEquals(1, modern.topLevelMods().size());
    }

    @Test
    void dualForgeAndNeoForgeMetadataCountsOnce(@TempDir Path dir) throws IOException {
        // One jar carrying both metadata files for the same mod, the common transition-era shape.
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(dir.resolve("dual.jar")))) {
            for (String entry : List.of("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
                out.putNextEntry(new JarEntry(entry));
                out.write(SyntheticJars.forgeToml("dualmod", "1.0.0", "").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        ModSet mods = new ModSet(scanner.scan(dir), Optional.of("1.20.1"), ModLoader.NEOFORGE);

        assertEquals(1, mods.topLevelMods().size());
        assertTrue(new DuplicateModIdPass().analyze(mods).isEmpty());
    }

    @Test
    void fabricJarInAForgePackIsQuietWithConnectorInstalled(@TempDir Path dir) throws IOException {
        SyntheticJars.writeForgeJar(dir.resolve("a.jar"), "forgemoda");
        SyntheticJars.writeForgeJar(dir.resolve("b.jar"), "forgemodb");
        SyntheticJars.writeTomlJar(dir.resolve("connector.jar"), "META-INF/mods.toml",
                SyntheticJars.forgeToml("connector", "1.0.0", ""));
        SyntheticJars.writeFabricJar(dir.resolve("fabricmod.jar"), "fabricmod", "1.0.0");
        ModSet mods = scan(dir, Optional.empty());

        assertEquals(ModLoader.FORGE, mods.targetLoader());
        assertTrue(new WrongLoaderPass().analyze(mods).isEmpty());
    }
}
