package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.testutil.FixtureJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/**
 * The deliberately broken pack from the roadmap: every fixture jar in one folder must make
 * every metadata pass fire at least once.
 */
class BrokenPackIntegrationTest {

    @Test
    void everyPassFiresOnThePackOfAllFixtures(@TempDir Path dir) throws IOException {
        pack(dir, "missing-dependency", "iris-1.7.6+mc1.20.1");
        pack(dir, "version-range-violation", "sodium-fabric-0.6.13+mc1.21.1");
        pack(dir, "declared-breaks", "sodium-fabric-0.8.12+mc1.21.11");
        pack(dir, "declared-breaks", "iris-fabric-1.10.4+mc1.21.11");
        pack(dir, "wrong-loader", "jei-1.20.1-forge-15.20.0.130");

        ModSet mods = new ModSet(new ModsFolderScanner().scan(dir), Optional.empty());
        List<Finding> findings = new Analyzer().analyze(mods);

        Set<String> types = findings.stream().map(Finding::type).collect(Collectors.toSet());
        assertEquals(Set.of("missing-dependency", "version-range-violation", "duplicate-mod-id",
                "declared-incompatibility", "wrong-loader"), types);

        // Both sodiums and both irises are top-level here, so both duplicate findings appear.
        assertEquals(2, findings.stream().filter(f -> f.type().equals("duplicate-mod-id")).count());
        // Findings come back most severe first.
        assertTrue(findings.stream().map(Finding::severity).toList()
                .equals(findings.stream().map(Finding::severity).sorted().toList()));
    }

    private static void pack(Path dir, String conflictCase, String modDir) throws IOException {
        FixtureJars.packJar(FixtureJars.fixture(conflictCase, modDir), dir.resolve(modDir + ".jar"));
    }
}
