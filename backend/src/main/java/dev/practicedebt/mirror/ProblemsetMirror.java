package dev.practicedebt.mirror;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.practicedebt.cf.CodeforcesClient;
import dev.practicedebt.cf.dto.CfProblem;
import dev.practicedebt.cf.dto.CfProblemStatistics;
import dev.practicedebt.cf.dto.CfProblemset;
import dev.practicedebt.config.CodeforcesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps a local copy of the shared Codeforces problemset.
 *
 * <p>The problemset is identical for every user, and fetching it per client would be both wasteful
 * and a reliable way to earn a 403 during a live contest. It is fetched on a schedule and read
 * from Postgres everywhere else.
 */
@Service
public class ProblemsetMirror {

    public static final String SOURCE = "problemset.problems";

    private static final Logger log = LoggerFactory.getLogger(ProblemsetMirror.class);

    private final CodeforcesClient codeforces;
    private final ProblemRepository problems;
    private final MirrorRunRepository runs;
    private final CodeforcesProperties props;

    public ProblemsetMirror(CodeforcesClient codeforces, ProblemRepository problems,
            MirrorRunRepository runs, CodeforcesProperties props) {
        this.codeforces = codeforces;
        this.problems = problems;
        this.runs = runs;
        this.props = props;
    }

    /**
     * Refreshes the mirror only if the last successful refresh is older than the configured max
     * age. Returns empty when the mirror was already fresh enough.
     */
    public Optional<Result> refreshIfStale() {
        Duration maxAge = props.mirror().maxAge();
        Optional<Instant> last = runs.lastSuccessAt(SOURCE);
        if (last.isPresent() && Duration.between(last.get(), Instant.now()).compareTo(maxAge) < 0) {
            log.debug("Problemset mirror last refreshed at {}; within max age {}", last.get(), maxAge);
            return Optional.empty();
        }
        return Optional.of(refresh());
    }

    /**
     * Fetches the problemset and writes it to the mirror.
     *
     * <p>The run is recorded before the fetch starts, so a crash mid-fetch leaves a visible RUNNING
     * row rather than silence.
     */
    public Result refresh() {
        long runId = runs.start(SOURCE);
        try {
            CfProblemset fetched = codeforces.problemset();
            List<Problem> mapped = merge(fetched);
            int skipped = fetched.problems().size() - mapped.size();

            int written = problems.upsertAll(mapped);
            runs.succeed(runId, written);

            log.info("Problemset mirror refreshed: {} problems written, {} skipped, {} rows now",
                    written, skipped, problems.count());
            return new Result(runId, written, skipped);

        } catch (RuntimeException e) {
            runs.fail(runId, describe(e));
            throw e;
        }
    }

    /**
     * Joins the two parallel lists Codeforces returns into one row per problem.
     *
     * <p>Problems with no {@code contestId} are dropped: those belong to named problemsets such as
     * ACMSGURU, cannot be failed in a live contest, and have no contest to name in a reason string.
     */
    private static List<Problem> merge(CfProblemset fetched) {
        Map<String, Integer> solvedCounts = new HashMap<>();
        for (CfProblemStatistics stat : fetched.problemStatistics()) {
            if (stat.contestId() != null) {
                solvedCounts.put(key(stat.contestId(), stat.index()), stat.solvedCount());
            }
        }

        return fetched.problems().stream()
                .filter(p -> p.contestId() != null && p.index() != null)
                .map(p -> toProblem(p, solvedCounts.get(key(p.contestId(), p.index()))))
                .toList();
    }

    private static Problem toProblem(CfProblem p, Integer solvedCount) {
        return new Problem(
                p.contestId(),
                p.index(),
                p.name(),
                p.type(),
                p.rating(),
                p.points() == null ? null : BigDecimal.valueOf(p.points()),
                p.tagsOrEmpty(),
                solvedCount,
                null,
                null);
    }

    private static String key(int contestId, String index) {
        return contestId + "/" + index;
    }

    private static String describe(RuntimeException e) {
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    /** What one refresh did. {@code skipped} counts problems with no contest of their own. */
    public record Result(long runId, int written, int skipped) {
    }
}
