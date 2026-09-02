package dev.practicedebt.rating;

/**
 * The Codeforces rating formula, as far as it can be reconstructed.
 *
 * <h2>How faithful this is</h2>
 *
 * <p>Measured against the real deltas of Codeforces Round 963 (Div. 2), 20,489 rated participants:
 * the median error for established competitors is about -12 rating, drifting from roughly -6 at the
 * top of the ranklist to -100 at the bottom. Newcomers are worse still and are not modelled at all.
 * Modern Codeforces evidently applies rules on top of the published formula that are not documented.
 *
 * <p>That would make this useless as an absolute predictor. It is not used as one. A debt item's
 * cost is the <em>difference</em> between two runs of this model over the same field — the author at
 * their real rank, and the author at the rank they would have reached. A bias that afflicts both
 * runs cancels. On the item this was developed against, model-minus-model gave 47 and the naive
 * model-minus-actual gave 44, while the model reproduced the author's real delta to within 3.
 *
 * <p>{@code modelActualDelta} is stored next to every computed cost precisely so this assumption
 * stays checkable rather than becoming folklore.
 *
 * <h2>The algorithm</h2>
 *
 * <p>For each contestant: their <em>seed</em> is the rank they would be expected to take, given
 * everyone else's rating. The geometric mean of seed and actual rank is the performance to reward;
 * the rating that would have produced that performance, minus the current rating, halved, is the
 * delta. Two passes then re-centre the field.
 */
public final class RatingSystem {

    /**
     * What a first-time participant is treated as being worth.
     *
     * <p>Codeforces reports {@code oldRating: 0} for a first rated contest. Taken literally that
     * makes 1,458 free wins for everyone else and drags every delta down. Seeding them at 1500
     * moved exact reproductions from 5 to 408 and near-misses from 43 to 1,479 on the contest this
     * was calibrated against, which is the empirical case for 1500 being the value Codeforces uses.
     */
    public static final int NEWCOMER_SEED_RATING = 1500;

    private static final int MAX_RATING = 9000;

    /**
     * Codeforces ratings can be negative, and in a large Div. 2 field several are. Every table here
     * is therefore indexed by {@code rating + RATING_OFFSET} rather than by the rating itself.
     */
    private static final int MIN_RATING = -1000;

    private static final int RATING_OFFSET = -MIN_RATING;
    private static final int RATING_SLOTS = MAX_RATING - MIN_RATING + 1;
    private static final int MAX_DIFFERENCE = MAX_RATING - MIN_RATING;

    private static final int SEARCH_LOW = 1;
    private static final int SEARCH_HIGH = 8000;

    private RatingSystem() {
    }

