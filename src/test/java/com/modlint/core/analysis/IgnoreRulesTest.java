package com.modlint.core.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IgnoreRulesTest {

    private final Finding wrongLoader = new Finding("wrong-loader", Severity.HIGH,
            List.of("jei-forge.jar"), "problem", "fix");
    private final Finding missingSodium = new Finding("missing-dependency", Severity.HIGH,
            List.of("iris", "sodium"), "problem", "fix");

    @Test
    void typeOnlyRuleSuppressesTheWholeType() {
        IgnoreRules rules = IgnoreRules.parse(List.of("wrong-loader"));
        assertTrue(rules.ignores(wrongLoader));
        assertFalse(rules.ignores(missingSodium));
    }

    @Test
    void typeWithModRuleSuppressesOnlyThatMod() {
        IgnoreRules rules = IgnoreRules.parse(List.of("missing-dependency sodium"));
        assertTrue(rules.ignores(missingSodium));
        assertFalse(rules.ignores(new Finding("missing-dependency", Severity.HIGH,
                List.of("iris", "othermod"), "problem", "fix")));
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        IgnoreRules rules = IgnoreRules.parse(List.of("# a comment", "", "   ", "wrong-loader"));
        assertTrue(rules.ignores(wrongLoader));
    }

    @Test
    void noRulesIgnoresNothing() {
        assertFalse(IgnoreRules.none().ignores(wrongLoader));
    }
}
