package dev.practicedebt.cf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.practicedebt.cf.dto.CfContest;
import dev.practicedebt.cf.dto.CfProblemset;
import dev.practicedebt.cf.dto.CfRatingChange;
import dev.practicedebt.cf.dto.CfResponse;
import dev.practicedebt.cf.dto.CfStandings;
import dev.practicedebt.cf.dto.CfSubmission;
import dev.practicedebt.config.CodeforcesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * The only place the Codeforces API is spoken to.
 *
 * <p>Three things are enforced for every call and are not optional per call site: pacing, retry
 * with backoff, and treating the {@code status}/{@code comment} envelope as part of the protocol
 * rather than as noise wrapped around a payload.
 *
 * <p>Failures are split deliberately. {@link CodeforcesApiException} means upstream understood the
 * request and refused it - retrying changes nothing. {@link CodeforcesUnavailableException} means
 * the answer never arrived; callers should fall back to the last good snapshot.
 */
@Component
public class CodeforcesClient {

    private static final Logger log = LoggerFactory.getLogger(CodeforcesClient.class);

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final RestClient http;
    private final RequestPacer pacer;
    private final CodeforcesProperties props;
    private final RequestSigner signer = new RequestSigner();

    public CodeforcesClient(RestClient codeforcesRestClient, RequestPacer codeforcesPacer,
            CodeforcesProperties props) {
        this.http = codeforcesRestClient;
        this.pacer = codeforcesPacer;
        this.props = props;
        // Says whether signing is on without ever revealing the credentials themselves.
        log.info("Codeforces client {}", props.authenticated()
                ? "is signing requests; private gym and group submissions will be visible"
                : "is anonymous; private gym and group submissions will be invisible");
    }

    /**
     * The full public problemset with tags, ratings and solved counts.
     *
     * <p>Roughly 2 MB and 11k problems in one response. Mirror it; never call it per user request.
     */
    public CfProblemset problemset() {
        return call("problemset.problems", params(),
                new ParameterizedTypeReference<CfResponse<CfProblemset>>() {
                });
    }

    /**
     * A handle's submissions, newest first.
     *
     * @param from  1-based index of the first submission to return
     * @param count how many submissions to return
     */
    public List<CfSubmission> userStatus(String handle, int from, int count) {
        return call("user.status",
                params("handle", handle, "from", String.valueOf(from), "count", String.valueOf(count)),
                new ParameterizedTypeReference<CfResponse<List<CfSubmission>>>() {
                });
    }

    /**
     * Final ranklist for a contest.
     *
     * <p>All or nothing: Codeforces rejects every extra parameter on non-gym contests for non-admin
     * callers, so paging and handle filtering are not available. A Div. 4 ranklist is ~16k rows and
     * ~15 MB. Mirror it, and never fetch it while a contest is running.
     */
    public CfStandings contestStandings(int contestId) {
        return call("contest.standings",
                params("contestId", String.valueOf(contestId)),
                new ParameterizedTypeReference<CfResponse<CfStandings>>() {
                });
    }

    /**
     * Every public (non-gym) contest.
     *
     * <p>One call, ~2,100 contests, every field present. Contest names are what reason strings are
     * built from, so this is mirrored rather than looked up.
     */
    public List<CfContest> contestList() {
        return call("contest.list",
                params("gym", "false"),
                new ParameterizedTypeReference<CfResponse<List<CfContest>>>() {
                });
    }

    /**
     * Pre-contest rating, final rank and awarded delta for every rated participant of a contest.
     *
     * <p>One call, ~3.5 MB for a large Div. 2. This is the entire input to the rating formula, so
     * the counterfactual needs it mirrored per contest that produced a debt item.
     *
     * <p>An unrated contest answers with an empty list rather than an error.
     */
    public List<CfRatingChange> contestRatingChanges(int contestId) {
        return call("contest.ratingChanges",
                params("contestId", String.valueOf(contestId)),
                new ParameterizedTypeReference<CfResponse<List<CfRatingChange>>>() {
                });
    }

    /** Rating change history for a handle, oldest first. */
    public List<CfRatingChange> userRating(String handle) {
        return call("user.rating",
                params("handle", handle),
                new ParameterizedTypeReference<CfResponse<List<CfRatingChange>>>() {
                });
    }

    /**
     * Parameters in a stable order.
     *
     * <p>Ordered rather than a {@code Map.of}: the signature sorts its own copy, but an unordered
     * map makes the request URL itself vary between runs, which turns logs and any exact-URI
     * assertion into noise.
     */
    private static Map<String, String> params(String... keyValuePairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            params.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return params;
    }

