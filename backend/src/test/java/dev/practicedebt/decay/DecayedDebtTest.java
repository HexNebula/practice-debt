package dev.practicedebt.decay;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.practicedebt.support.PostgresIntegrationTest;
import dev.practicedebt.taxonomy.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decay end to end, against the real taxonomy.
 *
 * <p>Problems are given tags that the shipped rules genuinely map, so these tests exercise the
 * mapping the tool actually uses rather than a fixture invented to pass.
 */
class DecayedDebtTest extends PostgresIntegrationTest {

    private static final String HANDLE = "author";

    @Autowired
    private DecayedDebtService decay;

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private TechniqueReturnRepository returns;

    @Autowired
    private JdbcClient db;

    private long nextSubmissionId = 1;

    @BeforeEach
    void reset() {
        db.sql("delete from technique_return").update();
        db.sql("delete from technique_snapshot").update();
        db.sql("delete from problem_technique").update();
        db.sql("delete from submission").update();
        db.sql("delete from problem").update();
        nextSubmissionId = 1;
    }

    @Test
    @DisplayName("a technique practised recently never surfaces")
    void freshTechniquesStaySilent() {
        givenSolvedProblems("dp", "trees", 4, Duration.ofDays(3));

        DecayedDebtService.Report report = decay.reportFor(HANDLE);

        assertThat(report.items()).extracting(DecayedDebtItem::techniqueId).doesNotContain("tree-dp");
        assertThat(report.fresh()).isPositive();
    }

    @Test
    @DisplayName("a technique gone quiet for long enough surfaces, and says so")
    void quietTechniquesSurface() {
        givenSolvedProblems("dp", "trees", 5, Duration.ofDays(300));

        DecayedDebtService.Report report = decay.reportFor(HANDLE);

        DecayedDebtItem item = report.items().stream()
                .filter(i -> i.techniqueId().equals("tree-dp"))
                .findFirst()
                .orElseThrow();

        assertThat(item.daysSinceLast()).isGreaterThan(290);
        assertThat(item.retention()).isLessThan(0.2);
        assertThat(item.reason()).contains("Tree DP").contains("months").contains("half-life");
        assertThat(report.halfLifeDays()).isEqualTo(DecayPolicy.HALF_LIFE_DAYS);
        assertThat(report.policy()).contains("guess");
    }

    @Test
    @DisplayName("a technique barely touched is a gap, not a debt")
    void barelyTouchedTechniquesAreNotDebt() {
        // Two solves, long ago. Never established, so forgetting it is not the right description.
        givenSolvedProblems("dp", "trees", 2, Duration.ofDays(400));

        DecayedDebtService.Report report = decay.reportFor(HANDLE);

        assertThat(report.items()).extracting(DecayedDebtItem::techniqueId).doesNotContain("tree-dp");
        assertThat(report.neverEstablished()).isPositive();
    }

    @Test
    @DisplayName("suggestions are unsolved problems in the technique, never re-solves")
    void suggestionsAreAlwaysNewProblems() {
        givenSolvedProblems("dp", "trees", 5, Duration.ofDays(300));
        // Candidates at the right difficulty that the author has never solved.
        givenProblem(5001, "A", 1500, "dp", "trees");
        givenProblem(5002, "B", 1600, "dp", "trees");
        givenRating(1500);
        taxonomy.apply();

        DecayedDebtItem item = decay.reportFor(HANDLE).items().stream()
                .filter(i -> i.techniqueId().equals("tree-dp"))
                .findFirst()
                .orElseThrow();

        assertThat(item.suggestions()).isNotEmpty();
        assertThat(item.suggestions())
                .extracting(TechniqueActivityRepository.Suggestion::problemId)
                .doesNotContain("1000A", "1001A", "1002A", "1003A", "1004A");
    }

    @Test
    @DisplayName("snapshots accumulate rather than overwrite")
    void snapshotsAreAppendOnly() {
        givenSolvedProblems("dp", "trees", 5, Duration.ofDays(300));

        DecayedDebtService.SnapshotResult first = decay.snapshot(HANDLE);
        DecayedDebtService.SnapshotResult second = decay.snapshot(HANDLE);

        assertThat(first.techniquesRecorded()).isPositive();
        assertThat(second.totalSnapshots()).isEqualTo(first.totalSnapshots() * 2);
        assertThat(decay.history(HANDLE, "tree-dp")).hasSize(2);
        // The guess in force is recorded with each point, so changing it later cannot rewrite
        // what was already observed.
        assertThat(decay.history(HANDLE, "tree-dp"))
                .allSatisfy(p -> assertThat(p.halfLifeDays()).isEqualTo(DecayPolicy.HALF_LIFE_DAYS));
    }

    @Test
    @DisplayName("returning to a technique after a gap is recorded as calibration evidence")
    void returnsAfterAGapAreRecorded() {
        // Solved once long ago, then again after a 200-day silence, cleanly on the first attempt.
        givenSolve(2001, "A", Duration.ofDays(400), true, List.of("dp", "trees"));
        givenSolve(2002, "A", Duration.ofDays(200), true, List.of("dp", "trees"));
        taxonomy.apply();

        decay.snapshot(HANDLE);

        List<TechniqueReturnRepository.Calibration> calibration = returns.calibration(HANDLE);
        assertThat(calibration).isNotEmpty();
        assertThat(calibration).anySatisfy(c -> {
            assertThat(c.returns()).isPositive();
            assertThat(c.gapBucket()).isNotBlank();
        });
    }

