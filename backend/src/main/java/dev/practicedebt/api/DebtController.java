package dev.practicedebt.api;

import dev.practicedebt.debt.AbandonedDebtService;
import dev.practicedebt.mirror.ContestMirror;
import dev.practicedebt.mirror.ContestResultsMirror;
import dev.practicedebt.mirror.SubmissionRepository;
import dev.practicedebt.mirror.SubmissionMirror;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-handle sync and the abandoned-debt list.
 *
 * <p>Reads never touch Codeforces. Syncing is an explicit action, so a slow or unavailable upstream
 * cannot make looking at the queue slow or unavailable.
 */
@RestController
@RequestMapping("/api/handles/{handle}")
public class DebtController {

    private final SubmissionMirror submissions;
    private final ContestMirror contests;
    private final AbandonedDebtService debt;
    private final ContestResultsMirror contestResults;
    private final SubmissionRepository submissionRepository;

    public DebtController(SubmissionMirror submissions, ContestMirror contests,
            AbandonedDebtService debt, ContestResultsMirror contestResults,
            SubmissionRepository submissionRepository) {
        this.submissions = submissions;
        this.contests = contests;
        this.debt = debt;
        this.contestResults = contestResults;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Pulls this handle's submission history, and refreshes the contest list first so every
     * failure found has a contest that can be named.
     */
    @PostMapping("/sync")
    public SyncResult sync(@PathVariable String handle) {
        int contestsWritten = contests.refresh();
        SubmissionMirror.Result result = submissions.sync(handle);
        return new SyncResult(contestsWritten, result);
    }

    /**
     * Mirrors gym and group contests this handle has submitted to.
     *
     * <p>Needs an API key for private ones: Codeforces denies they exist to an anonymous caller.
     */
    @PostMapping("/mirror-missing-contests")
    public SubmissionMirror.MissingContests mirrorMissingContests(@PathVariable String handle) {
        return submissions.mirrorMissingContests(handle, contestResults,
                submissionRepository.unknownContestIds(SubmissionMirror.normalise(handle)));
    }

    @GetMapping("/debt/abandoned")
    public AbandonedDebtService.Report abandoned(@PathVariable String handle) {
        return debt.reportFor(handle);
    }

    /**
     * Works out what each abandoned item cost in rating, and stores it.
     *
     * <p>Deliberately a separate step from reading the queue: every item needs its contest's whole
     * ranklist and rating change list mirrored, which is tens of megabytes paced at one request per
     * two seconds. Expect minutes on a first run, and reuse afterwards.
     */
    @PostMapping("/debt/abandoned/cost")
    public AbandonedDebtService.ComputeResult computeCosts(@PathVariable String handle) {
        return debt.computeCosts(handle);
    }

    public record SyncResult(int contestsWritten, SubmissionMirror.Result submissions) {
    }
}
