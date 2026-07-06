package com.modlint.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.modlint.core.analysis.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RulesLoaderTest {

    @Test
    void parsesARuleWithStringAndListRanges() {
        List<Rule> rules = RulesLoader.parse("""
                rules:
                  - id: a-b-clash
                    mods:
                      moda: ">=2.0.0"
                      modb: ["1.x", "2.x"]
                    severity: medium
                    problem: "They clash."
                    fix: "Remove one."
                """);

        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals("a-b-clash", rule.id());
        assertEquals(Map.of("moda", List.of(">=2.0.0"), "modb", List.of("1.x", "2.x")), rule.mods());
        assertEquals(List.of(), rule.absent());
        assertEquals(Severity.MEDIUM, rule.severity());
        assertEquals("They clash.", rule.problem());
        assertEquals("Remove one.", rule.fix());
    }

    @Test
    void parsesAnAbsentList() {
        List<Rule> rules = RulesLoader.parse("""
                rules:
                  - id: needs-companion
                    mods: { moda: "*" }
                    absent: [modc, modd]
                    severity: high
                    problem: "p"
                    fix: "f"
                """);

        assertEquals(List.of("modc", "modd"), rules.get(0).absent());
    }

    @Test
    void rejectsARuleWithoutMods() {
        assertThrows(IllegalArgumentException.class, () -> RulesLoader.parse("""
                rules:
                  - id: broken
                    severity: high
                    problem: "p"
                    fix: "f"
                """));
    }

    @Test
    void rejectsAnUnknownSeverity() {
        assertThrows(IllegalArgumentException.class, () -> RulesLoader.parse("""
                rules:
                  - id: broken
                    mods: { moda: "*" }
                    severity: catastrophic
                    problem: "p"
                    fix: "f"
                """));
    }

    @Test
    void bundledMasterlistLoadsAndIsValid() {
        List<Rule> rules = RulesLoader.loadBundled();
        assertFalse(rules.isEmpty());
        for (Rule rule : rules) {
            assertFalse(rule.mods().isEmpty());
            assertFalse(rule.fix().isBlank());
        }
    }
}
