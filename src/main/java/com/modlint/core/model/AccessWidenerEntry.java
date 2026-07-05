package com.modlint.core.model;

/**
 * One access widener line: the access keyword ({@code accessible}, {@code extendable},
 * {@code mutable}, with any {@code transitive-} prefix stripped) and the widened member,
 * normalized to {@code kind owner [name descriptor]}.
 */
public record AccessWidenerEntry(String access, String member) {
}
