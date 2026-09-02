package dev.practicedebt.cf.dto;

public record CfProblemResult(
        Double points,
        Integer rejectedAttemptCount,
        String type,
        Long bestSubmissionTimeSeconds) {
}
