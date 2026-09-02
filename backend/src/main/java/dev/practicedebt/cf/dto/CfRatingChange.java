package dev.practicedebt.cf.dto;

public record CfRatingChange(
        int contestId,
        String contestName,
        String handle,
        int rank,
        long ratingUpdateTimeSeconds,
        int oldRating,
        int newRating) {

    public int delta() {
        return newRating - oldRating;
    }
}
