package dev.practicedebt.queue;

/**
 * How an abandoned problem ranks against a decayed technique.
 *
 * <p>The spec calls this the central unresolved design decision, and says there is no correct
 * answer. It is therefore one named constant in one file, rather than an ordering that emerges from
 * whichever list happened to be built first.
 *
 * <h2>The problem</h2>
 *
 * <p>The two sources are not measured in the same thing and cannot be. An abandoned item carries a
 * rating cost computed from real standings and a real rating formula. A decayed technique carries
 * "skill at risk", which rests on a hand-picked half-life that this tool's own calibration data
 * already disagrees with. Putting them on one numeric scale would mean inventing a conversion, and
 * a conversion between a measurement and a guess is just a second guess wearing a disguise.
 *
 * <h2>The policy</h2>
 *
 * <p>Each source ranks its own items, which it can do honestly. That rank becomes a percentile in
 * [0, 1], so the best item of each source scores 1 and the worst scores near 0. Decayed items are
 * then multiplied by {@link #DECAYED_WEIGHT}.
 *
 * <p>This keeps both sources permanently visible - a queue that only ever shows abandoned debt
 * hides half the product - while stating plainly that a measured rating loss outranks an
 * equally-placed inference. The weight is the single arbitrary number, and it is visible rather
 * than buried.
 */
public final class QueuePolicy {

    /**
     * What a decayed technique is worth relative to an equally-placed abandoned problem.
     *
     * <p>0.6 says: the best decayed technique sits just below the top few abandoned items, and
     * comfortably above the mediocre ones. Raise it towards 1.0 to treat forgetting as seriously as
     * losing rating; lower it to push decay down the list. There is no principled value, only a
     * defensible one.
     */
    public static final double DECAYED_WEIGHT = 0.6;

    private QueuePolicy() {
    }

    /**
     * Turns a position within one source into a comparable score.
     *
     * @param rankWithinSource 0-based, 0 being that source's most urgent item
     * @param sourceSize       how many items that source produced
     */
    public static double score(int rankWithinSource, int sourceSize, boolean abandoned) {
        if (sourceSize <= 0) {
            return 0;
        }
        double percentile = (double) (sourceSize - rankWithinSource) / sourceSize;
        return abandoned ? percentile : percentile * DECAYED_WEIGHT;
    }

    /** Stated in the UI, because a ranking nobody can interrogate is a ranking nobody should trust. */
    public static String describe() {
        return "Each source ranks its own items — abandoned debt by rating cost, decayed techniques "
                + "by how much accumulated skill the model says is at risk. Those positions become "
                + "percentiles, and decayed items are then multiplied by " + DECAYED_WEIGHT + ", "
                + "because a rating loss computed from real standings deserves more weight than an "
                + "inference resting on a guessed half-life. That weight is the one arbitrary number "
                + "here. There is no correct value for it.";
    }
}
