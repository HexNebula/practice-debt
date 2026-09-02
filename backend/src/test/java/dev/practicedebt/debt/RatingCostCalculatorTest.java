package dev.practicedebt.debt;

import java.util.Optional;

import dev.practicedebt.mirror.ContestResultsMirror;
import dev.practicedebt.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counterfactual, end to end, against a small hand-built contest.
 *
 * <p>Every contest here is marked already-mirrored in {@code mirror_run}, so nothing reaches
 * Codeforces: these tests assert arithmetic, not connectivity.
 */
class RatingCostCalculatorTest extends PostgresIntegrationTest {

    private static final String HANDLE = "author";
    private static final int CONTEST = 9001;

    @Autowired
    private RatingCostCalculator calculator;

    @Autowired
    private JdbcClient db;

    @BeforeEach
    void reset() {
        db.sql("delete from standings_row").update();
        db.sql("delete from rating_change").update();
        db.sql("delete from contest_problem").update();
        db.sql("delete from mirror_run").update();
        db.sql("delete from contest").update();
    }

    @Test
    @DisplayName("solving one more problem lifts the rank and costs positive rating")
    void computesACostForARatedContest() {
        givenRatedContest("CF");
        // The author sits on 1000 points in 5th; solving D at 60s is worth nearly its full 2000.
        givenStanding("rival1", 1, 5000);
        givenStanding("rival2", 2, 4000);
        givenStanding("rival3", 3, 3000);
        givenStanding("rival4", 4, 2000);
        givenStanding(HANDLE, 5, 1000);
        givenRatingChanges();

        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 60, 1).orElseThrow();

        assertThat(cost.unrated()).isFalse();
        assertThat(cost.actualRank()).isEqualTo(5);
        // 1000 + ~1992 = ~2992, which slots in just behind rival3's 3000.
        assertThat(cost.counterfactualRank()).isEqualTo(4);
        assertThat(cost.ratingCost()).isPositive();
        assertThat(cost.modelCounterfactualDelta()).isGreaterThan(cost.modelActualDelta());
        assertThat(cost.assumedSolveSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("the last failed attempt becomes the solve, so it is not also a penalty")
    void theFinalAttemptIsNotCountedAsWrong() {
        givenRatedContest("CF");
        givenStanding(HANDLE, 1, 1000);
        givenRatingChanges();

        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 600, 5).orElseThrow();

        assertThat(cost.assumedWrongAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("an unrated contest reports no cost rather than a wrong one")
    void unratedContestsHaveNoCost() {
        givenRatedContest("ICPC");
        givenStanding(HANDLE, 1, 3);
        // No rating_change rows at all, which is exactly what an unrated contest looks like.
        markMirrored();

        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 600, 2).orElseThrow();

        assertThat(cost.unrated()).isTrue();
        assertThat(cost.ratingCost()).isNull();
        assertThat(cost.actualRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("being in the ranklist but not rated for the round yields no cost")
    void unratedParticipantsHaveNoCost() {
        givenRatedContest("CF");
        givenStanding(HANDLE, 3, 1000);
        givenStanding("rival1", 1, 5000);
        db.sql("""
                insert into rating_change (contest_id, handle, rank, old_rating, new_rating)
                values (:c, 'rival1', 1, 1600, 1700)
                """).param("c", CONTEST).update();
        markMirrored();

        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 600, 1).orElseThrow();

        assertThat(cost.unrated()).isTrue();
    }

    @Test
    @DisplayName("a handle absent from the ranklist yields nothing at all")
    void missingStandingsRowYieldsEmpty() {
        givenRatedContest("CF");
        givenStanding("rival1", 1, 5000);
        givenRatingChanges();

        assertThat(calculator.compute(HANDLE, CONTEST, "D", 600, 1)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("cost is never negative, even when the counterfactual changes nothing")
    void costNeverGoesNegative() {
        givenRatedContest("CF");
        // Already last by a mile; solving a problem worth 2000 still leaves them last.
        givenStanding("rival1", 1, 90000);
        givenStanding("rival2", 2, 80000);
        givenStanding(HANDLE, 3, 10);
        givenRatingChanges();

        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 7000, 1).orElseThrow();

        assertThat(cost.counterfactualRank()).isEqualTo(3);
        assertThat(cost.ratingCost()).isZero();
    }

    @Test
    @DisplayName("an ICPC-style contest ranks by problems solved and penalty time")
    void icpcStyleCounterfactual() {
        givenRatedContest("ICPC");
        givenStandingWithPenalty("rival1", 1, 4, 100);
        givenStandingWithPenalty("rival2", 2, 4, 400);
        givenStandingWithPenalty(HANDLE, 3, 3, 38);
        givenRatingChanges();

        // Solving a fourth at 60 minutes with 1 earlier failure: penalty 38 + 60 + 10 = 108,
        // which is 4 problems in less time than rival2 but more than rival1.
        RatingCost cost = calculator.compute(HANDLE, CONTEST, "D", 3600, 2).orElseThrow();

        assertThat(cost.counterfactualRank()).isEqualTo(2);
        assertThat(cost.ratingCost()).isPositive();
    }

    // --- fixtures ---

    private void givenRatedContest(String type) {
        db.sql("""
                insert into contest (id, name, type, phase, frozen, duration_seconds, start_time_seconds)
                values (:c, 'Test Round', :type, 'FINISHED', false, 7200, 1700000000)
                """).param("c", CONTEST).param("type", type).update();
        db.sql("""
                insert into contest_problem (contest_id, problem_index, max_points)
                values (:c, 'D', 2000)
                on conflict do nothing
                """).param("c", CONTEST).update();
    }

    private void givenStanding(String handle, int rank, int points) {
        givenStandingWithPenalty(handle, rank, points, 0);
    }

    private void givenStandingWithPenalty(String handle, int rank, int points, int penalty) {
        db.sql("""
                insert into standings_row (contest_id, party_key, rank, points, penalty)
                values (:c, :h, :r, :p, :pen)
                """)
                .param("c", CONTEST).param("h", handle).param("r", rank)
                .param("p", points).param("pen", penalty)
                .update();
    }

    /** Makes every standings party rated, so the field the formula runs over is the whole ranklist. */
    private void givenRatingChanges() {
        db.sql("""
                insert into rating_change (contest_id, handle, rank, old_rating, new_rating)
                select contest_id, party_key, rank, 1500, 1500 from standings_row
                 where contest_id = :c
                """).param("c", CONTEST).update();
        markMirrored();
    }

    private void markMirrored() {
        for (String source : new String[]{
                ContestResultsMirror.standingsSource(CONTEST),
                ContestResultsMirror.ratingChangesSource(CONTEST)}) {
            db.sql("""
                    insert into mirror_run (source, status, finished_at, item_count)
                    values (:s, 'SUCCESS', now(), 1)
                    """).param("s", source).update();
        }
    }
}
