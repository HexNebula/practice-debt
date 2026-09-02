package dev.practicedebt.cf;

/**
 * Codeforces could not be reached, or kept refusing, after every attempt was spent.
 *
 * <p>Callers are expected to fall back to the last good snapshot rather than fail the request.
 */
public class CodeforcesUnavailableException extends RuntimeException {

    public CodeforcesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public CodeforcesUnavailableException(String message) {
        super(message);
    }
}
