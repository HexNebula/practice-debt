package dev.practicedebt.decay;

import java.time.Instant;

/**
 * How alive one technique is for one handle.
 *
 * @param solvedCount   distinct problems in this technique ever solved
 * @param lastSolvedAt  most recent solve, or null if never
 * @param solvedLast30  solves in the last 30 days, the "this is obviously fresh" signal
 * @param retention     modelled fraction retained, under a guessed half-life
 */
public record TechniqueActivity(
        String techniqueId,
        String name,
        String family,
        int solvedCount,
        Instant lastSolvedAt,
        Integer daysSinceLast,
        int solvedLast30,
        double retention) {

    /**
     * Whether this technique is worth surfacing as debt.
     *
     * <p>Two conditions, both necessary. It must have been learned - a technique with almost no
     * solves is a gap, not a debt - and it must have gone quiet for long enough that the guess says
     * half of it is gone.
     */
    public boolean isDecayed() {
        return solvedCount >= DecayPolicy.MIN_SOLVES_TO_COUNT
                && lastSolvedAt != null
                && retention < DecayPolicy.SURFACE_BELOW_RETENTION;
    }
}
