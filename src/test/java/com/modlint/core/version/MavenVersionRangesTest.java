package com.modlint.core.version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MavenVersionRangesTest {

    @Test
    void boundedAndUnboundedRangesMatchLikeMaven() {
        assertTrue(MavenVersionRanges.satisfies("1.20.1", List.of("[1.20,1.21)")));
        assertFalse(MavenVersionRanges.satisfies("1.21", List.of("[1.20,1.21)")));
        assertTrue(MavenVersionRanges.satisfies("47.4.10", List.of("[47.1.0,)")));
        assertFalse(MavenVersionRanges.satisfies("46.9.9", List.of("[47.1.0,)")));
        assertTrue(MavenVersionRanges.satisfies("2.6.3", List.of("[2.6.0,)")));
    }

    @Test
    void exactPinMatchesOnlyThatVersion() {
        assertTrue(MavenVersionRanges.satisfies("1.20.1", List.of("[1.20.1]")));
        assertFalse(MavenVersionRanges.satisfies("1.20.2", List.of("[1.20.1]")));
    }

    @Test
    void blankOrAbsentRangeMeansAnyVersion() {
        assertTrue(MavenVersionRanges.satisfies("1.0.0", List.of()));
        assertTrue(MavenVersionRanges.satisfies("1.0.0", List.of("")));
    }

    @Test
    void softVersionPreferenceIsNeverAConstraint() {
        // A bare version without brackets is Maven's "recommended version", not a requirement.
        assertTrue(MavenVersionRanges.satisfies("9.9.9", List.of("1.0")));
    }

    @Test
    void unparseableRangeCountsAsSatisfied() {
        assertTrue(MavenVersionRanges.satisfies("1.0.0", List.of("[1.0")));
    }
}
