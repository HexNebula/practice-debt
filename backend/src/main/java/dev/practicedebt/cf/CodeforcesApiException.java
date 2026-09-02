package dev.practicedebt.cf;

/**
 * Codeforces answered, and the answer was a refusal.
 *
 * <p>{@code comment} is upstream's own words, kept verbatim: it is what distinguishes "handle not
 * found" from "call limit exceeded" from a malformed parameter, and the client's retry policy
 * reads it. Retrying this exception is pointless by definition - see
 * {@link CodeforcesUnavailableException} for the failures that are worth another attempt.
 */
public class CodeforcesApiException extends RuntimeException {

    private final String comment;

    public CodeforcesApiException(String message, String comment) {
        super(message);
        this.comment = comment;
    }

    /** Upstream's explanation, when it gave one. */
    public String comment() {
        return comment;
    }
}
