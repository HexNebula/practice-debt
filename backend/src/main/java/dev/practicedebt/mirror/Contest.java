package dev.practicedebt.mirror;

import java.time.Instant;

/** A mirrored Codeforces contest. */
public record Contest(
        int id,
        String name,
        String type,
        String phase,
        Boolean frozen,
        Long durationSeconds,
        Long startTimeSeconds) {

    public Instant startedAt() {
        return startTimeSeconds == null ? null : Instant.ofEpochSecond(startTimeSeconds);
    }
}
