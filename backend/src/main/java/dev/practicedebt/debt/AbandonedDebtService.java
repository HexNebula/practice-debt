package dev.practicedebt.debt;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.practicedebt.mirror.SubmissionMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns rows into items that explain themselves.
 *
 * <p>The reason string is the product. The spec is blunt about it: an item that cannot say why it
 * is in the queue does not ship, because the reason is what makes the ranking trustworthy.
 */
@Service
public class AbandonedDebtService {

    private static final Logger log = LoggerFactory.getLogger(AbandonedDebtService.class);

    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMMM yyyy").withZone(ZoneOffset.UTC);

    private final AbandonedDebtRepository repository;
    private final DebtRatingCostRepository costs;
    private final RatingCostCalculator calculator;

    public AbandonedDebtService(AbandonedDebtRepository repository, DebtRatingCostRepository costs,
            RatingCostCalculator calculator) {
        this.repository = repository;
        this.costs = costs;
        this.calculator = calculator;
    }

    public Report reportFor(String rawHandle) {
        String handle = SubmissionMirror.normalise(rawHandle);
        Map<String, RatingCost> known = costs.findAll(handle);

        List<AbandonedDebtItem> items = repository.find(handle).stream()
                .map(item -> describe(item, known.get(item.problemId())))
                // Most expensive first. Items whose cost has not been computed yet sort last
                // rather than pretending to be free.
                .sorted(Comparator
                        .comparing((AbandonedDebtItem i) -> costOrNull(i) == null ? 1 : 0)
                        .thenComparing(i -> -Optional.ofNullable(costOrNull(i)).orElse(0))
                        .thenComparing(i -> -i.contestId()))
                .toList();

        return new Report(items, repository.unattributableCount(handle),
                ParticipationPolicy.describe(), CounterfactualPolicy.describe());
    }

    /**
     * Computes and stores the rating cost of every abandoned item for a handle.
     *
     * <p>Separate from reading the queue on purpose. Each item needs its contest's whole ranklist
     * and rating change list mirrored - tens of megabytes, paced at one request per two seconds -
     * and then two full runs of the rating formula. That is not work to do inside a page load.
     */
    public ComputeResult computeCosts(String rawHandle) {
        String handle = SubmissionMirror.normalise(rawHandle);
        List<AbandonedDebtItem> items = repository.find(handle);

        int computed = 0;
        int unrated = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();

        for (AbandonedDebtItem item : items) {
            if (item.lastLiveRelativeSeconds() == null) {
                // Without a time relative to the contest start there is no defensible moment at
                // which to place the counterfactual solve.
                skipped++;
                continue;
            }
            try {
                Optional<RatingCost> cost = calculator.compute(handle, item.contestId(),
                        item.problemIndex(), item.lastLiveRelativeSeconds(), item.liveWrongAttempts());

                if (cost.isEmpty()) {
                    skipped++;
                    continue;
                }
                costs.save(handle, item.contestId(), item.problemIndex(), cost.get());
                if (cost.get().unrated()) {
                    unrated++;
                } else {
                    computed++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not cost {} for {}: {}", item.problemId(), handle, e.toString());
                failures.add(item.problemId() + ": " + e.getMessage());
            }
        }
        return new ComputeResult(items.size(), computed, unrated, skipped, failures);
    }

    private static Integer costOrNull(AbandonedDebtItem item) {
        return item.ratingCost() == null ? null : item.ratingCost().ratingCost();
    }

    private AbandonedDebtItem describe(AbandonedDebtItem item, RatingCost cost) {
        return item.withCostAndReason(cost, reasonFor(item, cost));
    }

    /**
     * Builds the reason. It always names the contest - an invariant, not a nicety - and says how
     * hard the problem was fought before it was abandoned.
     */
    static String reasonFor(AbandonedDebtItem item, RatingCost cost) {
        StringBuilder reason = new StringBuilder("failed in ").append(item.contestName());

        if (item.contestStartedAt() != null) {
            reason.append(" (").append(MONTH_YEAR.format(item.contestStartedAt())).append(')');
        }

        reason.append(" — ")
                .append(item.liveAttempts())
                .append(item.liveAttempts() == 1 ? " live attempt" : " live attempts");

        if (cost == null) {
            reason.append(", never solved since");
        } else if (cost.unrated()) {
            reason.append(", never solved since — unrated contest, no rating was at stake");
        } else if (cost.ratingCost() != null && cost.ratingCost() > 0) {
            reason.append(" — cost you roughly ")
                    .append(cost.ratingCost())
                    .append(" rating, finishing ")
                    .append(cost.actualRank())
                    .append(" instead of ")
                    .append(cost.counterfactualRank());
        } else {
            reason.append(", never solved since — solving it would not have moved your rating");
        }

        return reason.toString();
    }

    /**
     * @param unattributable live failures dropped because their contest is not mirrored, surfaced
     *                       rather than hidden so a shrinking queue is never a mystery
     */
    public record Report(List<AbandonedDebtItem> items, long unattributable,
                         String participationPolicy, String counterfactualPolicy) {
    }

    /** What one costing pass did. {@code skipped} items had nothing to anchor a counterfactual to. */
    public record ComputeResult(int items, int computed, int unrated, int skipped,
                                List<String> failures) {
    }
}
