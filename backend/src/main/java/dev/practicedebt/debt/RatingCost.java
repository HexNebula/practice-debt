package dev.practicedebt.debt;

/**
 * What one abandoned item cost in rating, and enough context to disbelieve it.
 *
 * @param ratingCost              rating plausibly lost, never negative; null when uncomputable
 * @param unrated                 the contest was unrated, so there was nothing to lose
 * @param actualRank              where they actually finished
 * @param counterfactualRank      where they would have finished with this problem also solved
 * @param actualDelta             what Codeforces really awarded
 * @param modelActualDelta        what the model says it would have awarded for the actual rank
 * @param modelCounterfactualDelta what the model awards for the counterfactual rank
 * @param assumedSolveSeconds     when the problem is assumed to have been solved, from contest start
 * @param assumedWrongAttempts    wrong attempts assumed to precede that solve
 */
public record RatingCost(
        Integer ratingCost,
        boolean unrated,
        Integer actualRank,
        Integer counterfactualRank,
        Integer actualDelta,
        Integer modelActualDelta,
        Integer modelCounterfactualDelta,
        Integer assumedSolveSeconds,
        Integer assumedWrongAttempts) {

    /**
     * How far the model was from reality on this contest, for this competitor.
     *
     * <p>The cost is a difference of two model runs, so a shared bias cancels out of it. This
     * number is what tells you whether that assumption held here.
     */
    public Integer modelError() {
        return modelActualDelta == null || actualDelta == null
                ? null
                : modelActualDelta - actualDelta;
    }

    public static RatingCost unrated(int actualRank) {
        return new RatingCost(null, true, actualRank, null, null, null, null, null, null);
    }
}
