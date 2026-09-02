package dev.practicedebt.cf.dto;

/**
 * One submission from {@code user.status}.
 *
 * <p>{@code verdict} is absent while a submission is still being judged, so it is nullable here
 * rather than an enum.
 */
public record CfSubmission(
        long id,
        Integer contestId,
        long creationTimeSeconds,
        Long relativeTimeSeconds,
        CfProblem problem,
        CfParty author,
        String programmingLanguage,
        String verdict,
        String testset,
        Integer passedTestCount,
        Integer timeConsumedMillis,
        Long memoryConsumedBytes) {

    public boolean accepted() {
        return "OK".equals(verdict);
    }
}