    private <T> T call(String method, Map<String, String> params,
            ParameterizedTypeReference<CfResponse<T>> responseType) {

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            pace(method);
            // Signed afresh on every attempt: the signature covers a timestamp, so a retry that
            // reused the previous one would be rejected as stale.
            Map<String, String> query = authorise(method, params);

            try {
                Attempt<T> outcome = http.get()
                        .uri(uri -> {
                            UriBuilder b = uri.path("/" + method);
                            query.forEach(b::queryParam);
                            return b.build();
                        })
                        .exchange((request, response) -> {
                            HttpStatusCode status = response.getStatusCode();
                            // A refusal arrives as HTTP 400 carrying the same JSON envelope as a
                            // success, so the envelope is parsed on any status that is JSON.
                            // Confirmed against the live API: an unknown handle returns 400 with
                            // {"status":"FAILED","comment":"..."}.
                            if (isJson(response.getHeaders().getContentType())) {
                                return new Attempt<>(status, response.bodyTo(responseType), null);
                            }
                            return new Attempt<T>(status, null, readBody(response.getBody()));
                        });

                return interpret(method, outcome);

            } catch (Retryable e) {
                lastFailure = e;
            } catch (ResourceAccessException | IllegalStateException e) {
                // Timeouts, connection resets, and truncated bodies all land here.
                lastFailure = new CodeforcesUnavailableException(method + ": " + e.getMessage(), e);
            }

            if (attempt < props.maxAttempts()) {
                log.warn("Codeforces {} attempt {}/{} failed: {}",
                        method, attempt, props.maxAttempts(), lastFailure.getMessage());
                backoff(attempt);
            }
        }

        throw new CodeforcesUnavailableException(
                method + " failed after " + props.maxAttempts() + " attempts", lastFailure);
    }

    /**
     * Turns one HTTP outcome into a result, a permanent refusal, or a signal to try again.
     *
     * @throws Retryable               the attempt failed in a way another attempt might survive
     * @throws CodeforcesApiException  upstream refused for a reason that will not change
     */
    private <T> T interpret(String method, Attempt<T> outcome) {
        CfResponse<T> body = outcome.body();
        boolean success = outcome.status().is2xxSuccessful();

        if (body != null) {
            if (body.ok() && success) {
                if (body.result() == null) {
                    throw new CodeforcesApiException(method + ": OK response carried no result", null);
                }
                return body.result();
            }
            if (transientComment(body.comment())) {
                throw new Retryable(method + " throttled: " + body.comment());
            }
            throw new CodeforcesApiException(
                    method + " refused (HTTP " + outcome.status().value() + "): " + body.comment(),
                    body.comment());
        }

        if (success) {
            throw new Retryable(method + ": HTTP " + outcome.status().value() + " with no JSON body");
        }

        // 403 is what Codeforces returns to an IP that is calling too often, and it comes back as
        // plain text rather than an envelope. It is a signal to slow down, not a permanent error.
        int code = outcome.status().value();
        if (code == 403 || code == 429 || outcome.status().is5xxServerError()) {
            throw new Retryable(method + ": HTTP " + code + " - " + abbreviate(outcome.rawBody()));
        }
        throw new CodeforcesApiException(
                method + ": HTTP " + code + " - " + abbreviate(outcome.rawBody()), null);
    }

    /**
     * Adds Codeforces' authorisation parameters when credentials are configured.
     *
     * <p>Unauthenticated calls are a supported mode, not a degraded one - everything works, it just
     * cannot see private gym or group submissions.
     */
    private Map<String, String> authorise(String method, Map<String, String> params) {
        if (!props.authenticated()) {
            return params;
        }
        return signer.sign(method, params, props.apiKey(), props.apiSecret());
    }

    private void pace(String method) {
        try {
            pacer.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeforcesUnavailableException(method + ": interrupted while pacing", e);
        }
    }

    private void backoff(int attempt) {
        long millis = Math.min(
                props.retryBackoff().toMillis() * (1L << (attempt - 1)),
                MAX_BACKOFF.toMillis());
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodeforcesUnavailableException("interrupted during backoff", e);
        }
    }

    private static boolean isJson(MediaType contentType) {
        return contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
    }

    private static boolean transientComment(String comment) {
        if (comment == null) {
            return false;
        }
        String c = comment.toLowerCase(Locale.ROOT);
        return c.contains("limit exceeded") || c.contains("try again");
    }

    private static String readBody(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String abbreviate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "<empty body>";
        }
        String flat = raw.strip().replaceAll("\\s+", " ");
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "...";
    }

    private record Attempt<T>(HttpStatusCode status, CfResponse<T> body, String rawBody) {
    }

    /** Internal control flow only; never escapes {@link #call}. */
    private static final class Retryable extends RuntimeException {
        Retryable(String message) {
            super(message, null, false, false);
        }
    }
}
