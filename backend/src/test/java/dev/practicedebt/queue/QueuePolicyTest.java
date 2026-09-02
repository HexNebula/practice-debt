package dev.practicedebt.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one arbitrary number in the system, pinned down.
 *
 * <p>These tests exist less to catch bugs than to make the policy's consequences explicit: if
 * someone changes the weight, these say exactly what they changed.
 */
class QueuePolicyTest {

    @Test
    @DisplayName("the best item of each source scores its full weight")
    void bestItemScoresFullWeight() {
        assertThat(QueuePolicy.score(0, 29, true)).isEqualTo(1.0);
        assertThat(QueuePolicy.score(0, 30, false)).isEqualTo(QueuePolicy.DECAYED_WEIGHT);
    }

    @Test
    @DisplayName("the top decayed technique outranks a mid-table abandoned problem")
    void decayedDebtStaysVisible() {
        // The point of weighting rather than tiering: with 29 abandoned items, a strict
        // measured-before-modelled ordering would bury decay where nobody ever sees it.
        double topDecayed = QueuePolicy.score(0, 30, false);
        double midAbandoned = QueuePolicy.score(15, 29, true);

        assertThat(topDecayed).isGreaterThan(midAbandoned);
    }

    @Test
    @DisplayName("an abandoned item outranks an equally-placed decayed one")
    void measurementOutranksInference() {
        assertThat(QueuePolicy.score(3, 20, true)).isGreaterThan(QueuePolicy.score(3, 20, false));
    }

    @Test
    @DisplayName("score falls monotonically down each source's own ranking")
    void scoreDecreasesWithRank() {
        for (int i = 1; i < 20; i++) {
            assertThat(QueuePolicy.score(i, 20, true))
                    .isLessThan(QueuePolicy.score(i - 1, 20, true));
            assertThat(QueuePolicy.score(i, 20, false))
                    .isLessThan(QueuePolicy.score(i - 1, 20, false));
        }
    }

    @Test
    @DisplayName("an empty source cannot contribute")
    void emptySourceScoresZero() {
        assertThat(QueuePolicy.score(0, 0, true)).isZero();
    }

    @Test
    @DisplayName("the policy explains itself, including that the weight is arbitrary")
    void policyIsStated() {
        assertThat(QueuePolicy.describe())
                .contains(String.valueOf(QueuePolicy.DECAYED_WEIGHT))
                .contains("no correct value");
    }
}
