package dev.practicedebt.debt;

/**
 * The assumptions behind every rating cost, in one place and in plain words.
 *
 * <p>The spec asks for the counterfactual to be surfaced in the UI rather than hidden, on the
 * grounds that the assumption is wrong but tractable. This is the text that does that.
 */
public final class CounterfactualPolicy {

    private CounterfactualPolicy() {
    }

    public static String describe() {
        return "Cost assumes your last attempt at the problem had passed instead of failing, "
                + "with everyone else's results left exactly as they were. Earlier failed attempts "
                + "still count against the score; the final one does not, because in this telling "
                + "it succeeded. Ranks are recomputed from the real ranklist and the rating formula "
                + "is re-run over the same field, so the figure is a difference between two model "
                + "runs rather than a prediction. It ignores that a better round would have shifted "
                + "everyone else too.";
    }
}
