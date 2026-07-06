package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.modlint.core.rules.Rule;
import com.modlint.core.scan.ModsFolderScanner;
import com.modlint.testutil.SyntheticJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class KnownBadCombinationPassTest {

    private static final Rule RULE = new Rule("a-b-clash",
            Map.of("moda", List.of("*"), "modb", List.of("<2.0.0")),
            Severity.HIGH,
            "moda corrupts modb worlds.",
            "Update modb to 2.0.0 or newer.");

    private ModSet scan(Path dir) throws IOException {
        return new ModSet(new ModsFolderScanner().scan(dir), Optional.empty());
    }

    @Test
    void seededRuleFiresWithItsFixText(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJar(dir.resolve("moda.jar"), "moda", "1.0.0");
        SyntheticJars.writeFabricJar(dir.resolve("modb.jar"), "modb", "1.5.0");

        List<Finding> findings = new KnownBadCombinationPass(List.of(RULE)).analyze(scan(dir));

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals("known-bad-combination", finding.type());
        assertEquals(Severity.HIGH, finding.severity());
        assertTrue(finding.problem().contains("moda corrupts modb worlds."));
        assertTrue(finding.problem().contains("a-b-clash"));
        assertEquals("Update modb to 2.0.0 or newer.", finding.fix());
    }

    @Test
    void ruleStaysQuietWhenAVersionIsOutsideTheRange(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJar(dir.resolve("moda.jar"), "moda", "1.0.0");
        SyntheticJars.writeFabricJar(dir.resolve("modb.jar"), "modb", "2.1.0");

        assertTrue(new KnownBadCombinationPass(List.of(RULE)).analyze(scan(dir)).isEmpty());
    }

    @Test
    void ruleStaysQuietWhenAModIsAbsent(@TempDir Path dir) throws IOException {
        SyntheticJars.writeFabricJar(dir.resolve("moda.jar"), "moda", "1.0.0");

        assertTrue(new KnownBadCombinationPass(List.of(RULE)).analyze(scan(dir)).isEmpty());
    }
}
