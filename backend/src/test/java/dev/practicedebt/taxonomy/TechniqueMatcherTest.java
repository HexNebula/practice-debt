package dev.practicedebt.taxonomy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matching rules, against the real shipped taxonomy rather than a toy one.
 *
 * <p>Tag sets here are copied from actual problems in the mirror.
 */
class TechniqueMatcherTest {

    private final Taxonomy taxonomy = new TaxonomyLoader(
            new org.springframework.core.io.ClassPathResource("taxonomy/techniques-v1.yaml")).load();

    private final TechniqueMatcher matcher = new TechniqueMatcher(taxonomy);

    private List<String> techniquesFor(String problemId, Integer rating, String... tags) {
        return matcher.match(problemId, List.of(tags), rating).stream()
                .map(TechniqueMatcher.Assignment::techniqueId)
                .toList();
    }

    @Test
    @DisplayName("the taxonomy file loads and every technique is distinct")
    void taxonomyLoads() {
        assertThat(taxonomy.version()).isEqualTo(1);
        assertThat(taxonomy.techniques()).hasSizeGreaterThan(25);
        assertThat(taxonomy.techniques()).extracting(Taxonomy.Technique::id).doesNotHaveDuplicates();
        assertThat(taxonomy.techniques()).allSatisfy(t -> {
            assertThat(t.summary()).isNotBlank();
            assertThat(t.family()).isNotBlank();
        });
    }

    @Test
    @DisplayName("digit DP is found by pin, because no tag combination can find it")
    void digitDpComesFromPins() {
        // 55D Beautiful numbers is tagged only dp, number theory - indistinguishable from any
        // other number-theoretic DP.
        assertThat(techniquesFor("55D", 2500, "dp", "number theory")).contains("digit-dp");
        assertThat(matcher.match("55D", List.of("dp", "number theory"), 2500))
                .anySatisfy(a -> assertThat(a.source()).isEqualTo(TechniqueMatcher.Source.PINNED));

        // An unpinned problem with identical tags must not be mistaken for digit DP.
        assertThat(techniquesFor("9999Z", 2500, "dp", "number theory")).doesNotContain("digit-dp");
    }

    @Test
    @DisplayName("an exclusion beats the technique's own rules")
    void exclusionsWin() {
        // "Digital Village" is a tree problem. Any name-based heuristic would misfile it, and the
        // exclusion list is what stops that being possible.
        assertThat(techniquesFor("2021E2", 2500, "data structures", "dp", "dsu", "graphs", "math",
                "trees")).doesNotContain("digit-dp");
    }

    @Test
    @DisplayName("a specific DP class beats the generic one")
    void specificDpWinsOverLinearDp() {
        assertThat(techniquesFor("1000A", 2100, "dp", "trees")).contains("tree-dp");
        assertThat(techniquesFor("1000B", 2100, "dp", "bitmasks")).contains("bitmask-dp");
        assertThat(techniquesFor("1000C", 2100, "dp", "probabilities")).contains("expectation-dp");
        // Plain dp with nothing more specific falls through to the base class.
        assertThat(techniquesFor("1000D", 1800, "dp")).contains("linear-dp");
    }

    @Test
    @DisplayName("a fallback stays out of the way when something specific already claimed the problem")
    void fallbacksOnlyTakeUnclaimedProblems() {
        // greedy-and-exchange is a fallback; binary-search-on-answer is not.
        List<String> assigned = techniquesFor("1000E", 1700, "binary search", "greedy");

        assertThat(assigned).contains("binary-search-on-answer");
        assertThat(assigned).doesNotContain("greedy-and-exchange");

        // With nothing specific present, the fallback does its job. Note the invented problem id:
        // real ids appear in the pin lists, and a pin would beat the rule being tested here.
        assertThat(techniquesFor("9998A", 1200, "greedy")).contains("greedy-and-exchange");
    }

    @Test
    @DisplayName("no problem is claimed by more techniques than the taxonomy allows")
    void respectsThePerProblemCap() {
        List<String> assigned = techniquesFor("1000G", 2600,
                "binary search", "data structures", "dp", "dsu", "graphs", "greedy", "math",
                "trees", "strings", "geometry", "number theory", "combinatorics");

        assertThat(assigned).hasSizeLessThanOrEqualTo(taxonomy.maxTechniquesPerProblem());
    }

    @Test
    @DisplayName("a rating-bounded rule does not fire on a problem with no rating")
    void ratingBoundsNeedARating() {
        // ~2.5% of problems carry no rating. A bounded rule must decline rather than guess.
        assertThat(techniquesFor("1000H", null, "trees")).doesNotContain("tree-structure");
        assertThat(techniquesFor("1000I", 2400, "trees")).contains("tree-structure");
    }

    @Test
    @DisplayName("a problem with no tags at all is left unmapped rather than guessed at")
    void untaggedProblemsAreLeftAlone() {
        assertThat(techniquesFor("1000J", 1500)).isEmpty();
    }

    @Test
    @DisplayName("a pin beats a fallback that would otherwise claim the problem")
    void pinsBeatRules() {
        // 1000F is pinned to sqrt-decomposition. Tagged only 'greedy', the fallback would
        // otherwise take it; the hand-curated assignment wins instead.
        assertThat(techniquesFor("1000F", 1200, "greedy")).contains("sqrt-decomposition");
    }

    @Test
    @DisplayName("matching is deterministic")
    void matchingIsDeterministic() {
        List<String> first = techniquesFor("1000K", 2000, "dp", "trees", "greedy");
        List<String> second = techniquesFor("1000K", 2000, "dp", "trees", "greedy");

        assertThat(first).isEqualTo(second);
    }
}
