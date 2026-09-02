package dev.practicedebt.mirror;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A mirrored Codeforces problem.
 *
 * <p>Identity is upstream's: {@code (contestId, index)}. Nothing here is author-specific.
 */
public record Problem(
        int contestId,
        String index,
        String name,
        String type,
        Integer rating,
        BigDecimal points,
        List<String> tags,
        Integer solvedCount,
        Instant firstMirroredAt,
        Instant lastMirroredAt) {

    /** How Codeforces itself names a problem, e.g. {@code 1985A}. */
    public String displayId() {
        return contestId + index;
    }
}
