package dev.practicedebt.rating;

import java.math.BigDecimal;

/**
 * How a Codeforces contest scores a solved problem.
 *
 * <p>Both rules below were measured against real ranklists, not taken from documentation:
 *
 * <ul>
 *   <li><b>CF-type.</b> Contest 1993: problem A worth 500 solved at 218s scored 494, B worth 1000 at
 *       937s scored 940, C worth 1500 at 2675s scored 1236. All three match
 *       {@code max(0.3X, X - X*floor(t/60)/250 - 50w)} exactly, including the truncation of elapsed
 *       time to whole minutes.
 *   <li><b>ICPC-type.</b> Contest 1969: of 1,514 ranklist rows carrying wrong attempts on solved
 *       problems, every single one implies a penalty of exactly 10 minutes per wrong attempt.
 * </ul>
 *
 * <p>Also confirmed on contest 1993: a compilation error is <em>not</em> a wrong attempt. Problem A
 * scored full formula points with one compile error against it.
 */
public final class ContestScoring {

    /** Points bleed away over 250 minutes' worth of the problem's value, whatever the duration. */
    private static final double DECAY_DIVISOR = 250.0;

    /** A problem never drops below this fraction of its opening value. */
    private static final double FLOOR_FRACTION = 0.3;

    /** Points lost per wrong attempt in a scored contest. */
    private static final double WRONG_ATTEMPT_POINTS = 50.0;

    /** Minutes added per wrong attempt on a solved problem in an ICPC-type contest. */
    public static final int WRONG_ATTEMPT_PENALTY_MINUTES = 10;

    private ContestScoring() {
    }

    /**
     * What a problem would have been worth, solved at {@code solvedAtSeconds} after the start.
     *
     * @param maxPoints      the problem's opening value
     * @param wrongAttempts  wrong attempts preceding the solve
     */
    public static double scoredPoints(double maxPoints, long solvedAtSeconds, int wrongAttempts) {
        long minutes = solvedAtSeconds / 60;
        double decayed = maxPoints - maxPoints * minutes / DECAY_DIVISOR
                - WRONG_ATTEMPT_POINTS * wrongAttempts;
        return Math.max(FLOOR_FRACTION * maxPoints, decayed);
    }

    /** Penalty minutes added by solving a problem at {@code solvedAtSeconds} in an ICPC-type contest. */
    public static long penaltyMinutes(long solvedAtSeconds, int wrongAttempts) {
        return solvedAtSeconds / 60 + (long) WRONG_ATTEMPT_PENALTY_MINUTES * wrongAttempts;
    }

    /**
     * The score a party would hold after also solving one more problem.
     *
     * @param contestType  {@code CF}, {@code ICPC} or {@code IOI} as Codeforces reports it
     * @param maxPoints    the abandoned problem's opening value; ignored for ICPC-type contests
     */
    public static Score afterAlsoSolving(String contestType, BigDecimal currentPoints,
            int currentPenalty, BigDecimal maxPoints, long solvedAtSeconds, int wrongAttempts) {

        if (isIcpcStyle(contestType)) {
            // One more problem solved, and the clock plus wrong attempts added to the penalty.
            return new Score(
                    currentPoints.add(BigDecimal.ONE),
                    currentPenalty + (int) penaltyMinutes(solvedAtSeconds, wrongAttempts));
        }

        double gained = maxPoints == null
                ? 0
                : scoredPoints(maxPoints.doubleValue(), solvedAtSeconds, wrongAttempts);
        return new Score(currentPoints.add(BigDecimal.valueOf(Math.round(gained))), currentPenalty);
    }

    /**
     * Whether the contest ranks by problems solved and penalty time rather than by points.
     *
     * <p>Codeforces labels Div. 3 and educational rounds {@code ICPC} even though they are ordinary
     * rated rounds, so this is about the scoring rule, not the event.
     */
    public static boolean isIcpcStyle(String contestType) {
        return "ICPC".equals(contestType);
    }

    /** A party's position in the ranklist: more points is better, then fewer penalty minutes. */
    public record Score(BigDecimal points, int penalty) {

        /** True when this score finishes strictly above {@code other} in the ranklist. */
        public boolean beats(Score other) {
            int byPoints = points.compareTo(other.points);
            if (byPoints != 0) {
                return byPoints > 0;
            }
            return penalty < other.penalty;
        }
    }
}