    /**
     * Computes the rating delta of every participant.
     *
     * @param ranks   final rank of each participant; ties share the better rank, as Codeforces does
     * @param ratings pre-contest rating of each participant, with 0 meaning "first rated contest"
     * @return deltas, parallel to the inputs
     */
    public static int[] deltas(int[] ranks, int[] ratings) {
        if (ranks.length != ratings.length) {
            throw new IllegalArgumentException("ranks and ratings must be parallel");
        }
        int n = ranks.length;
        if (n == 0) {
            return new int[0];
        }

        int[] seeded = new int[n];
        for (int i = 0; i < n; i++) {
            int rating = ratings[i] == 0 ? NEWCOMER_SEED_RATING : ratings[i];
            seeded[i] = Math.clamp(rating, MIN_RATING, MAX_RATING);
        }

        double[] winProbability = winProbabilityByDifference();
        double[] seedAt = seedByRating(seeded, winProbability);

        long[] delta = new long[n];
        for (int i = 0; i < n; i++) {
            // The contestant does not seed against themselves; they would contribute exactly 0.5.
            double ownSeed = seedAt[seeded[i] + RATING_OFFSET] - 0.5;
            double target = Math.sqrt(ownSeed * ranks[i]);
            int needed = ratingForSeed(target, seeded[i], seedAt, winProbability);
            delta[i] = Math.floorDiv(needed - seeded[i], 2);
        }

        // Pass one: the field as a whole should not manufacture rating.
        long sum = 0;
        for (long d : delta) {
            sum += d;
        }
        long inc = Math.floorDiv(-sum, n) - 1;
        for (int i = 0; i < n; i++) {
            delta[i] += inc;
        }

        // Pass two: the strongest slice of the field should be roughly zero-sum, which stops
        // rating draining out of the top over time.
        int[] byRatingDesc = indicesByRatingDescending(seeded);
        int top = (int) Math.min(n, Math.round(4 * Math.sqrt(n)));
        long topSum = 0;
        for (int i = 0; i < top; i++) {
            topSum += delta[byRatingDesc[i]];
        }
        long adjust = Math.min(Math.max(Math.floorDiv(-topSum, top), -10), 0);
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int) (delta[i] + adjust);
        }
        return result;
    }

    /**
     * Probability that a competitor rated {@code a} finishes above one rated {@code b}, indexed by
     * {@code a - b + MAX_RATING}.
     *
     * <p>Tabulated because the alternative is tens of millions of {@link Math#pow} calls.
     */
    private static double[] winProbabilityByDifference() {
        double[] table = new double[2 * MAX_DIFFERENCE + 1];
        for (int d = -MAX_DIFFERENCE; d <= MAX_DIFFERENCE; d++) {
            table[d + MAX_DIFFERENCE] = 1.0 / (1.0 + Math.pow(10.0, -d / 400.0));
        }
        return table;
    }

    /**
     * {@code seedAt[r]} is the rank a competitor rated {@code r} would be expected to take against
     * this whole field, themselves included.
     *
     * <p>Computed once over distinct ratings rather than per contestant: the field has at most a
     * few thousand distinct ratings but tens of thousands of members.
     */
    private static double[] seedByRating(int[] ratings, double[] winProbability) {
        int[] count = new int[RATING_SLOTS];
        for (int r : ratings) {
            count[r + RATING_OFFSET]++;
        }

        int distinct = 0;
        for (int slot = 0; slot < RATING_SLOTS; slot++) {
            if (count[slot] > 0) {
                distinct++;
            }
        }
        int[] presentRating = new int[distinct];
        int[] presentCount = new int[distinct];
        int k = 0;
        for (int slot = 0; slot < RATING_SLOTS; slot++) {
            if (count[slot] > 0) {
                presentRating[k] = slot - RATING_OFFSET;
                presentCount[k] = count[slot];
                k++;
            }
        }

        double[] seedAt = new double[RATING_SLOTS];
        for (int slot = 0; slot < RATING_SLOTS; slot++) {
            int rating = slot - RATING_OFFSET;
            double seed = 1.0;
            for (int j = 0; j < distinct; j++) {
                seed += presentCount[j] * winProbability[presentRating[j] - rating + MAX_DIFFERENCE];
            }
            seedAt[slot] = seed;
        }
        return seedAt;
    }

    /**
     * The highest rating whose expected rank is still at least {@code target}, for a competitor who
     * does not seed against themselves.
     */
    private static int ratingForSeed(double target, int ownRating, double[] seedAt,
            double[] winProbability) {
        int low = SEARCH_LOW;
        int high = SEARCH_HIGH;
        while (high - low > 1) {
            int mid = (low + high) / 2;
            double seed = seedAt[mid + RATING_OFFSET]
                    - winProbability[ownRating - mid + MAX_DIFFERENCE];
            if (seed < target) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return low;
    }

    private static int[] indicesByRatingDescending(int[] ratings) {
        Integer[] boxed = new Integer[ratings.length];
        for (int i = 0; i < ratings.length; i++) {
            boxed[i] = i;
        }
        java.util.Arrays.sort(boxed, (a, b) -> Integer.compare(ratings[b], ratings[a]));
        int[] order = new int[ratings.length];
        for (int i = 0; i < ratings.length; i++) {
            order[i] = boxed[i];
        }
        return order;
    }
}
