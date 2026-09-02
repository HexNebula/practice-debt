package dev.practicedebt.taxonomy;

import java.util.List;
import java.util.Set;

/**
 * The parsed contents of the taxonomy file.
 *
 * @param version                 bumped when the meaning of a technique changes, because staleness
 *                                measured under one version cannot be compared with another
 * @param maxTechniquesPerProblem how many techniques a single problem may be claimed by
 */
public record Taxonomy(int version, String revised, int maxTechniquesPerProblem,
                       List<Technique> techniques) {

    /**
     * One technique class.
     *
     * @param whenUnclaimed a fallback: takes a problem only when no more specific technique wanted
     *                      it. Without these, the coarse classes would swallow everything.
     * @param pinned        problem ids assigned by hand. Always wins - this is where judgement that
     *                      no rule can express is recorded.
     * @param excluded      problem ids this technique must never claim, overriding its own rules.
     */
    public record Technique(
            String id,
            String name,
            String family,
            String summary,
            boolean whenUnclaimed,
            List<Rule> rules,
            Set<String> pinned,
            Set<String> excluded) {
    }

    /**
     * A tag-and-rating pattern that seeds the mapping.
     *
     * <p>All populated conditions must hold for the rule to fire.
     *
     * @param allTags   every one of these tags must be present
     * @param anyTags   at least one of these tags must be present
     * @param noneTags  none of these tags may be present
     */
    public record Rule(
            List<String> allTags,
            List<String> anyTags,
            List<String> noneTags,
            Integer minRating,
            Integer maxRating) {

        public boolean matches(List<String> tags, Integer rating) {
            if (!allTags.isEmpty() && !tags.containsAll(allTags)) {
                return false;
            }
            if (!anyTags.isEmpty() && anyTags.stream().noneMatch(tags::contains)) {
                return false;
            }
            if (noneTags.stream().anyMatch(tags::contains)) {
                return false;
            }
            // A rating bound can only be judged when upstream published a rating. Roughly 2.5% of
            // problems have none, and a bounded rule must not claim them on a guess.
            if (minRating != null && (rating == null || rating < minRating)) {
                return false;
            }
            return maxRating == null || (rating != null && rating <= maxRating);
        }
    }
}
