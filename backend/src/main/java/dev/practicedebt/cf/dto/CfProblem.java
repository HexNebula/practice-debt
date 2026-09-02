package dev.practicedebt.cf.dto;

import java.util.List;

/**
 * A problem as Codeforces reports it.
 *
 * <p>{@code contestId} is absent for problems that live only in a named problemset (ACMSGURU);
 * {@code rating} is absent for roughly 4% of problems, and {@code points} only exists for
 * scored (non-ICPC) contests. Verified against the live API, not assumed.
 */
public record CfProblem(
        Integer contestId,
        String problemsetName,
        String index,
        String name,
        String type,
        Double points,
        Integer rating,
        List<String> tags) {

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
