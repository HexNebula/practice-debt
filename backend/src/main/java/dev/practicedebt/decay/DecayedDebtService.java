package dev.practicedebt.decay;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import dev.practicedebt.mirror.SubmissionMirror;
import dev.practicedebt.taxonomy.TaxonomyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decayed debt: techniques solved before and not touched since.
 *
 * <p>Passive by design. Nothing here asks the author to review anything - freshness is read off
 * work they were going to do anyway, and a technique they have been practising simply never
 * appears. Only the quiet ones surface.
 */
@Service
public class DecayedDebtService {

    private static final int SUGGESTIONS_PER_TECHNIQUE = 3;

    /**
     * How many decayed techniques to surface at once.
     *
     * <p>Someone who has been away for a year has technically forgotten everything, and listing all
     * 34 techniques would be true and useless. A queue that reports everything reports nothing.
     */
    private static final int MAX_ITEMS = 10;

    private static final Logger log = LoggerFactory.getLogger(DecayedDebtService.class);

    private final TechniqueActivityRepository activity;
    private final TechniqueSnapshotRepository snapshots;
    private final TechniqueReturnRepository returns;
    private final TaxonomyLoader taxonomy;

    public DecayedDebtService(TechniqueActivityRepository activity,
            TechniqueSnapshotRepository snapshots, TechniqueReturnRepository returns,
            TaxonomyLoader taxonomy) {
        this.activity = activity;
        this.snapshots = snapshots;
        this.returns = returns;
        this.taxonomy = taxonomy;
    }

    public Report reportFor(String rawHandle) {
        String handle = SubmissionMirror.normalise(rawHandle);
        List<TechniqueActivity> all = activity.activityFor(handle);
        int anchor = anchorRating(handle);

        List<DecayedDebtItem> decayed = all.stream()
                .filter(TechniqueActivity::isDecayed)
                .map(a -> toItem(handle, a, anchor))
                // Most skill at risk first. Sorting by staleness alone puts whatever was practised
                // least at the top, which is precisely backwards: it ranks the technique the author
                // barely learned above the one they built up over a hundred problems.
                .sorted(Comparator.comparingDouble(DecayedDebtItem::skillAtRisk).reversed())
                .toList();

        List<DecayedDebtItem> items = decayed.stream().limit(MAX_ITEMS).toList();
        long withheld = decayed.size() - items.size();

        // These four buckets partition the taxonomy: every technique lands in exactly one, and
        // together they account for all of it. The earlier version counted a technique with one or
        // two solves as both fresh and never-established, so the figures on screen did not add up
        // to the number of techniques that exist - which is exactly the kind of quiet
        // inconsistency that makes a reader stop trusting the rest of the page.
        long fresh = all.stream()
                .filter(a -> a.solvedCount() >= DecayPolicy.MIN_SOLVES_TO_COUNT && !a.isDecayed())
                .count();
        long never = all.stream()
                .filter(a -> a.solvedCount() > 0 && a.solvedCount() < DecayPolicy.MIN_SOLVES_TO_COUNT)
                .count();
        long untouched = all.stream().filter(a -> a.solvedCount() == 0).count();

        return new Report(items, withheld, fresh, never, untouched, all.size(), anchor,
                DecayPolicy.HALF_LIFE_DAYS, DecayPolicy.describe(), returns.calibration(handle));
    }

    /**
     * Records snapshots and recomputes the calibration evidence.
     *
     * <p>Both are cheap and read only mirrored data, so this can be run as often as the mirror is
     * refreshed. Snapshots are append-only: each run adds a new observation rather than replacing
     * the last, because the history is the point.
     */
    public SnapshotResult snapshot(String rawHandle) {
        String handle = SubmissionMirror.normalise(rawHandle);
        int version = taxonomy.load().version();

        int written = snapshots.take(handle, activity.activityFor(handle), version);
        int recorded = returns.recordReturns(handle, version);

        log.info("Snapshot for {}: {} techniques recorded, {} returns observed", handle, written,
                recorded);
        return new SnapshotResult(written, recorded, snapshots.countFor(handle));
    }

    public List<TechniqueSnapshotRepository.Point> history(String handle, String techniqueId) {
        return snapshots.history(handle, techniqueId);
    }

    private DecayedDebtItem toItem(String handle, TechniqueActivity a, int anchor) {
        List<TechniqueActivityRepository.Suggestion> suggestions =
                activity.suggestionsFor(handle, a.techniqueId(), anchor, SUGGESTIONS_PER_TECHNIQUE);

        return new DecayedDebtItem(a.techniqueId(), a.name(), a.family(), a.solvedCount(),
                a.lastSolvedAt(), a.daysSinceLast(), a.retention(),
                a.solvedCount() * (1 - a.retention()), suggestions,
                reasonFor(a, suggestions.isEmpty()));
    }

    /**
     * The reason names the technique, when it was last touched, and how much of it was ever built -
     * enough for the author to disagree with the item on sight, which is the point.
     */
    static String reasonFor(TechniqueActivity a, boolean noSuggestions) {
        String reason = String.format(
                "%s: last solved %s ago, %d problems solved in total — a guessed %d-day half-life "
                        + "puts you at roughly %d%% retained",
                a.name(), humanise(a.daysSinceLast()), a.solvedCount(),
                DecayPolicy.HALF_LIFE_DAYS, Math.round(a.retention() * 100));

        if (noSuggestions) {
            // An item that cannot name an action is close to useless, so it says so plainly
            // rather than appearing actionable and then offering nothing.
            reason += " (no unsolved problems mapped to this technique near your rating)";
        }
        return reason;
    }

    private static String humanise(int days) {
        if (days < 45) {
            return days + " days";
        }
        if (days < 365) {
            return Math.round(days / 30.0) + " months";
        }
        long years = Math.round(days / 365.0);
        return years == 1 ? "a year" : years + " years";
    }

    private int anchorRating(String handle) {
        Integer rating = activity.currentRating(handle);
        return rating == null ? DecayPolicy.DEFAULT_RATING_ANCHOR : rating;
    }

    /**
     * Every technique falls into exactly one bucket, and the buckets account for all of them.
     *
     * @param items            decayed techniques surfaced, capped for readability
     * @param withheld         decayed techniques below the cap
     * @param fresh            established and still warm, deliberately not listed - a queue that
     *                         reports everything reports nothing
     * @param neverEstablished touched, but too few times to have been forgotten: a gap, not a debt
     * @param untouched        never solved at all
     * @param techniques       the taxonomy's size, so the arithmetic can be checked on sight
     */
    public record Report(List<DecayedDebtItem> items, long withheld, long fresh,
                         long neverEstablished, long untouched, long techniques,
                         int suggestionAnchorRating, int halfLifeDays, String policy,
                         List<TechniqueReturnRepository.Calibration> calibration) {

        /** The buckets must add up to the taxonomy. Asserted in tests, and cheap to check here. */
        public boolean reconciles() {
            return items.size() + withheld + fresh + neverEstablished + untouched == techniques;
        }
    }

    public record SnapshotResult(int techniquesRecorded, int returnsObserved, long totalSnapshots) {
    }
}
