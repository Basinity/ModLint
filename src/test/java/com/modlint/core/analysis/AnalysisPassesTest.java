package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.model.ScannedJar;
import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.testutil.FixtureJars;
import com.modlint.testutil.SyntheticJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/** Each pass against the real-world fixture that seeds exactly its conflict. */
class AnalysisPassesTest {

    private final ModsFolderScanner scanner = new ModsFolderScanner();

    private ModSet scanFixtures(Path dir, String conflictCase, String... modDirs) throws IOException {
        for (String modDir : modDirs) {
            FixtureJars.packJar(FixtureJars.fixture(conflictCase, modDir), dir.resolve(modDir + ".jar"));
        }
        return new ModSet(scanner.scan(dir), Optional.empty());
    }

    @Test
    void missingDependencyFixtureYieldsExactlyTheSodiumFinding(@TempDir Path dir) throws IOException {
        ModSet mods = scanFixtures(dir, "missing-dependency", "iris-1.7.6+mc1.20.1");

        List<Finding> findings = new MissingDependencyPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals("missing-dependency", findings.get(0).type());
        assertEquals(List.of("iris", "sodium"), findings.get(0).mods());
    }

    @Test
    void versionRangeViolationFixtureYieldsTheSodiumFinding(@TempDir Path dir) throws IOException {
        ModSet mods = scanFixtures(dir, "version-range-violation",
                "iris-1.7.6+mc1.20.1", "sodium-fabric-0.6.13+mc1.21.1");

        List<Finding> findings = new VersionRangeViolationPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals("version-range-violation", findings.get(0).type());
        assertEquals(List.of("iris", "sodium"), findings.get(0).mods());
    }

    @Test
    void declaredBreaksFixtureYieldsExactlyTheSodiumIrisFinding(@TempDir Path dir) throws IOException {
        ModSet mods = scanFixtures(dir, "declared-breaks",
                "sodium-fabric-0.8.12+mc1.21.11", "iris-fabric-1.10.4+mc1.21.11");

        List<Finding> findings = new DeclaredIncompatibilityPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals("declared-incompatibility", findings.get(0).type());
        assertEquals(Severity.HIGH, findings.get(0).severity());
        assertEquals(List.of("sodium", "iris"), findings.get(0).mods());
    }

    @Test
    void wrongLoaderFixtureIsFlagged(@TempDir Path dir) throws IOException {
        ModSet mods = scanFixtures(dir, "wrong-loader", "jei-1.20.1-forge-15.20.0.130");

        List<Finding> findings = new WrongLoaderPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals("wrong-loader", findings.get(0).type());
        assertEquals(List.of("jei-1.20.1-forge-15.20.0.130.jar"), findings.get(0).mods());
    }

    @Test
    void wrongLoaderStaysQuietWhenACompatLayerIsInstalled(@TempDir Path dir) throws IOException {
        FixtureJars.packJar(FixtureJars.fixture("wrong-loader", "jei-1.20.1-forge-15.20.0.130"),
                dir.resolve("jei-1.20.1-forge-15.20.0.130.jar"));
        SyntheticJars.writeFabricJar(dir.resolve("kilt.jar"), "kilt", "2.0.0");
        ModSet mods = new ModSet(scanner.scan(dir), Optional.empty());

        assertTrue(new WrongLoaderPass().analyze(mods).isEmpty());
    }

    @Test
    void duplicateTopLevelIdsAreFlagged(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJar(dir.resolve("somemod-1.0.0.jar"), "somemod", "1.0.0");
        SyntheticJars.writeFabricJar(dir.resolve("somemod-2.0.0.jar"), "somemod", "2.0.0");
        ModSet mods = new ModSet(scanner.scan(dir), Optional.empty());

        List<Finding> findings = new DuplicateModIdPass().analyze(mods);

        assertEquals(1, findings.size());
        assertEquals("duplicate-mod-id", findings.get(0).type());
        assertEquals(List.of("somemod-1.0.0.jar", "somemod-2.0.0.jar"), findings.get(0).mods());
    }

    @Test
    void minecraftRangeIsCheckedWhenGameVersionIsKnown(@TempDir Path dir) throws IOException {
        // iris 1.7.6 pins minecraft to 1.20.1; sodium provides its 0.5.x dependency.
        scanFixtures(dir, "version-range-violation", "iris-1.7.6+mc1.20.1");
        ModSet wrongGame = new ModSet(scanner.scan(dir), Optional.of("1.21.1"));
        ModSet rightGame = new ModSet(scanner.scan(dir), Optional.of("1.20.1"));

        List<Finding> findings = new VersionRangeViolationPass().analyze(wrongGame);
        assertEquals(1, findings.stream().filter(f -> f.mods().contains("minecraft")).count());
        assertTrue(new VersionRangeViolationPass().analyze(rightGame).stream()
                .noneMatch(f -> f.mods().contains("minecraft")));
    }
}
