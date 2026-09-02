package dev.practicedebt.cf.dto;

/**
 * Contest metadata. {@code type} is {@code CF}, {@code ICPC} or {@code IOI} and decides how
 * standings are scored - which in turn decides how a counterfactual rank is computed.
 */
public record CfContest(
        int id,
        String name,
        String type,
        String phase,
        Boolean frozen,
        Long durationSeconds,
        Long startTimeSeconds,
        Long relativeTimeSeconds) {
}
