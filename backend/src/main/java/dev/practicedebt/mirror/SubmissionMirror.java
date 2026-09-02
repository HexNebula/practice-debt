package dev.practicedebt.mirror;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.practicedebt.cf.CodeforcesClient;
import dev.practicedebt.cf.dto.CfProblem;
import dev.practicedebt.cf.dto.CfRatingChange;
import dev.practicedebt.cf.dto.CfSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mirrors one handle's submission history.
 *
 * <p>Every sync refetches the whole history rather than only what is new. That is deliberate:
 * verdicts are not immutable. A successful hack, a rejudge or a plagiarism sweep rewrites the
 * verdict of a submission made months ago, and an incremental sync that only reads the newest
 * page would keep serving the stale one. A handle's whole history is a few thousand rows.
 */
@Service
public class SubmissionMirror {

    /** Codeforces accepts far larger counts, but a bounded page bounds memory. */
    private static final int PAGE_SIZE = 2000;

    /** Guards against paging forever if upstream ever stops honouring {@code from}. */
    private static final int MAX_PAGES = 100;

    private static final Logger log = LoggerFactory.getLogger(SubmissionMirror.class);

    private final CodeforcesClient codeforces;
    private final SubmissionRepository submissions;
    private final MirrorRunRepository runs;
    private final ContestResultsRepository results;
    private final ProblemRepository problems;

    public SubmissionMirror(CodeforcesClient codeforces, SubmissionRepository submissions,
            MirrorRunRepository runs, ContestResultsRepository results,
            ProblemRepository problems) {
        this.codeforces = codeforces;
        this.submissions = submissions;
        this.runs = runs;
        this.results = results;
        this.problems = problems;
    }

    public static String source(String handle) {
        return "user.status:" + normalise(handle);
    }

    /** Lowercased, because Codeforces handles are case-insensitive. */
    public static String normalise(String handle) {
        return handle.strip().toLowerCase(Locale.ROOT);
    }

    public Result sync(String rawHandle) {
        String handle = normalise(rawHandle);
        long runId = runs.start(source(handle));
        try {
            List<CfSubmission> raw = fetchRaw(handle);
            List<Submission> fetched = raw.stream()
                    .map(s -> toSubmission(handle, s))
                    .flatMap(java.util.Optional::stream)
                    .toList();

            int written = submissions.upsertAll(fetched);
            int discovered = learnProblemsFrom(raw);
            int ratings = mirrorRatingHistory(handle);
            runs.succeed(runId, written);

            log.info("Synced {} submissions, {} problems learned, {} rating changes for {}",
                    written, discovered, ratings, handle);
            return new Result(handle, written, submissions.solvedProblemCount(handle), ratings,
                    discovered);

        } catch (RuntimeException e) {
            runs.fail(runId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Mirrors the handle's own rating history.
     *
     * <p>One extra call per sync, and it is what gives suggestions a difficulty to aim at. It also
     * fills in contests whose full rating change list was never fetched.
     */
    private int mirrorRatingHistory(String handle) {
        List<CfRatingChange> history = codeforces.userRating(handle);
        if (history.isEmpty()) {
            return 0;
        }
        List<ContestResultsRepository.RatingChange> rows = history.stream()
                .map(c -> new ContestResultsRepository.RatingChange(
                        handle, c.rank(), c.oldRating(), c.newRating()))
                .toList();
        int[] contestIds = history.stream().mapToInt(CfRatingChange::contestId).toArray();
        return results.upsertRatingHistory(rows, contestIds);
    }

    /**
     * Mirrors every contest this handle has submitted to that is not yet known.
     *
     * <p>Gym and group contests reach the mirror no other way: {@code contest.list} excludes gym,
     * and {@code problemset.problems} covers only the public problemset. Until a contest is
     * mirrored, a solve inside it is a submission against a problem with no name, no tags and no
     * contest to cite - invisible to the taxonomy and unusable in a reason string.
     *
     * <p>Each unknown contest costs one paced request, so this is a separate step rather than part
     * of every sync.
     */
    public MissingContests mirrorMissingContests(String rawHandle, ContestResultsMirror mirror,
            List<Integer> unknownContestIds) {
        String handle = normalise(rawHandle);
        int mirrored = 0;
        List<String> failures = new ArrayList<>();

        for (int contestId : unknownContestIds) {
            try {
                mirror.ensureMirrored(contestId, false);
                mirrored++;
            } catch (RuntimeException e) {
                // A contest can be genuinely unreachable even when authorised - a mashup that was
                // deleted, or a group the handle has since left. Report it, do not abort the rest.
                log.warn("Could not mirror contest {} for {}: {}", contestId, handle, e.toString());
                failures.add(contestId + ": " + e.getMessage());
            }
        }
        return new MissingContests(unknownContestIds.size(), mirrored, failures);
    }

    public record MissingContests(int found, int mirrored, List<String> failures) {
    }

    private List<CfSubmission> fetchRaw(String handle) {
        List<CfSubmission> all = new ArrayList<>();
        int from = 1;

        for (int page = 0; page < MAX_PAGES; page++) {
            List<CfSubmission> batch = codeforces.userStatus(handle, from, PAGE_SIZE);
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                return all;
            }
            from += PAGE_SIZE;
        }

        log.warn("Stopped paging {} at {} pages; history may be truncated", handle, MAX_PAGES);
        return all;
    }

    /**
     * Drops submissions that cannot be keyed to a contest problem.
     *
     * <p>Problems from named problemsets such as ACMSGURU carry no {@code contestId}. They cannot
     * have been failed in a live contest, so nothing downstream would ever read them.
     */
    private static java.util.Optional<Submission> toSubmission(String handle, CfSubmission s) {
        if (s.problem() == null || s.problem().contestId() == null || s.author() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Submission(
                s.id(),
                handle,
                s.problem().contestId(),
                s.problem().index(),
                s.problem().name(),
                s.creationTimeSeconds(),
                s.relativeTimeSeconds(),
                s.author().participantType(),
                s.verdict(),
                s.programmingLanguage()));
    }

    /**
     * Records every problem a submission refers to.
     *
     * <p>Costs nothing: {@code user.status} already returns a complete problem object with each
     * submission, including tags. This is the only way problems from private gym and group
     * contests can ever be learned - {@code problemset.problems} does not contain them, and
     * {@code contest.standings} refuses those contests even to an authorised caller.
     *
     * <p>Existing rows are preserved where the submission payload is poorer: the problemset mirror
     * is the better source when it has an opinion.
     */
    private int learnProblemsFrom(List<CfSubmission> raw) {
        Map<String, Problem> byId = new LinkedHashMap<>();
        for (CfSubmission s : raw) {
            CfProblem p = s.problem();
            if (p == null || p.contestId() == null || p.index() == null) {
                continue;
            }
            byId.putIfAbsent(p.contestId() + p.index(), new Problem(
                    p.contestId(),
                    p.index(),
                    p.name(),
                    p.type() == null ? "PROGRAMMING" : p.type(),
                    p.rating(),
                    p.points() == null ? null : java.math.BigDecimal.valueOf(p.points()),
                    p.tagsOrEmpty(),
                    null, null, null));
        }
        return problems.upsertFromContest(List.copyOf(byId.values()));
    }

    /**
     * @param problemsLearned problems discovered from the submissions themselves, which is how
     *                        gym and group problems enter the mirror at all
     */
    public record Result(String handle, int submissionsWritten, long solvedProblems,
                         int ratingChangesWritten, int problemsLearned) {
    }
}
