package com.modlint.core.analysis;

/**
 * How likely a finding is to break the game, from certain breakage down to surprise.
 * {@code POTENTIAL} marks heuristic findings: high impact if real, but the overlap alone
 * does not prove breakage, so they group separately and are prime suppression candidates.
 */
public enum Severity {
    HIGH,
    MEDIUM,
    LOW,
    POTENTIAL
}
