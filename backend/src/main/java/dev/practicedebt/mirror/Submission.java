package dev.practicedebt.mirror;

import java.time.Instant;

/**
 * One mirrored submission.
 *
 * <p>{@code handle} is stored lowercase: Codeforces handles are case-insensitive, and the same
 * person typed two ways must not become two people in this table.
 */
public record Submission(
        long id,
        String handle,
        int contestId,
        String problemIndex,
        String problemName,
        long creationTimeSeconds,
        Long relativeTimeSeconds,
        String participantType,
        String verdict,
        String programmingLanguage) {

    public Instant createdAt() {
        return Instant.ofEpochSecond(creationTimeSeconds);
    }

    public boolean accepted() {
        return "OK".equals(verdict);
    }
}
