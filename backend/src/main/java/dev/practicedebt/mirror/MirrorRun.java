package dev.practicedebt.mirror;

import java.time.Instant;

/** One recorded attempt to refresh a mirrored data source. */
public record MirrorRun(
        long id,
        String source,
        Status status,
        Instant startedAt,
        Instant finishedAt,
        Integer itemCount,
        String error) {

    public enum Status {
        RUNNING, SUCCESS, FAILED
    }
}
