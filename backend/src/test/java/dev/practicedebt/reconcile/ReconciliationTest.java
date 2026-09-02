package dev.practicedebt.reconcile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.practicedebt.debt.AbandonedDebtService;
import dev.practicedebt.decay.DecayedDebtService;
import dev.practicedebt.queue.QueueItem;
import dev.practicedebt.queue.QueueService;
import dev.practicedebt.support.PostgresIntegrationTest;
import dev.practicedebt.taxonomy.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec's correctness bar: "numbers shown in different views reconcile exactly".
 *
 * <p>Not a bug hunt for one calculation, but a check that three views of the same data cannot drift
 * apart. A total that disagrees with the list beneath it is the sort of thing a reader notices once
 * and then stops trusting everything else on the page.
 */
class ReconciliationTest extends PostgresIntegrationTest {

    private static final String HANDLE = "author";

    @Autowired
    private QueueService queue;

    @Autowired
    private AbandonedDebtService abandoned;

    @Autowired
    private DecayedDebtService decayed;

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private JdbcClient db;

    private long nextSubmissionId = 1;

    @BeforeEach
    void reset() {
        db.sql("delete from technique_return").update();
        db.sql("delete from technique_snapshot").update();
        db.sql("delete from problem_technique").update();
        db.sql("delete from debt_rating_cost").update();
        db.sql("delete from submission").update();
        db.sql("delete from problem").update();
        db.sql("delete from contest").update();
        nextSubmissionId = 1;
        givenAHistory();
    }

    @Test
    @DisplayName("the queue's counts equal the lists they summarise")
    void queueCountsMatchItsOwnItems() {
        QueueService.Queue result = queue.forHandle(HANDLE);

        long abandonedItems = result.items().stream()
                .filter(i -> i.source() == QueueItem.Source.ABANDONED).count();
        long decayedItems = result.items().stream()
                .filter(i -> i.source() == QueueItem.Source.DECAYED).count();

        assertThat(result.abandonedCount()).isEqualTo(abandonedItems);
        assertThat(result.decayedCount()).isEqualTo(decayedItems);
        assertThat(result.items()).hasSize((int) (abandonedItems + decayedItems));
    }

    @Test
    @DisplayName("the queue agrees with the two reports it is built from")
    void queueAgreesWithItsSources() {
        QueueService.Queue result = queue.forHandle(HANDLE);
        AbandonedDebtService.Report abandonedReport = abandoned.reportFor(HANDLE);
        DecayedDebtService.Report decayedReport = decayed.reportFor(HANDLE);

        assertThat(result.abandonedCount()).isEqualTo(abandonedReport.items().size());
        assertThat(result.decayedCount()).isEqualTo(decayedReport.items().size());
        assertThat(result.unattributable()).isEqualTo(abandonedReport.unattributable());
        assertThat(result.withheldDecayed()).isEqualTo(decayedReport.withheld());
    }

    @Test
    @DisplayName("every technique is counted exactly once, and they add up to the taxonomy")
    void decayBucketsPartitionTheTaxonomy() {
        // A technique with one or two solves used to be counted as both fresh and
        // never-established, so the figures on screen exceeded the number of techniques.
        DecayedDebtService.Report report = decayed.reportFor(HANDLE);

        long declared = report.items().size() + report.withheld() + report.fresh()
                + report.neverEstablished() + report.untouched();

        assertThat(report.reconciles()).isTrue();
        assertThat(declared).isEqualTo(report.techniques());
        assertThat(report.techniques()).isEqualTo(techniqueCount());
    }

    @Test
    @DisplayName("asking twice gives the same answer")
    void repeatedReadsAgree() {
        QueueService.Queue first = queue.forHandle(HANDLE);
        QueueService.Queue second = queue.forHandle(HANDLE);

        assertThat(second.items()).extracting(QueueItem::id)
                .isEqualTo(first.items().stream().map(QueueItem::id).toList());
        assertThat(second.abandonedCount()).isEqualTo(first.abandonedCount());
        assertThat(second.decayedCount()).isEqualTo(first.decayedCount());
    }

