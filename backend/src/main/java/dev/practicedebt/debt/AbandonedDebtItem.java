package dev.practicedebt.debt;

import java.time.Instant;
import java.util.List;

/**
 * One problem the author owes: attempted live in a contest, never solved since, in any mode.
 *
 * <p>{@code reason} is not decoration. It is what makes the ranking inspectable, and the spec is
 * explicit that an item which cannot explain itself does not ship.
 */
public record AbandonedDebtItem(
        int contestId,
        String problemIndex,
        String problemName,
        Integer rating,
        List<String> tags,
        String contestName,
        Instant contestStartedAt,
        int liveAttempts,
        /** Live attempts that were actually rejected; compile errors do not count on Codeforces. */
        int liveWrongAttempts,
        /** Seconds from contest start of the last live attempt. Null if upstream did not report it. */
        Long lastLiveRelativeSeconds,
        Instant firstAttemptedAt,
        Instant lastAttemptedAt,
        RatingCost ratingCost,
        String reason) {

    /** How Codeforces names the problem, e.g. {@code 1985A}. */
    public String problemId() {
        return contestId + problemIndex;
    }

    public String url() {
        return "https://codeforces.com/contest/" + contestId + "/problem/" + problemIndex;
    }

    public AbandonedDebtItem withCostAndReason(RatingCost cost, String newReason) {
        return new AbandonedDebtItem(contestId, problemIndex, problemName, rating, tags,
                contestName, contestStartedAt, liveAttempts, liveWrongAttempts,
                lastLiveRelativeSeconds, firstAttemptedAt, lastAttemptedAt, cost, newReason);
    }
}
