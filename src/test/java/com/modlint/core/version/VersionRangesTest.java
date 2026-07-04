package com.modlint.core.version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VersionRangesTest {

    @Test
    void matchesWildcardMinorRange() {
        assertTrue(VersionRanges.satisfies("0.5.13+mc1.20.1", List.of("0.5.x")));
        assertFalse(VersionRanges.satisfies("0.6.13+mc1.21.1", List.of("0.5.x")));
    }

    @Test
    void matchesComparatorRange() {
        assertTrue(VersionRanges.satisfies("1.20.1", List.of(">=1.20 <1.21")));
        assertFalse(VersionRanges.satisfies("1.21.1", List.of(">=1.20 <1.21")));
        assertTrue(VersionRanges.satisfies("1.10.4+mc1.21.11", List.of("<=1.10.6")));
    }

    @Test
    void matchesAnyVersionWithStar() {
        assertTrue(VersionRanges.satisfies("whatever-9.9", List.of("*")));
    }

    @Test
    void alternativesAreOr() {
        assertTrue(VersionRanges.satisfies("0.6.13", List.of("0.5.x", "0.6.x")));
        assertFalse(VersionRanges.satisfies("0.7.0", List.of("0.5.x", "0.6.x")));
    }

    @Test
    void nonSemverVersionMatchesOnlyWildcardOrExactEquality() {
        // fabric-loader falls back to a plain string version here, matching the loader in game.
        assertTrue(VersionRanges.satisfies("${version}", List.of("*")));
        assertFalse(VersionRanges.satisfies("${version}", List.of(">=2.0.0")));
    }

    @Test
    void unparseableRangeCountsAsSatisfied() {
        assertTrue(VersionRanges.satisfies("1.0.0", List.of(">>>nonsense<<<")));
    }
}
