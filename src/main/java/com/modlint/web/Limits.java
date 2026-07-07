package com.modlint.web;

import java.time.Duration;

/**
 * Caps protecting the server from oversized or abusive uploads: how many jars one analysis
 * may contain, how many bytes it may materialize to disk, how long it may run, how many
 * analyses one client gets per window, and how many may run at once.
 */
public record Limits(int maxJars, long maxTotalBytes, long maxJarBytes, Duration analysisTimeout,
                     int analysesPerWindow, Duration window, int concurrentAnalyses) {

    public static Limits defaults() {
        return new Limits(500, 2L * 1024 * 1024 * 1024, 512L * 1024 * 1024,
                Duration.ofSeconds(120), 10, Duration.ofMinutes(10), 2);
    }
}
