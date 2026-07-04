package com.modlint.core.version;

import java.util.List;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Evaluates fabric.mod.json version-range predicates against a version, using fabric-loader's
 * own parser so the semantics match what the loader does in game.
 */
public final class VersionRanges {

    private VersionRanges() {
    }

    /**
     * True when {@code version} satisfies at least one of the {@code ranges} (alternatives are OR,
     * per the fabric.mod.json spec). A version or range that fails to parse counts as satisfied,
     * so unparseable metadata can never produce a false conflict finding.
     */
    public static boolean satisfies(String version, List<String> ranges) {
        Version parsed;
        try {
            parsed = Version.parse(version);
        } catch (VersionParsingException e) {
            return true;
        }
        boolean anyParsed = false;
        for (String range : ranges) {
            try {
                if (VersionPredicate.parse(range).test(parsed)) {
                    return true;
                }
                anyParsed = true;
            } catch (VersionParsingException e) {
                // Skip the unparseable alternative; the others still count.
            }
        }
        return !anyParsed;
    }
}
