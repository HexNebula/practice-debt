package dev.practicedebt.cf.dto;

import java.util.List;

/** Result of {@code problemset.problems}: two parallel lists keyed by (contestId, index). */
public record CfProblemset(List<CfProblem> problems, List<CfProblemStatistics> problemStatistics) {
}