    @Test
    @DisplayName("a failed first attempt on the return is recorded as not clean")
    void messyReturnsAreRecordedAsMessy() {
        givenSolve(2001, "A", Duration.ofDays(400), true, List.of("dp", "trees"));
        givenSolve(2002, "A", Duration.ofDays(200), false, List.of("dp", "trees"));
        taxonomy.apply();

        decay.snapshot(HANDLE);

        Long clean = db.sql("""
                        select count(*) from technique_return
                         where handle = :h and technique_id = 'tree-dp' and solved_first_try
                        """).param("h", HANDLE).query(Long.class).single();
        assertThat(clean).isZero();
    }

    @Test
    @DisplayName("reloading the taxonomy does not destroy the history it exists to accumulate")
    void taxonomyReloadPreservesEvidence() {
        // Reloading rebuilt the technique table by deleting it, and both history tables cascaded.
        // Editing the taxonomy file - a routine, encouraged action - therefore erased every
        // snapshot and every recorded return. A snapshot of last spring cannot be regenerated.
        givenSolve(2001, "A", Duration.ofDays(400), true, List.of("dp", "trees"));
        givenSolve(2002, "A", Duration.ofDays(200), true, List.of("dp", "trees"));
        taxonomy.apply();
        decay.snapshot(HANDLE);

        long snapshotsBefore = count("technique_snapshot");
        long returnsBefore = count("technique_return");
        assertThat(snapshotsBefore).isPositive();
        assertThat(returnsBefore).isPositive();

        taxonomy.apply();

        assertThat(count("technique_snapshot")).isEqualTo(snapshotsBefore);
        assertThat(count("technique_return")).isEqualTo(returnsBefore);
    }

    @Test
    @DisplayName("history survives even when its technique is removed from the taxonomy")
    void historyOutlivesItsTechnique() {
        // A snapshot taken under a technique later renamed or merged away is still a true record
        // of what was observed. Deleting it would falsify the past rather than tidy it.
        givenSolvedProblems("dp", "trees", 5, Duration.ofDays(300));
        decay.snapshot(HANDLE);

        db.sql("delete from technique where id = 'tree-dp'").update();

        assertThat(db.sql("""
                        select count(*) from technique_snapshot
                         where handle = :h and technique_id = 'tree-dp'
                        """).param("h", HANDLE).query(Long.class).single()).isPositive();
    }

    @Test
    @DisplayName("no history table cascades from the technique table")
    void historyTablesDoNotCascade() {
        // The mechanism, not just the symptom: a future migration that reintroduces a cascading
        // foreign key here would reintroduce the data loss, and would pass every other test.
        List<String> cascading = db.sql("""
                        select conrelid::regclass::text
                          from pg_constraint
                         where confrelid = 'technique'::regclass
                           and confdeltype = 'c'
                        """).query(String.class).list();

        assertThat(cascading).doesNotContain("technique_snapshot", "technique_return");
    }

    @Test
    @DisplayName("a handle with no history reports cleanly")
    void emptyHandleIsFine() {
        DecayedDebtService.Report report = decay.reportFor("nobody");

        assertThat(report.items()).isEmpty();
        assertThat(report.calibration()).isEmpty();
        assertThat(report.policy()).isNotBlank();
    }

    private long count(String table) {
        return db.sql("select count(*) from " + table + " where handle = :h")
                .param("h", HANDLE).query(Long.class).single();
    }

    // --- fixtures ---

    private void givenSolvedProblems(String tagA, String tagB, int count, Duration ago) {
        for (int i = 0; i < count; i++) {
            givenSolve(1000 + i, "A", ago.plusDays(i), true, List.of(tagA, tagB));
        }
        taxonomy.apply();
    }

    private void givenSolve(int contestId, String index, Duration ago, boolean cleanFirstTry,
            List<String> tags) {
        givenProblem(contestId, index, 1500, tags.toArray(String[]::new));
        long solvedAt = Instant.now().minus(ago).getEpochSecond();
        if (!cleanFirstTry) {
            insertSubmission(contestId, index, solvedAt - 600, "WRONG_ANSWER");
        }
        insertSubmission(contestId, index, solvedAt, "OK");
    }

    private void givenProblem(int contestId, String index, int rating, String... tags) {
        db.sql("""
                        insert into problem (contest_id, problem_index, name, type, rating, tags)
                        values (:c, :i, :n, 'PROGRAMMING', :r, :t)
                        on conflict do nothing
                        """)
                .param("c", contestId).param("i", index).param("n", "problem " + contestId + index)
                .param("r", rating).param("t", tags)
                .update();
    }

    private void insertSubmission(int contestId, String index, long at, String verdict) {
        db.sql("""
                        insert into submission (id, handle, contest_id, problem_index, problem_name,
                                creation_time_seconds, participant_type, verdict)
                        values (:id, :h, :c, :i, 'p', :at, 'PRACTICE', :v)
                        """)
                .param("id", nextSubmissionId++).param("h", HANDLE).param("c", contestId)
                .param("i", index).param("at", at).param("v", verdict)
                .update();
    }

    private void givenRating(int rating) {
        db.sql("""
                insert into contest (id, name, type, phase, start_time_seconds)
                values (7777, 'Rating Round', 'CF', 'FINISHED', 1700000000)
                on conflict do nothing
                """).update();
        db.sql("""
                insert into rating_change (contest_id, handle, rank, old_rating, new_rating)
                values (7777, :h, 100, :r, :r) on conflict do nothing
                """).param("h", HANDLE).param("r", rating).update();
    }
}
