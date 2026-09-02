package dev.practicedebt.mirror;

import dev.practicedebt.config.CodeforcesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the problemset mirror: once at startup if the local copy is stale, then on a fixed cadence.
 *
 * <p>Refresh failures never stop the application. A stale mirror still answers questions; an
 * application that refuses to start answers none.
 */
@Component
public class ProblemsetMirrorSchedule implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProblemsetMirrorSchedule.class);

    private final ProblemsetMirror mirror;
    private final CodeforcesProperties props;

    public ProblemsetMirrorSchedule(ProblemsetMirror mirror, CodeforcesProperties props) {
        this.mirror = mirror;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.mirror().refreshOnStartup()) {
            log.info("Problemset mirror refresh on startup is disabled");
            return;
        }
        try {
            mirror.refreshIfStale().ifPresentOrElse(
                    result -> log.info("Startup refresh wrote {} problems", result.written()),
                    () -> log.info("Problemset mirror is fresh; skipping startup refresh"));
        } catch (RuntimeException e) {
            log.warn("Startup problemset refresh failed; serving whatever the mirror already holds",
                    e);
        }
    }

    @Scheduled(
            fixedDelayString = "${codeforces.mirror.refresh-interval}",
            initialDelayString = "${codeforces.mirror.refresh-interval}")
    void refreshOnSchedule() {
        try {
            mirror.refresh();
        } catch (RuntimeException e) {
            log.warn("Scheduled problemset refresh failed", e);
        }
    }
}
