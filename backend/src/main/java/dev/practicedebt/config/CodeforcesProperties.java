package dev.practicedebt.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Codeforces API client and mirror settings.
 *
 * <p>The rate limit is unofficial and enforced per IP. Every value here exists so it can be
 * turned down in anger without a rebuild.
 */
@ConfigurationProperties(prefix = "codeforces")
public record CodeforcesProperties(

        @DefaultValue("https://codeforces.com/api") String baseUrl,

        /**
         * Codeforces API key, or blank to call anonymously.
         *
         * <p>Set in {@code secrets.properties}, which is gitignored. An authorised call sees what
         * that user sees, which for this project means private gym and group submissions that the
         * anonymous API silently omits.
         */
        @DefaultValue("") String apiKey,

        /** Paired secret. Never logged, never included in an error message. */
        @DefaultValue("") String apiSecret,

        /** Hard floor between two outbound calls, applied process-wide. */
        @DefaultValue("2s") Duration minRequestInterval,

        @DefaultValue("10s") Duration connectTimeout,

        @DefaultValue("120s") Duration readTimeout,

        /** Total attempts per call, including the first. */
        @DefaultValue("4") int maxAttempts,

        /** Base delay for exponential backoff between retries. */
        @DefaultValue("2s") Duration retryBackoff,

        @DefaultValue Mirror mirror) {

    /** Whether requests should be signed. Both halves are required; one alone is a misconfiguration. */
    public boolean authenticated() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }


    public record Mirror(
            @DefaultValue("true") boolean refreshOnStartup,

            /** A mirror older than this is refreshed on startup. */
            @DefaultValue("12h") Duration maxAge,

            @DefaultValue("6h") Duration refreshInterval) {
    }
}
