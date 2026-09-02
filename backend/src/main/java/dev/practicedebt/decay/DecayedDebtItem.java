package dev.practicedebt.decay;

import java.time.Instant;
import java.util.List;

/**
 * One technique that has gone quiet.
 *
 * <p>The suggested action is always a <em>different</em> problem in the technique, at a difficulty
 * worth the hour it costs. Never a re-solve: re-solving tests memory of one solution, not command
 * of a technique, and it costs the same 30-60 minutes as real practice while teaching less.
 */
public record DecayedDebtItem(
        String techniqueId,
        String name,
        String family,
        int solvedCount,
        Instant lastSolvedAt,
        int daysSinceLast,
        double retention,
        /**
         * Roughly how many problems' worth of accumulated skill the model says has decayed:
         * {@code solvedCount * (1 - retention)}. This, not raw staleness, is what makes an item
         * worth reading - a technique with 100 solves gone quiet matters more than one with 3.
         */
        double skillAtRisk,
        List<TechniqueActivityRepository.Suggestion> suggestions,
        String reason) {
}
