package com.modlint.core.version;

import java.util.List;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Evaluates Maven version ranges ({@code [1.0,2.0)} and friends) against a version, using
 * Maven's own parser so the semantics match what Forge and NeoForge do in game.
 */
public final class MavenVersionRanges {

    private MavenVersionRanges() {
    }

    /**
     * True when {@code version} satisfies at least one of the {@code ranges}. A blank range
     * means any version, per FML. A range that fails to parse counts as satisfied, so
     * unparseable metadata can never produce a false conflict finding.
     */
    public static boolean satisfies(String version, List<String> ranges) {
        if (ranges.isEmpty()) {
            return true;
        }
        DefaultArtifactVersion parsed = new DefaultArtifactVersion(version);
        boolean anyParsed = false;
        for (String range : ranges) {
            if (range.isBlank()) {
                return true;
            }
            try {
                VersionRange spec = VersionRange.createFromVersionSpec(range);
                // A spec without restrictions ("1.0") is a soft preference, never a constraint.
                if (!spec.hasRestrictions() || spec.containsVersion(parsed)) {
                    return true;
                }
                anyParsed = true;
            } catch (InvalidVersionSpecificationException e) {
                // Skip the unparseable alternative; the others still count.
            }
        }
        return !anyParsed;
    }
}
