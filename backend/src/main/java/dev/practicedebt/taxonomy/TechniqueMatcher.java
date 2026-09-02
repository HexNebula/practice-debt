package dev.practicedebt.taxonomy;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which techniques a problem belongs to.
 *
 * <p>Pure and deterministic: given the same taxonomy and the same problem it always returns the
 * same answer, which is what makes the mapping arguable rather than mysterious.
 *
 * <p>Order of business:
 *
 * <ol>
 *   <li>Hand-pinned assignments, which always win. This is where judgement no rule can express
 *       lives - digit DP has no tag and never will.
 *   <li>Tag rules, most specific technique first, up to the taxonomy's per-problem cap.
 *   <li>Fallback techniques, which take a problem only if nothing more specific claimed it.
 * </ol>
 */
public class TechniqueMatcher {

    private final Taxonomy taxonomy;

    public TechniqueMatcher(Taxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    public List<Assignment> match(String problemId, List<String> tags, Integer rating) {
        List<Assignment> assigned = new ArrayList<>();

        for (Taxonomy.Technique technique : taxonomy.techniques()) {
            if (technique.pinned().contains(problemId) && !technique.excluded().contains(problemId)) {
                assigned.add(new Assignment(technique.id(), Source.PINNED));
            }
        }

        for (Taxonomy.Technique technique : taxonomy.techniques()) {
            if (assigned.size() >= taxonomy.maxTechniquesPerProblem()) {
                break;
            }
            if (technique.excluded().contains(problemId) || alreadyAssigned(assigned, technique)) {
                continue;
            }
            // A fallback exists to catch what nothing else wanted; if anything has been claimed,
            // it stays out of the way.
            if (technique.whenUnclaimed() && !assigned.isEmpty()) {
                continue;
            }
            if (technique.rules().stream().anyMatch(rule -> rule.matches(tags, rating))) {
                assigned.add(new Assignment(technique.id(), Source.RULE));
            }
        }
        return List.copyOf(assigned);
    }

    private static boolean alreadyAssigned(List<Assignment> assigned, Taxonomy.Technique technique) {
        return assigned.stream().anyMatch(a -> a.techniqueId().equals(technique.id()));
    }

    /** Where an assignment came from, kept because a pin and a heuristic deserve different trust. */
    public enum Source {
        PINNED, RULE
    }

    public record Assignment(String techniqueId, Source source) {
    }
}