    @Test
    @DisplayName("handle casing changes nothing")
    void caseDoesNotChangeTheNumbers() {
        QueueService.Queue lower = queue.forHandle(HANDLE);
        QueueService.Queue mixed = queue.forHandle("AuThOr");

        assertThat(mixed.items()).hasSameSizeAs(lower.items());
        assertThat(mixed.abandonedCount()).isEqualTo(lower.abandonedCount());
        assertThat(mixed.decayedCount()).isEqualTo(lower.decayedCount());
    }

    @Test
    @DisplayName("no queue item is a problem the handle has solved")
    void solvedProblemsNeverAppear() {
        // The spec calls this the one bug that destroys trust, so it is asserted at the queue -
        // the surface a person actually reads - and not only at the query that derives it.
        List<String> solved = db.sql("""
                        select distinct contest_id || problem_index
                          from submission where handle = :h and verdict = 'OK'
                        """).param("h", HANDLE).query(String.class).list();

        assertThat(solved).isNotEmpty();
        assertThat(queue.forHandle(HANDLE).items())
                .filteredOn(i -> i.source() == QueueItem.Source.ABANDONED)
                .extracting(QueueItem::id)
                .doesNotContainAnyElementsOf(solved);
    }

    @Test
    @DisplayName("an unknown handle reports zeroes that still add up")
    void emptyHandleStillReconciles() {
        QueueService.Queue result = queue.forHandle("nobody");
        DecayedDebtService.Report report = decayed.reportFor("nobody");

        assertThat(result.items()).isEmpty();
        assertThat(result.abandonedCount()).isZero();
        assertThat(result.decayedCount()).isZero();
        assertThat(report.reconciles()).isTrue();
        assertThat(report.untouched()).isEqualTo(report.techniques());
    }

    // --- fixtures ---

    /** A handle with one abandoned problem, one solved problem, and a technique gone quiet. */
    private void givenAHistory() {
        db.sql("""
                insert into contest (id, name, type, phase, frozen, duration_seconds, start_time_seconds)
                values (4242, 'Reconciliation Round', 'CF', 'FINISHED', false, 7200, 1700000000)
                """).update();

        // Failed live, never solved: abandoned debt.
        problem(4242, "A", 1500, "dp", "trees");
        submission(4242, "A", "CONTESTANT", "WRONG_ANSWER", daysAgo(500));

        // A technique with enough solves, long ago: decayed debt.
        for (int i = 0; i < 4; i++) {
            problem(5000 + i, "A", 1500, "dp", "trees");
            submission(5000 + i, "A", "PRACTICE", "OK", daysAgo(400 + i));
        }
        // A technique touched twice only: established too weakly to be debt.
        for (int i = 0; i < 2; i++) {
            problem(6000 + i, "A", 1500, "geometry");
            submission(6000 + i, "A", "PRACTICE", "OK", daysAgo(400));
        }
        taxonomy.apply();
    }

    private long techniqueCount() {
        return db.sql("select count(*) from technique").query(Long.class).single();
    }

    private static long daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days)).getEpochSecond();
    }

    private void problem(int contestId, String index, int rating, String... tags) {
        db.sql("""
                        insert into problem (contest_id, problem_index, name, type, rating, tags)
                        values (:c, :i, :n, 'PROGRAMMING', :r, :t) on conflict do nothing
                        """)
                .param("c", contestId).param("i", index).param("n", "p" + contestId + index)
                .param("r", rating).param("t", tags)
                .update();
    }

    private void submission(int contestId, String index, String type, String verdict, long at) {
        db.sql("""
                        insert into submission (id, handle, contest_id, problem_index, problem_name,
                                creation_time_seconds, relative_time_seconds, participant_type, verdict)
                        values (:id, :h, :c, :i, 'p', :at, 600, :type, :v)
                        """)
                .param("id", nextSubmissionId++).param("h", HANDLE).param("c", contestId)
                .param("i", index).param("at", at).param("type", type).param("v", verdict)
                .update();
    }
}
