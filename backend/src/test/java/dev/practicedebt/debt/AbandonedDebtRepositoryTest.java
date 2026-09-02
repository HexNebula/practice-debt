package dev.practicedebt.debt;

import java.util.List;

import dev.practicedebt.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The derivation, tested against the database that runs it.
 *
 * <p>Timestamps are seconds since the epoch because that is what Codeforces returns; the constants
 * below are arbitrary but ordered.
 */
class AbandonedDebtRepositoryTest extends PostgresIntegrationTest {

    private static final String HANDLE = "author";
    private static final long CONTEST_START = 1_700_000_000L;

    @Autowired
    private AbandonedDebtRepository repository;

    @Autowired
    private JdbcClient db;

    private long nextSubmissionId = 1;

    @BeforeEach
    void reset() {
        db.sql("delete from submission").update();
        db.sql("delete from problem").update();
        db.sql("delete from contest").update();
        nextSubmissionId = 1;
    }

    @Test
    @DisplayName("a problem failed live and never solved is debt, and says which contest")
    void surfacesAnUnsolvedLiveFailure() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "D", "Manhattan Circle", 1200, "geometry", "implementation");
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);

        List<AbandonedDebtItem> debt = repository.find(HANDLE);

        assertThat(debt).hasSize(1);
        AbandonedDebtItem item = debt.get(0);
        assertThat(item.problemId()).isEqualTo("1985D");
        assertThat(item.problemName()).isEqualTo("Manhattan Circle");
        assertThat(item.rating()).isEqualTo(1200);
        assertThat(item.tags()).containsExactly("geometry", "implementation");
        assertThat(item.contestName()).isEqualTo("Codeforces Round 952 (Div. 4)");
        assertThat(item.liveAttempts()).isEqualTo(1);
        assertThat(item.url()).isEqualTo("https://codeforces.com/contest/1985/problem/D");
    }

    @Test
    @DisplayName("THE invariant: a problem solved later in practice is never debt")
    void aProblemSolvedInPracticeIsNotDebt() {
        // This is the bug the spec says destroys trust. It is tested first-class, not implied.
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "D", "Manhattan Circle", 1200);
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);
        givenSubmission(1985, "D", "CONTESTANT", "TIME_LIMIT_EXCEEDED", CONTEST_START + 900);
        givenSubmission(1985, "D", "PRACTICE", "OK", CONTEST_START + 999_999);

        assertThat(repository.find(HANDLE)).isEmpty();
    }

    @Test
    @DisplayName("solved during the contest itself is not debt either")
    void aProblemSolvedInContestIsNotDebt() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "A", "Creating Words", 800);
        givenSubmission(1985, "A", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 100);
        givenSubmission(1985, "A", "CONTESTANT", "OK", CONTEST_START + 200);

        assertThat(repository.find(HANDLE)).isEmpty();
    }

    @Test
    @DisplayName("a virtual solve still clears debt, even though a virtual failure never creates it")
    void anAcceptedSubmissionInAnyModeClearsTheDebt() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "E", "Secret Box", 1700);
        givenSubmission(1985, "E", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);
        givenSubmission(1985, "E", "VIRTUAL", "OK", CONTEST_START + 500_000);

        assertThat(repository.find(HANDLE)).isEmpty();
    }

    @Test
    @DisplayName("failing only in practice is not debt")
    void practiceFailuresAreNotDebt() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "F", "Final Boss", 2000);
        givenSubmission(1985, "F", "PRACTICE", "WRONG_ANSWER", CONTEST_START + 900_000);

        assertThat(repository.find(HANDLE)).isEmpty();
    }

    @Test
    @DisplayName("the participation policy excludes virtual and out-of-competition failures")
    void onlyLiveParticipationCreatesDebt() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "F", "Final Boss", 2000);
        givenProblem(1985, "G", "Hills", 2400);
        givenSubmission(1985, "F", "VIRTUAL", "WRONG_ANSWER", CONTEST_START + 900_000);
        givenSubmission(1985, "G", "OUT_OF_COMPETITION", "WRONG_ANSWER", CONTEST_START + 700);

        assertThat(repository.find(HANDLE)).isEmpty();
        assertThat(ParticipationPolicy.liveParticipationTypes()).containsExactly("CONTESTANT");
    }

    @Test
    @DisplayName("live attempts are counted, and non-live attempts are not counted among them")
    void countsOnlyLiveAttempts() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "D", "Manhattan Circle", 1200);
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);
        givenSubmission(1985, "D", "CONTESTANT", "RUNTIME_ERROR", CONTEST_START + 800);
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 1200);
        givenSubmission(1985, "D", "PRACTICE", "WRONG_ANSWER", CONTEST_START + 900_000);

        AbandonedDebtItem item = repository.find(HANDLE).get(0);

        assertThat(item.liveAttempts()).isEqualTo(3);
        assertThat(item.firstAttemptedAt().getEpochSecond()).isEqualTo(CONTEST_START + 600);
        assertThat(item.lastAttemptedAt().getEpochSecond()).isEqualTo(CONTEST_START + 1200);
    }

    @Test
    @DisplayName("another handle's accepted submission does not clear this handle's debt")
    void debtIsPerHandle() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "D", "Manhattan Circle", 1200);
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);
        givenSubmissionFor("someone-else", 1985, "D", "PRACTICE", "OK", CONTEST_START + 900_000);

        assertThat(repository.find(HANDLE)).hasSize(1);
    }

    @Test
    @DisplayName("handles are matched case-insensitively")
    void handleLookupIgnoresCase() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenProblem(1985, "D", "Manhattan Circle", 1200);
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);

        assertThat(repository.find("AuThOr")).hasSize(1);
    }

    @Test
    @DisplayName("a failure whose contest is not mirrored is excluded, but counted")
    void unmirroredContestsAreExcludedAndCounted() {
        // Gym rounds arrive in user.status but never in contest.list. An item that cannot name its
        // contest cannot explain itself, so it is withheld - visibly, not silently.
        givenSubmission(100_001, "A", "CONTESTANT", "WRONG_ANSWER", CONTEST_START);

        assertThat(repository.find(HANDLE)).isEmpty();
        assertThat(repository.unattributableCount(HANDLE)).isEqualTo(1);
    }

    @Test
    @DisplayName("a problem missing from the problemset mirror still renders")
    void fallsBackToTheSubmissionsProblemName() {
        givenContest(1985, "Codeforces Round 952 (Div. 4)");
        givenSubmission(1985, "D", "CONTESTANT", "WRONG_ANSWER", CONTEST_START + 600);

        AbandonedDebtItem item = repository.find(HANDLE).get(0);

        assertThat(item.problemName()).isEqualTo("problem 1985D");
        assertThat(item.rating()).isNull();
        assertThat(item.tags()).isEmpty();
    }

    @Test
    @DisplayName("a handle with no submissions at all reports cleanly")
    void anUnknownHandleIsEmptyRatherThanAnError() {
        assertThat(repository.find("nobody")).isEmpty();
        assertThat(repository.unattributableCount("nobody")).isZero();
    }

    @Test
    @DisplayName("a handle who has only ever practised reports cleanly, not emptily by accident")
    void aHandleWithNoContestHistoryRendersCleanly() {
        // Taken from a real account: every submission accepted, every one in practice, one of them
        // in a gym contest that is not mirrored. The correct answer is an empty queue, and it must
        // arrive as an empty queue rather than an error or a stray unattributable count.
        givenContest(535, "Codeforces Round 299 (Div. 2)");
        givenProblem(535, "B", "Tavas and SaDDas", 1100);
        givenSubmission(535, "B", "PRACTICE", "OK", CONTEST_START);
        givenSubmission(102961, "E", "PRACTICE", "OK", CONTEST_START);

        assertThat(repository.find(HANDLE)).isEmpty();
        assertThat(repository.unattributableCount(HANDLE)).isZero();
    }

    @Test
    @DisplayName("newest contest first")
    void ordersByContestRecency() {
        givenContest(1000, "Old Round", CONTEST_START - 5_000_000);
        givenContest(2000, "Recent Round", CONTEST_START);
        givenSubmission(1000, "A", "CONTESTANT", "WRONG_ANSWER", CONTEST_START - 5_000_000);
        givenSubmission(2000, "A", "CONTESTANT", "WRONG_ANSWER", CONTEST_START);

        assertThat(repository.find(HANDLE))
                .extracting(AbandonedDebtItem::contestId)
                .containsExactly(2000, 1000);
    }

    // --- fixtures ---

    private void givenContest(int id, String name) {
        givenContest(id, name, CONTEST_START);
    }

    private void givenContest(int id, String name, long startSeconds) {
        db.sql("""
                        insert into contest (id, name, type, phase, frozen, duration_seconds,
                                             start_time_seconds)
                        values (:id, :name, 'CF', 'FINISHED', false, 7200, :start)
                        """)
                .param("id", id)
                .param("name", name)
                .param("start", startSeconds)
                .update();
    }

    private void givenProblem(int contestId, String index, String name, Integer rating,
            String... tags) {
        db.sql("""
                        insert into problem (contest_id, problem_index, name, type, rating, tags)
                        values (:contestId, :index, :name, 'PROGRAMMING', :rating, :tags)
                        """)
                .param("contestId", contestId)
                .param("index", index)
                .param("name", name)
                .param("rating", rating)
                .param("tags", tags)
                .update();
    }

    private void givenSubmission(int contestId, String index, String participantType,
            String verdict, long createdAt) {
        givenSubmissionFor(HANDLE, contestId, index, participantType, verdict, createdAt);
    }

    private void givenSubmissionFor(String handle, int contestId, String index,
            String participantType, String verdict, long createdAt) {
        db.sql("""
                        insert into submission (id, handle, contest_id, problem_index, problem_name,
                                                creation_time_seconds, participant_type, verdict,
                                                programming_language)
                        values (:id, :handle, :contestId, :index, :problemName, :createdAt,
                                :participantType, :verdict, 'C++20')
                        """)
                .param("id", nextSubmissionId++)
                .param("handle", handle)
                .param("contestId", contestId)
                .param("index", index)
                .param("problemName", "problem " + contestId + index)
                .param("createdAt", createdAt)
                .param("participantType", participantType)
                .param("verdict", verdict)
                .update();
    }
}
