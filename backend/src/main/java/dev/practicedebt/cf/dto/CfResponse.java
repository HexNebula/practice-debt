package dev.practicedebt.cf.dto;

/**
 * Every Codeforces API response is this envelope. {@code status} is {@code OK} or {@code FAILED};
 * on failure {@code comment} carries the reason and {@code result} is absent.
 */
public record CfResponse<T>(String status, T result, String comment) {

    public boolean ok() {
        return "OK".equals(status);
    }
}
