package dev.practicedebt.mirror;

import java.util.List;

import dev.practicedebt.cf.CodeforcesClient;
import dev.practicedebt.cf.dto.CfContest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps a local copy of the public contest list.
 *
 * <p>Cheap - one call returns every non-gym contest - and load-bearing: a debt item's reason string
 * has to name the contest it came from, so without this table an item cannot explain itself and by
 * the spec does not ship.
 */
@Service
public class ContestMirror {

    public static final String SOURCE = "contest.list";

    private static final Logger log = LoggerFactory.getLogger(ContestMirror.class);

    private final CodeforcesClient codeforces;
    private final ContestRepository contests;
    private final MirrorRunRepository runs;

    public ContestMirror(CodeforcesClient codeforces, ContestRepository contests,
            MirrorRunRepository runs) {
        this.codeforces = codeforces;
        this.contests = contests;
        this.runs = runs;
    }

    public int refresh() {
        long runId = runs.start(SOURCE);
        try {
            List<Contest> fetched = codeforces.contestList().stream()
                    .map(ContestMirror::toContest)
                    .toList();

            int written = contests.upsertAll(fetched);
            runs.succeed(runId, written);
            log.info("Contest mirror refreshed: {} contests written", written);
            return written;

        } catch (RuntimeException e) {
            runs.fail(runId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private static Contest toContest(CfContest c) {
        return new Contest(c.id(), c.name(), c.type(), c.phase(), c.frozen(),
                c.durationSeconds(), c.startTimeSeconds());
    }
}
