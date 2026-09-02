package dev.practicedebt.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.practicedebt.config.CodeforcesProperties;
import dev.practicedebt.mirror.MirrorRun;
import dev.practicedebt.mirror.MirrorRunRepository;
import dev.practicedebt.mirror.ProblemRepository;
import dev.practicedebt.mirror.ProblemsetMirror;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational view of the mirror.
 *
 * <p>Not a product surface. It exists so the state of the local copy can be inspected without a
 * psql prompt, which matters because every later feature is read off this data.
 */
@RestController
@RequestMapping("/api/mirror")
public class MirrorController {

    private final ProblemsetMirror mirror;
    private final ProblemRepository problems;
    private final MirrorRunRepository runs;
    private final CodeforcesProperties codeforces;

    public MirrorController(ProblemsetMirror mirror, ProblemRepository problems,
            MirrorRunRepository runs, CodeforcesProperties codeforces) {
        this.mirror = mirror;
        this.problems = problems;
        this.runs = runs;
        this.codeforces = codeforces;
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(
                problems.count(),
                runs.lastSuccessAt(ProblemsetMirror.SOURCE).orElse(null),
                // Whether requests are signed, and therefore whether private gym and group
                // submissions are visible at all. Reports the fact, never the credentials.
                codeforces.authenticated(),
                runs.recent(10));
    }

    /** Forces a refresh regardless of staleness. Paced and retried like any other call. */
    @PostMapping("/problemset/refresh")
    public ProblemsetMirror.Result refresh() {
        return mirror.refresh();
    }

    /** Refreshes only if the mirror is older than the configured max age. */
    @PostMapping("/problemset/refresh-if-stale")
    public RefreshIfStale refreshIfStale() {
        Optional<ProblemsetMirror.Result> result = mirror.refreshIfStale();
        return new RefreshIfStale(result.isPresent(), result.orElse(null));
    }

    public record Status(long problemCount, Instant problemsetLastRefreshedAt,
                         boolean codeforcesAuthenticated, List<MirrorRun> recentRuns) {
    }

    public record RefreshIfStale(boolean refreshed, ProblemsetMirror.Result result) {
    }
}
