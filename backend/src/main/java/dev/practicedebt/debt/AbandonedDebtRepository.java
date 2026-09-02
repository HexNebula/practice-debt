package dev.practicedebt.debt;

import java.sql.Array;
import java.time.Instant;
import java.util.List;

import dev.practicedebt.mirror.SubmissionMirror;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The abandoned-debt derivation.
 *
 * <p>The rule from the spec, verbatim: a problem is abandoned debt for a handle when there exists a
 * submission to it with live-contest participation, and no accepted submission to it exists, ever,
 * in any participation mode.
 *
 * <p>That is an anti-join, and it is expressed as one here rather than reconstructed in Java. The
 * invariant it protects - a solved problem never appears as debt, however many times it was failed
 * first - is a property of this query, so this query is what the tests exercise.
 */
@Repository
public class AbandonedDebtRepository {

    /**
     * Reads as: problems this handle attempted under a live participation type, minus every problem
     * they have ever had accepted.
     *
     * <p>The join to {@code contest} is not decoration either. An item's reason string has to name
     * the contest, so an item whose contest is not mirrored - a gym round, say - is not a debt item
     * this system can honestly produce. {@link #unattributableCount} counts what that join drops,
     * so the exclusion stays visible instead of silently shrinking the queue.
     */
    private static final String FIND = """
            with live_attempts as (
                select s.contest_id,
                       s.problem_index,
                       count(*)                     as live_attempts,
                       -- Compile errors are not rejected attempts on Codeforces; verified against
                       -- a real ranklist, where a problem with one compile error scored as if
                       -- there had been none.
                       count(*) filter (
                           where s.verdict is distinct from 'COMPILATION_ERROR'
                       )                            as live_wrong_attempts,
                       max(s.relative_time_seconds) as last_live_relative_seconds,
                       min(s.creation_time_seconds) as first_attempt_seconds,
                       max(s.creation_time_seconds) as last_attempt_seconds,
                       max(s.problem_name)          as fallback_problem_name
                  from submission s
                 where s.handle = :handle
                   and s.participant_type in (:liveTypes)
                 group by s.contest_id, s.problem_index
            )
            select la.contest_id,
                   la.problem_index,
                   coalesce(p.name, la.fallback_problem_name) as problem_name,
                   p.rating,
                   coalesce(p.tags, '{}')                     as tags,
                   c.name                                     as contest_name,
                   c.start_time_seconds,
                   la.live_attempts,
                   la.live_wrong_attempts,
                   la.last_live_relative_seconds,
                   la.first_attempt_seconds,
                   la.last_attempt_seconds
              from live_attempts la
              join contest c
                on c.id = la.contest_id
              left join problem p
                on p.contest_id = la.contest_id
               and p.problem_index = la.problem_index
             where not exists (
                       select 1
                         from submission solved
                        where solved.handle = :handle
                          and solved.contest_id = la.contest_id
                          and solved.problem_index = la.problem_index
                          and solved.verdict = 'OK')
             order by c.start_time_seconds desc nulls last, la.problem_index
            """;

    /** Live failures whose contest is not in the mirror, and which therefore cannot explain themselves. */
    private static final String UNATTRIBUTABLE = """
            select count(*)
              from (select distinct s.contest_id, s.problem_index
                      from submission s
                     where s.handle = :handle
                       and s.participant_type in (:liveTypes)
                       and not exists (select 1 from contest c where c.id = s.contest_id)
                       and not exists (
                               select 1
                                 from submission solved
                                where solved.handle = s.handle
                                  and solved.contest_id = s.contest_id
                                  and solved.problem_index = s.problem_index
                                  and solved.verdict = 'OK')) orphans
            """;

    private static final RowMapper<AbandonedDebtItem> MAPPER = (rs, rowNum) -> {
        Array tags = rs.getArray("tags");
        Long startSeconds = (Long) rs.getObject("start_time_seconds");
        return new AbandonedDebtItem(
                rs.getInt("contest_id"),
                rs.getString("problem_index"),
                rs.getString("problem_name"),
                (Integer) rs.getObject("rating"),
                tags == null ? List.of() : List.of((String[]) tags.getArray()),
                rs.getString("contest_name"),
                startSeconds == null ? null : Instant.ofEpochSecond(startSeconds),
                rs.getInt("live_attempts"),
                rs.getInt("live_wrong_attempts"),
                (Long) rs.getObject("last_live_relative_seconds"),
                Instant.ofEpochSecond(rs.getLong("first_attempt_seconds")),
                Instant.ofEpochSecond(rs.getLong("last_attempt_seconds")),
                null,
                null);
    };

    private final JdbcClient db;

    public AbandonedDebtRepository(JdbcClient db) {
        this.db = db;
    }

    public List<AbandonedDebtItem> find(String rawHandle) {
        return db.sql(FIND)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .param("liveTypes", liveTypes())
                .query(MAPPER)
                .list();
    }

    public long unattributableCount(String rawHandle) {
        return db.sql(UNATTRIBUTABLE)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .param("liveTypes", liveTypes())
                .query(Long.class)
                .single();
    }

    /**
     * Passed as a collection so Spring expands it into a parameter list. A Postgres array would
     * need an explicit {@code createArrayOf}; there is no reason to reach for one here.
     */
    private static List<String> liveTypes() {
        return List.copyOf(ParticipationPolicy.liveParticipationTypes());
    }
}
