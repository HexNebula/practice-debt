package dev.practicedebt.cf.dto;

import java.util.List;

/** Result of {@code contest.standings}. Full ranklist only - see {@code docs/cf-api-notes.md}. */
public record CfStandings(CfContest contest, List<CfProblem> problems, List<CfRanklistRow> rows) {
}
