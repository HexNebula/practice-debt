package dev.practicedebt.decay;

import java.time.Duration;

/**
 * Every number in the decay model, in one place, with the reason it was chosen.
 *
 * <p>None of these are fitted. There is no data to fit them to yet, and the spec is explicit that
 * building a decay model before the data exists would be inventing precision. They are guesses,
 * they are labelled as guesses everywhere they surface, and {@code technique_return} is
 * accumulating the evidence that will one day replace them.
 */
public final class DecayPolicy {

    /**
     * How long until a technique is modelled as half-forgotten.
     *
     * <p>One constant for every technique, deliberately. Per-technique half-lives are certainly
     * different - nobody forgets binary search as fast as they forget suffix automata - but that
     * difference cannot be known before the calibration data exists, and inventing 34 numbers
     * instead of one would only make the guess harder to see.
     */
    public static final int HALF_LIFE_DAYS = 90;

    /**
     * Solves needed before a technique can be called forgotten.
     *
     * <p>Below this it was never learned, and a thing never learned is not a debt - it is a gap.
     * Conflating the two would fill the queue with everything the author has never tried.
     */
    public static final int MIN_SOLVES_TO_COUNT = 3;

    /**
     * Modelled retention below which a technique is worth surfacing.
     *
     * <p>0.5 is exactly one half-life. Anything fresher than that is not a complaint worth making.
     */
    public static final double SURFACE_BELOW_RETENTION = 0.5;

    /**
     * A gap long enough that returning to a technique is evidence about forgetting.
     *
     * <p>Shorter gaps are ordinary practice rhythm and would drown the signal in noise.
     */
    public static final int RETURN_GAP_DAYS = 30;

    /**
     * Where to aim a suggestion, relative to the author's current rating.
     *
     * <p>Slightly above comfort: low enough to be solvable after a lapse, high enough to be worth
     * the hour. Problems below the floor teach nothing about whether the technique came back.
     */
    public static final int SUGGESTION_RATING_BELOW = 100;
    public static final int SUGGESTION_RATING_ABOVE = 300;

    /** Used when a handle has no rating at all, so suggestions are still possible. */
    public static final int DEFAULT_RATING_ANCHOR = 1400;

    private DecayPolicy() {
    }

    /** Modelled fraction of the technique still retained after {@code days} of silence. */
    public static double retention(long days) {
        return Math.pow(0.5, (double) days / HALF_LIFE_DAYS);
    }

    public static Duration halfLife() {
        return Duration.ofDays(HALF_LIFE_DAYS);
    }

    /** The sentence the UI must show beside any decay figure. */
    public static String describe() {
        return "Freshness is inferred from what you already solved - nothing here schedules a review. "
                + "The " + HALF_LIFE_DAYS + "-day half-life is a hand-picked guess, identical for "
                + "every technique, and is not fitted to anything. It is a placeholder until enough "
                + "returns-after-a-gap have been recorded to fit a real one. A technique needs at "
                + "least " + MIN_SOLVES_TO_COUNT + " solves before it can count as forgotten rather "
                + "than never learned.";
    }
}
