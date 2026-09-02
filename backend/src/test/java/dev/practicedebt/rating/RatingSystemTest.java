package dev.practicedebt.rating;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Properties rather than exact deltas.
 *
 * <p>The model provably does not reproduce modern Codeforces exactly - see {@link RatingSystem} -
 * so pinning exact numbers would be testing the bug. What the rating cost actually depends on is
 * the model's behaviour across ranks in a fixed field, and that is what is asserted here.
 */
class RatingSystemTest {

    @Test
    @DisplayName("finishing higher is never worth less rating")
    void deltaIsMonotoneInRank() {
        int n = 400;
        int[] ratings = new int[n];
        int[] ranks = new int[n];
        Random random = new Random(42);
        for (int i = 0; i < n; i++) {
            ratings[i] = 800 + random.nextInt(2200);
            ranks[i] = i + 1;
        }

        int[] baseline = RatingSystem.deltas(ranks, ratings);

        // Move one competitor up the ranklist; everyone they passed drops a place.
        int subject = 300;
        int[] improved = ranks.clone();
        improved[subject] = 100;
        for (int i = 0; i < n; i++) {
            if (i != subject && ranks[i] >= 100 && ranks[i] <= ranks[subject]) {
                improved[i] = ranks[i] + 1;
            }
        }

        int[] after = RatingSystem.deltas(improved, ratings);
        assertThat(after[subject]).isGreaterThan(baseline[subject]);
    }

    @Test
    @DisplayName("in a field of equals, rank order is delta order")
    void equalRatingsRankInOrder() {
        int n = 50;
        int[] ratings = new int[n];
        int[] ranks = new int[n];
        for (int i = 0; i < n; i++) {
            ratings[i] = 1500;
            ranks[i] = i + 1;
        }

        int[] deltas = RatingSystem.deltas(ranks, ratings);

        assertThat(deltas[0]).isPositive();
        assertThat(deltas[n - 1]).isNegative();
        for (int i = 1; i < n; i++) {
            assertThat(deltas[i]).isLessThanOrEqualTo(deltas[i - 1]);
        }
    }

    @Test
    @DisplayName("a first-time entrant is seeded at 1500, not at zero")
    void newcomersAreSeeded() {
        // Taken literally, oldRating 0 makes a newcomer a free win for the whole field and drags
        // every delta down. Seeding them moved exact reproductions of a real contest from 5 to 408.
        int[] ranks = {1, 2, 3, 4};
        int[] withNewcomer = {0, 1500, 1500, 1500};
        int[] asSeeded = {RatingSystem.NEWCOMER_SEED_RATING, 1500, 1500, 1500};

        assertThat(RatingSystem.deltas(ranks, withNewcomer))
                .containsExactly(RatingSystem.deltas(ranks, asSeeded));
    }

    @Test
    @DisplayName("the field does not manufacture rating out of nothing")
    void fieldIsRoughlyZeroSum() {
        int n = 200;
        int[] ratings = new int[n];
        int[] ranks = new int[n];
        Random random = new Random(7);
        for (int i = 0; i < n; i++) {
            ratings[i] = 1000 + random.nextInt(1500);
            ranks[i] = i + 1;
        }

        int total = 0;
        for (int d : RatingSystem.deltas(ranks, ratings)) {
            total += d;
        }
        assertThat(total).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("negative ratings are ordinary, not a crash")
    void handlesNegativeRatings() {
        // Real Div. 2 fields contain them. Indexing tables by raw rating threw
        // ArrayIndexOutOfBounds on live data before this was handled.
        int[] ranks = {1, 2, 3, 4, 5};
        int[] ratings = {-45, -21, 0, 1200, 2600};

        int[] deltas = RatingSystem.deltas(ranks, ratings);

        assertThat(deltas).hasSize(5);
        assertThat(deltas[0]).isGreaterThan(deltas[4]);
    }

    @Test
    @DisplayName("degenerate fields do not blow up")
    void handlesTinyFields() {
        assertThat(RatingSystem.deltas(new int[0], new int[0])).isEmpty();
        assertThat(RatingSystem.deltas(new int[]{1}, new int[]{1500})).hasSize(1);
    }
}
