package com.modlint.core.version;

import com.modlint.core.model.ModLoader;
import java.util.List;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Evaluates fabric.mod.json version-range predicates against a version, using fabric-loader's
 * own parser so the semantics match what the loader does in game.
 */
public final class VersionRanges {

    private VersionRanges() {
    }

    /**
     * Evaluates {@code ranges} in the dialect the declaring mod's loader uses: Maven ranges
     * for the Forge family, Fabric predicates otherwise.
     */
    public static boolean satisfies(ModLoader dialect, String version, List<String> ranges) {
        return dialect.isForgeFamily()
                ? MavenVersionRanges.satisfies(version, ranges)
                : satisfies(version, ranges);
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

    /**
     * True when {@code candidate} parses as a newer version than {@code current}, using
     * fabric-loader's comparison. When either side fails Fabric's parser (Forge-style
     * versions often do), Maven's always-parsing comparison decides instead.
     */
    public static boolean isNewer(String candidate, String current) {
        try {
            return Version.parse(candidate).compareTo(Version.parse(current)) > 0;
        } catch (VersionParsingException e) {
            return new ComparableVersion(candidate).compareTo(new ComparableVersion(current)) > 0;
        }
    }
}
