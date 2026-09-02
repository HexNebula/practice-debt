package dev.practicedebt.rating;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected values here are not invented. They are what Codeforces actually awarded in contest
 * 1993 and contest 1969, read back out of the live ranklists.
 */
class ContestScoringTest {

    @Test
    @DisplayName("reproduces real scores from Codeforces Round 963")
    void matchesObservedScoredPoints() {
        // A: worth 500, solved at 218s, no wrong attempts -> Codeforces gave 494.
        assertThat(ContestScoring.scoredPoints(500, 218, 0)).isEqualTo(494.0);
        // B: worth 1000, solved at 937s -> 940.
        assertThat(ContestScoring.scoredPoints(1000, 937, 0)).isEqualTo(940.0);
        // C: worth 1500, solved at 2675s -> 1236.
        assertThat(ContestScoring.scoredPoints(1500, 2675, 0)).isEqualTo(1236.0);
    }

    @Test
    @DisplayName("elapsed time truncates to whole minutes")
    void timeTruncatesToMinutes() {
        // 218s is 3.63 minutes. Scoring by the real value would give 492.7, not the observed 494.
        assertThat(ContestScoring.scoredPoints(500, 218, 0))
                .isEqualTo(ContestScoring.scoredPoints(500, 180, 0));
    }

    @Test
    @DisplayName("each wrong attempt costs a flat 50 points")
    void wrongAttemptsCostFifty() {
        assertThat(ContestScoring.scoredPoints(2000, 0, 0)
                - ContestScoring.scoredPoints(2000, 0, 3)).isEqualTo(150.0);
    }

    @Test
    @DisplayName("a problem never falls below 30% of its opening value")
    void pointsFloorAtThirtyPercent() {
        assertThat(ContestScoring.scoredPoints(2000, 7200, 20)).isEqualTo(600.0);
        assertThat(ContestScoring.scoredPoints(500, 999_999, 0)).isEqualTo(150.0);
    }

    @Test
    @DisplayName("ICPC penalty is minutes plus ten per wrong attempt")
    void icpcPenaltyMatchesObservedRule() {
        // Derived from 1,514 rows of contest 1969, every one of which implied exactly 10.
        assertThat(ContestScoring.penaltyMinutes(1492, 0)).isEqualTo(24);
        assertThat(ContestScoring.penaltyMinutes(1492, 2)).isEqualTo(44);
    }

    @Test
    @DisplayName("an ICPC-style contest gains a point and pays penalty time")
    void icpcScoreAfterSolving() {
        ContestScoring.Score after = ContestScoring.afterAlsoSolving(
                "ICPC", new BigDecimal("3"), 38, null, 3600, 2);

        assertThat(after.points()).isEqualByComparingTo("4");
        assertThat(after.penalty()).isEqualTo(38 + 60 + 20);
    }

    @Test
    @DisplayName("a scored contest gains points and leaves penalty alone")
    void cfScoreAfterSolving() {
        // 1993D: worth 2000, last attempt at 7127s (118 min), 4 wrong attempts standing.
        ContestScoring.Score after = ContestScoring.afterAlsoSolving(
                "CF", new BigDecimal("2670"), 0, new BigDecimal("2000"), 7127, 4);

        assertThat(after.points()).isEqualByComparingTo("3526");
        assertThat(after.penalty()).isZero();
    }

    @Test
    @DisplayName("more points wins; equal points is broken by less penalty")
    void scoresCompareTheWayRanklistsDo() {
        ContestScoring.Score better = new ContestScoring.Score(new BigDecimal("3000"), 50);
        ContestScoring.Score worse = new ContestScoring.Score(new BigDecimal("2000"), 0);
        ContestScoring.Score tiedButSlower = new ContestScoring.Score(new BigDecimal("3000"), 90);

        assertThat(better.beats(worse)).isTrue();
        assertThat(worse.beats(better)).isFalse();
        assertThat(better.beats(tiedButSlower)).isTrue();
        assertThat(tiedButSlower.beats(better)).isFalse();
    }
}
