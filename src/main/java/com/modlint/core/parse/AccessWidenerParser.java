package com.modlint.core.parse;

import com.modlint.core.model.AccessWidenerEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the access widener text format: a header line, then one widening per line as
 * {@code <access> <class|method|field> <owner> [<name> <descriptor>]}. Comments ({@code #})
 * and blank lines are skipped; v2's {@code transitive-} prefix is stripped.
 */
public final class AccessWidenerParser {

    public List<AccessWidenerEntry> parse(String accessWidenerText) {
        List<AccessWidenerEntry> entries = new ArrayList<>();
        for (String rawLine : accessWidenerText.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty() || line.startsWith("accessWidener")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 3) {
                continue; // Malformed line; never fails the scan.
            }
            String access = parts[0].startsWith("transitive-") ? parts[0].substring("transitive-".length()) : parts[0];
            String member = String.join(" ", List.of(parts).subList(1, parts.length));
            entries.add(new AccessWidenerEntry(access, member));
        }
        return List.copyOf(entries);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }
}
