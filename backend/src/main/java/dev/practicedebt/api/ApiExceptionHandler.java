package dev.practicedebt.api;

import java.time.Instant;

import dev.practicedebt.cf.CodeforcesApiException;
import dev.practicedebt.cf.CodeforcesUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns upstream trouble into an explicit error state.
 *
 * <p>The correctness bar says a Codeforces outage must produce a clear error rather than a stack
 * trace or a silent empty list, so the two failure kinds are kept distinct all the way out.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CodeforcesUnavailableException.class)
    ProblemDetail unavailable(CodeforcesUnavailableException e) {
        log.warn("Codeforces unavailable: {}", e.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Codeforces could not be reached. Showing the last mirrored data where possible.");
        detail.setTitle("Codeforces unavailable");
        detail.setProperty("cause", e.getMessage());
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(CodeforcesApiException.class)
    ProblemDetail refused(CodeforcesApiException e) {
        log.warn("Codeforces refused a request: {}", e.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Codeforces refused the request.");
        detail.setTitle("Codeforces refused the request");
        detail.setProperty("comment", e.comment());
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
