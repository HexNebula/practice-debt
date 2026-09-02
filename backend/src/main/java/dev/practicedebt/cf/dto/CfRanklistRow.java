package dev.practicedebt.cf.dto;

import java.util.List;

public record CfRanklistRow(
        CfParty party,
        int rank,
        Double points,
        Integer penalty,
        Integer successfulHackCount,
        Integer unsuccessfulHackCount,
        List<CfProblemResult> problemResults) {
}
