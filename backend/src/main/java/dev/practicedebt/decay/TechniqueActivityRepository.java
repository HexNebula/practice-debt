package dev.practicedebt.decay;

import java.time.Instant;
import java.util.List;

import dev.practicedebt.mirror.SubmissionMirror;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Derives per-technique activity from the submission mirror.
 *
 * <p>Freshness comes from <em>solving</em>, not from attempting. An attempt that failed is not
 * evidence the technique is intact - if anything it is evidence of the opposite - so only accepted
 * submissions count as touching a technique.
 */
@Repository
public class TechniqueActivityRepository {

    /**
     * A problem is credited to every technique it maps to, at the moment it was first solved.
     *
     * <p>Solves in any participation mode count. Practice is how a technique stays alive, and the
     * live/practice distinction that matters for abandoned debt is irrelevant here.
     */
    private static final String ACTIVITY = """
            with solved as (
                select s.contest_id,
                       s.problem_index,
                       min(s.creation_time_seconds) as solved_at
                  from submission s
                 where s.handle = :handle
                   and s.verdict = 'OK'
                 group by s.contest_id, s.problem_index
            ),
            per_technique as (
                select pt.technique_id,
                       count(*)                          as solved_count,
                       max(solved.solved_at)             as last_solved_at,
                       count(*) filter (
                           where solved.solved_at > extract(epoch from now()) - 30 * 86400
                       )                                 as solved_last_30
                  from solved
                  join problem_technique pt
                    on pt.contest_id = solved.contest_id
                   and pt.problem_index = solved.problem_index
                 group by pt.technique_id
            )
            select t.id,
                   t.name,
                   t.family,
                   coalesce(pt.solved_count, 0)   as solved_count,
                   pt.last_solved_at,
                   coalesce(pt.solved_last_30, 0) as solved_last_30
              from technique t
              left join per_technique pt on pt.technique_id = t.id
             order by t.display_order
            """;

    private final JdbcClient db;

    public TechniqueActivityRepository(JdbcClient db) {
        this.db = db;
    }

    public List<TechniqueActivity> activityFor(String rawHandle) {
        return db.sql(ACTIVITY)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .query((rs, n) -> {
                    Long lastSolvedSeconds = (Long) rs.getObject("last_solved_at");
                    Instant lastSolvedAt = lastSolvedSeconds == null
                            ? null
                            : Instant.ofEpochSecond(lastSolvedSeconds);
                    Integer daysSince = lastSolvedAt == null
                            ? null
                            : (int) java.time.Duration.between(lastSolvedAt, Instant.now()).toDays();

                    return new TechniqueActivity(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("family"),
                            rs.getInt("solved_count"),
                            lastSolvedAt,
                            daysSince,
                            rs.getInt("solved_last_30"),
                            daysSince == null ? 0.0 : DecayPolicy.retention(daysSince));
                })
                .list();
    }

    /**
     * Problems in a technique the handle has never solved, nearest to a target difficulty.
     *
     * <p>The suggested action is a <em>different</em> problem in the technique, never a re-solve.
     * Re-solving something already beaten tests memory of a solution, not command of a technique.
     */
    public List<Suggestion> suggestionsFor(String rawHandle, String techniqueId, int targetRating,
            int limit) {
        return db.sql("""
                        select p.contest_id, p.problem_index, p.name, p.rating, p.solved_count
                          from problem p
                          join problem_technique pt
                            on pt.contest_id = p.contest_id
                           and pt.problem_index = p.problem_index
                         where pt.technique_id = :technique
                           and p.rating between :low and :high
                           and not exists (
                                   select 1 from submission s
                                    where s.handle = :handle
                                      and s.contest_id = p.contest_id
                                      and s.problem_index = p.problem_index
                                      and s.verdict = 'OK')
                         order by abs(p.rating - :target), p.solved_count desc nulls last
                         limit :limit
                        """)
                .param("technique", techniqueId)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .param("target", targetRating)
                .param("low", targetRating - DecayPolicy.SUGGESTION_RATING_BELOW)
                .param("high", targetRating + DecayPolicy.SUGGESTION_RATING_ABOVE)
                .param("limit", limit)
                .query((rs, n) -> new Suggestion(
                        rs.getInt("contest_id"), rs.getString("problem_index"),
                        rs.getString("name"), (Integer) rs.getObject("rating")))
                .list();
    }

    /**
     * The handle's current rating, taken from the most recent contest it changed in.
     *
     * <p>Ordered by when the contest ran rather than by contest id, which is close to chronological
     * but not reliably so.
     */
    public Integer currentRating(String rawHandle) {
        return db.sql("""
                        select rc.new_rating
                          from rating_change rc
                          join contest c on c.id = rc.contest_id
                         where rc.handle = :handle
                         order by c.start_time_seconds desc nulls last
                         limit 1
                        """)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .query(Integer.class)
                .optional()
                .orElse(null);
    }

    public record Suggestion(int contestId, String problemIndex, String name, Integer rating) {

        public String problemId() {
            return contestId + problemIndex;
        }

        public String url() {
            return "https://codeforces.com/contest/" + contestId + "/problem/" + problemIndex;
        }
    }
}
