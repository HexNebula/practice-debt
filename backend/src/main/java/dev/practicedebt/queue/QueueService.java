package dev.practicedebt.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.practicedebt.debt.AbandonedDebtItem;
import dev.practicedebt.debt.AbandonedDebtService;
import dev.practicedebt.debt.CounterfactualPolicy;
import dev.practicedebt.debt.ParticipationPolicy;
import dev.practicedebt.decay.DecayPolicy;
import dev.practicedebt.decay.DecayedDebtItem;
import dev.practicedebt.decay.DecayedDebtService;
import dev.practicedebt.decay.TechniqueActivityRepository;
import org.springframework.stereotype.Service;

/**
 * The single output of the whole system: what the author owes, in one ordered list.
 *
 * <p>Tracking and charts are not the product. This is.
 */
@Service
public class QueueService {

    private final AbandonedDebtService abandoned;
    private final DecayedDebtService decayed;

    public QueueService(AbandonedDebtService abandoned, DecayedDebtService decayed) {
        this.abandoned = abandoned;
        this.decayed = decayed;
    }

    public Queue forHandle(String handle) {
        AbandonedDebtService.Report abandonedReport = abandoned.reportFor(handle);
        DecayedDebtService.Report decayedReport = decayed.reportFor(handle);

        List<QueueItem> items = new ArrayList<>();

        // Each source arrives already ranked by its own measure; position within that ranking is
        // all the merge uses, which is what lets two incomparable scales be combined honestly.
        List<AbandonedDebtItem> abandonedItems = abandonedReport.items();
        for (int i = 0; i < abandonedItems.size(); i++) {
            items.add(toItem(abandonedItems.get(i),
                    QueuePolicy.score(i, abandonedItems.size(), true)));
        }

        List<DecayedDebtItem> decayedItems = decayedReport.items();
        for (int i = 0; i < decayedItems.size(); i++) {
            items.add(toItem(decayedItems.get(i),
                    QueuePolicy.score(i, decayedItems.size(), false)));
        }

        items.sort(Comparator.comparingDouble(QueueItem::score).reversed()
                .thenComparing(QueueItem::id));

        return new Queue(
                List.copyOf(items),
                abandonedItems.size(),
                decayedItems.size(),
                abandonedReport.unattributable(),
                decayedReport.withheld(),
                new Policies(QueuePolicy.describe(), ParticipationPolicy.describe(),
                        CounterfactualPolicy.describe(), DecayPolicy.describe()));
    }

    private static QueueItem toItem(AbandonedDebtItem item, double score) {
        Integer cost = item.ratingCost() == null ? null : item.ratingCost().ratingCost();
        return new QueueItem(
                QueueItem.Source.ABANDONED,
                item.problemId(),
                item.problemId() + " — " + item.problemName(),
                item.reason(),
                score,
                cost,
                null,
                List.of(new QueueItem.Action("Open " + item.problemId(), item.url())));
    }

    private static QueueItem toItem(DecayedDebtItem item, double score) {
        List<QueueItem.Action> actions = item.suggestions().stream()
                .map(s -> new QueueItem.Action(
                        s.problemId() + " — " + s.name()
                                + (s.rating() == null ? "" : " (" + s.rating() + ")"),
                        s.url()))
                .map(a -> (QueueItem.Action) a)
                .toList();

        return new QueueItem(
                QueueItem.Source.DECAYED,
                item.techniqueId(),
                item.name(),
                item.reason(),
                score,
                null,
                item.daysSinceLast(),
                actions);
    }

    /**
     * @param unattributable abandoned failures withheld because their contest cannot be named
     * @param withheldDecayed decayed techniques below the display cap
     */
    public record Queue(List<QueueItem> items, int abandonedCount, int decayedCount,
                        long unattributable, long withheldDecayed, Policies policies) {
    }

    /** Every assumption the queue rests on, carried with it so the UI can show them. */
    public record Policies(String ranking, String participation, String counterfactual,
                           String decay) {
    }
}
