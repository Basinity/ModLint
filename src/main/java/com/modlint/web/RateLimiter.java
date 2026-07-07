package com.modlint.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Sliding-window request limiter keyed by client, for the analyze endpoint. */
final class RateLimiter {

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    synchronized boolean allow(String key) {
        Instant cutoff = Instant.now().minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= limit) {
            return false;
        }
        timestamps.addLast(Instant.now());
        hits.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                || entry.getValue().peekLast().isBefore(cutoff));
        return true;
    }
}
