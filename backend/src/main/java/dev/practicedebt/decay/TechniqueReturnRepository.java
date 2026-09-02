package dev.practicedebt.decay;

import java.util.List;

import dev.practicedebt.mirror.SubmissionMirror;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The calibration signal.
 *
 * <p>The question a real decay model would need answered: after N days away from a technique, does
 * the author still solve it cleanly on the first attempt? Every such return is recorded here.
 *
 * <p>Nothing reads this to make a decision, and nothing should until there is enough of it. It
 * exists because the spec is right that the data must start accumulating before the model can be
 * fitted, and because it accumulates whether or not anyone else ever uses this tool.
 *
 * <p>Usefully, it is derivable retrospectively: a handle's whole history already contains every
 * return they have ever made, so a fresh sync yields years of evidence rather than starting at zero.
 */
@Repository
public class TechniqueReturnRepository {

    /**
     * Every solve that followed a long enough silence in its technique.
     *
     * <p>{@code solved_first_try} is the outcome being predicted: whether the earliest submission
     * to that problem was the accepted one. Compile errors are excluded from the attempt count,
     * matching how Codeforces itself counts rejected attempts.
     */
    private static final String DETECT_RETURNS = """
            with solves as (
                select pt.technique_id,
                       s.contest_id,
                       s.problem_index,
                       min(s.creation_time_seconds) as solved_at
                  from submission s
                  join problem_technique pt
                    on pt.contest_id = s.contest_id
                   and pt.problem_index = s.problem_index
                 where s.handle = :handle
                   and s.verdict = 'OK'
                 group by pt.technique_id, s.contest_id, s.problem_index
            ),
            gaps as (
                select technique_id,
                       contest_id,
                       problem_index,
                       solved_at,
                       lag(solved_at) over (
                           partition by technique_id order by solved_at
                       ) as previous_solve_at
                  from solves
            ),
            attempts as (
                select s.contest_id,
                       s.problem_index,
                       count(*) filter (
                           where s.verdict is distinct from 'COMPILATION_ERROR'
                       ) as attempt_count,
                       -- Did the very first submission to this problem pass?
                       (min(s.creation_time_seconds) filter (where s.verdict = 'OK'))
                           = min(s.creation_time_seconds) as clean
                  from submission s
                 where s.handle = :handle
                 group by s.contest_id, s.problem_index
            )
            select g.technique_id,
                   g.contest_id,
                   g.problem_index,
                   g.solved_at,
                   ((g.solved_at - g.previous_solve_at) / 86400)::integer as gap_days,
                   coalesce(a.clean, false)                               as solved_first_try,
                   coalesce(a.attempt_count, 1)                           as attempts,
                   p.rating
              from gaps g
              join attempts a
                on a.contest_id = g.contest_id
               and a.problem_index = g.problem_index
              left join problem p
                on p.contest_id = g.contest_id
               and p.problem_index = g.problem_index
             where g.previous_solve_at is not null
               and (g.solved_at - g.previous_solve_at) >= :gapSeconds
            """;

    private final JdbcClient db;

    public TechniqueReturnRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Recomputes every recorded return for a handle from their mirrored history.
     *
     * @return how many returns are now on record
     */
    @Transactional
    public int recordReturns(String rawHandle, int taxonomyVersion) {
        String handle = SubmissionMirror.normalise(rawHandle);
        db.sql("delete from technique_return where handle = :handle").param("handle", handle).update();

        return db.sql("""
                        insert into technique_return (handle, technique_id, contest_id, problem_index,
                                returned_at, gap_days, solved_first_try, attempts, problem_rating,
                                taxonomy_version)
                        """ + "select :handle, r.technique_id, r.contest_id, r.problem_index,"
                        + " to_timestamp(r.solved_at), r.gap_days, r.solved_first_try, r.attempts,"
                        + " r.rating, :version from (" + DETECT_RETURNS + ") r"
                        + " on conflict do nothing")
                .param("handle", handle)
                .param("gapSeconds", (long) DecayPolicy.RETURN_GAP_DAYS * 86400)
                .param("version", taxonomyVersion)
                .update();
    }

    /**
     * What the evidence says so far, bucketed by how long the author had been away.
     *
     * <p>Reported, not acted on. When the buckets stop being noise, a model can be fitted to them.
     */
    public List<Calibration> calibration(String rawHandle) {
        return db.sql("""
                        select case
                                   when gap_days < 60  then '30-59 days'
                                   when gap_days < 120 then '60-119 days'
                                   when gap_days < 240 then '120-239 days'
                                   else '240+ days'
                               end                                              as bucket,
                               min(gap_days)                                    as min_gap,
                               count(*)                                         as returns,
                               count(*) filter (where solved_first_try)          as clean,
                               round(avg(attempts), 2)                          as average_attempts,
                               -- Exposed deliberately. A clean-solve rate that rises with the gap
                               -- is more likely to mean "came back to something easier" than
                               -- "forgetting improves memory", and the reader must be able to see
                               -- that confound rather than be misled by the headline rate.
                               round(avg(problem_rating))::integer              as average_rating
                          from technique_return
                         where handle = :handle
                         group by bucket, case
                                   when gap_days < 60  then 1
                                   when gap_days < 120 then 2
                                   when gap_days < 240 then 3
                                   else 4
                               end
                         order by min(gap_days)
                        """)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .query((rs, n) -> new Calibration(
                        rs.getString("bucket"),
                        rs.getLong("returns"),
                        rs.getLong("clean"),
                        rs.getBigDecimal("average_attempts"),
                        (Integer) rs.getObject("average_rating")))
                .list();
    }

    /**
     * @param cleanRate the number a decay model would eventually be fitted to
     */
    public record Calibration(String gapBucket, long returns, long solvedFirstTry,
                              java.math.BigDecimal averageAttempts, Integer averageProblemRating) {

        public double cleanRate() {
            return returns == 0 ? 0 : (double) solvedFirstTry / returns;
        }
    }
}
