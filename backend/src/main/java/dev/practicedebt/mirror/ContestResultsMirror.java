package dev.practicedebt.mirror;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import dev.practicedebt.cf.CodeforcesApiException;
import dev.practicedebt.cf.CodeforcesClient;
import dev.practicedebt.cf.dto.CfContest;
import dev.practicedebt.cf.dto.CfProblem;
import dev.practicedebt.cf.dto.CfRanklistRow;
import dev.practicedebt.cf.dto.CfRatingChange;
import dev.practicedebt.cf.dto.CfStandings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mirrors one contest's ranklist and rating changes.
 *
 * <p>Expensive and therefore fetched once and kept: a Div. 4 ranklist is around 16,000 rows and
 * 15 MB, and Codeforces refuses to page or filter it for non-admin callers. Both fetches are
 * skipped when the contest is already mirrored, because a finished contest's results do not change.
 */
@Service
public class ContestResultsMirror {

    private static final Logger log = LoggerFactory.getLogger(ContestResultsMirror.class);

    private final CodeforcesClient codeforces;
    private final ContestResultsRepository results;
    private final MirrorRunRepository runs;
    private final ContestRepository contests;
    private final ProblemRepository problems;

    public ContestResultsMirror(CodeforcesClient codeforces, ContestResultsRepository results,
            MirrorRunRepository runs, ContestRepository contests, ProblemRepository problems) {
        this.codeforces = codeforces;
        this.results = results;
        this.runs = runs;
        this.contests = contests;
        this.problems = problems;
    }

    /**
     * Ensures both the ranklist and the rating changes for a contest are mirrored.
     *
     * <p>Whether a fetch already happened is answered from the run history, not from whether rows
     * landed. An unrated contest legitimately produces zero rating changes, and asking "are there
     * rows?" would refetch it on every single pass forever.
     *
     * @param force refetch even when a copy is already held
     * @return true if anything was fetched
     */
    public boolean ensureMirrored(int contestId, boolean force) {
        boolean fetched = false;
        if (force || !alreadyFetched(standingsSource(contestId))) {
            mirrorStandings(contestId);
            fetched = true;
        }
        if (force || !alreadyFetched(ratingChangesSource(contestId))) {
            mirrorRatingChanges(contestId);
            fetched = true;
        }
        return fetched;
    }

    public static String standingsSource(int contestId) {
        return "contest.standings:" + contestId;
    }

    public static String ratingChangesSource(int contestId) {
        return "contest.ratingChanges:" + contestId;
    }

    private boolean alreadyFetched(String source) {
        return runs.lastSuccessAt(source).isPresent();
    }

    private void mirrorStandings(int contestId) {
        long runId = runs.start(standingsSource(contestId));
        try {
            CfStandings standings = codeforces.contestStandings(contestId);

            List<ContestResultsRepository.StandingsRow> rows = standings.rows().stream()
                    .map(ContestResultsMirror::toRow)
                    .toList();
            List<ContestResultsRepository.ContestProblem> contestProblems = standings.problems().stream()
                    .map(ContestResultsMirror::toContestProblem)
                    .toList();

            results.replaceStandings(contestId, rows, contestProblems);

            // A standings response also carries the contest itself and its problem list. For gym
            // and group contests that is the only place they appear at all: contest.list excludes
            // gym, and problemset.problems covers only the public problemset. Without this, a
            // solved gym problem is a submission against something the system cannot name.
            int contestsWritten = contests.upsertAll(List.of(toContest(standings.contest())));
            int problemsWritten = problems.upsertFromContest(standings.problems().stream()
                    .filter(p -> p.contestId() != null && p.index() != null)
                    .map(p -> toProblem(contestId, p))
                    .toList());

            runs.succeed(runId, rows.size());
            log.info("Mirrored contest {}: {} standings rows, {} problems, contest row {}",
                    contestId, rows.size(), problemsWritten, contestsWritten > 0 ? "written" : "unchanged");

        } catch (RuntimeException e) {
            runs.fail(runId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private void mirrorRatingChanges(int contestId) {
        long runId = runs.start(ratingChangesSource(contestId));
        try {
            List<CfRatingChange> fetched;
            try {
                fetched = codeforces.contestRatingChanges(contestId);
            } catch (CodeforcesApiException e) {
                if (!unratedContest(e)) {
                    throw e;
                }
                // An unrated contest does not answer with an empty list, it refuses outright with
                // HTTP 400. That is a fact about the contest, not a failure to mirror it: there was
                // no rating at stake, so a debt item from it costs nothing by definition.
                log.info("Contest {} is unrated: {}", contestId, e.comment());
                results.replaceRatingChanges(contestId, List.of());
                runs.succeed(runId, 0);
                return;
            }
            List<ContestResultsRepository.RatingChange> changes = fetched.stream()
                    .map(c -> new ContestResultsRepository.RatingChange(
                            c.handle().toLowerCase(Locale.ROOT), c.rank(), c.oldRating(), c.newRating()))
                    .toList();

            results.replaceRatingChanges(contestId, changes);
            runs.succeed(runId, changes.size());

            log.info("Mirrored {} rating changes for contest {}", changes.size(), contestId);

        } catch (RuntimeException e) {
            runs.fail(runId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    /** Codeforces says this in as many words when a contest was never rated. */
    private static boolean unratedContest(CodeforcesApiException e) {
        String comment = e.comment();
        return comment != null && comment.contains("Rating changes are unavailable");
    }

    private static ContestResultsRepository.StandingsRow toRow(CfRanklistRow row) {
        String partyKey = row.party().members().stream()
                .map(m -> m.handle().toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + "," + b)
                .orElse("ghost-" + row.party().participantId());
        BigDecimal points = row.points() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(row.points());
        return new ContestResultsRepository.StandingsRow(
                partyKey, row.rank(), points, row.penalty() == null ? 0 : row.penalty());
    }

    private static Contest toContest(CfContest c) {
        return new Contest(c.id(), c.name(), c.type(), c.phase(), c.frozen(),
                c.durationSeconds(), c.startTimeSeconds());
    }

    /** A problem as learned from a ranklist. Solved counts are not available here. */
    private static Problem toProblem(int contestId, CfProblem p) {
        return new Problem(
                p.contestId() == null ? contestId : p.contestId(),
                p.index(),
                p.name(),
                p.type() == null ? "PROGRAMMING" : p.type(),
                p.rating(),
                p.points() == null ? null : java.math.BigDecimal.valueOf(p.points()),
                p.tagsOrEmpty(),
                null,
                null,
                null);
    }

    private static ContestResultsRepository.ContestProblem toContestProblem(CfProblem problem) {
        return new ContestResultsRepository.ContestProblem(
                problem.index(),
                problem.points() == null ? null : BigDecimal.valueOf(problem.points()));
    }
}
