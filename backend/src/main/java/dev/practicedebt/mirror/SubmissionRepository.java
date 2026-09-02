package dev.practicedebt.mirror;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubmissionRepository {

    private static final int BATCH_SIZE = 500;

    private static final String UPSERT = """
            insert into submission (id, handle, contest_id, problem_index, problem_name,
                                    creation_time_seconds, relative_time_seconds,
                                    participant_type, verdict, programming_language, mirrored_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            on conflict (id) do update
               set verdict              = excluded.verdict,
                   participant_type     = excluded.participant_type,
                   problem_name         = excluded.problem_name,
                   programming_language = excluded.programming_language,
                   mirrored_at          = now()
            """;

    private final JdbcTemplate jdbc;
    private final JdbcClient db;

    public SubmissionRepository(JdbcTemplate jdbc, JdbcClient db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    /**
     * Inserts or refreshes submissions.
     *
     * <p>The update list is deliberately narrow. A verdict genuinely changes upstream - rejudges
     * and successful hacks rewrite history - so it must be refreshed, but a submission's identity,
     * problem and creation time never move.
     */
    @Transactional
    public int upsertAll(List<Submission> submissions) {
        int written = 0;
        for (int start = 0; start < submissions.size(); start += BATCH_SIZE) {
            List<Submission> chunk =
                    submissions.subList(start, Math.min(start + BATCH_SIZE, submissions.size()));
            int[] counts = jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Submission s = chunk.get(i);
                    ps.setLong(1, s.id());
                    ps.setString(2, s.handle());
                    ps.setInt(3, s.contestId());
                    ps.setString(4, s.problemIndex());
                    ps.setString(5, s.problemName());
                    ps.setLong(6, s.creationTimeSeconds());
                    if (s.relativeTimeSeconds() == null) {
                        ps.setNull(7, Types.BIGINT);
                    } else {
                        ps.setLong(7, s.relativeTimeSeconds());
                    }
                    ps.setString(8, s.participantType());
                    ps.setString(9, s.verdict());
                    ps.setString(10, s.programmingLanguage());
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
            for (int c : counts) {
                written += Math.max(c, 0);
            }
        }
        return written;
    }

    /** Contests this handle submitted to that the contest mirror has never heard of. */
    public List<Integer> unknownContestIds(String handle) {
        return db.sql("""
                        select distinct s.contest_id
                          from submission s
                         where s.handle = :handle
                           and not exists (select 1 from contest c where c.id = s.contest_id)
                         order by s.contest_id
                        """)
                .param("handle", handle)
                .query(Integer.class)
                .list();
    }

    public long countFor(String handle) {
        return db.sql("select count(*) from submission where handle = :handle")
                .param("handle", handle)
                .query(Long.class)
                .single();
    }

    /** Distinct problems this handle has ever had accepted, in any participation mode. */
    public long solvedProblemCount(String handle) {
        return db.sql("""
                        select count(distinct (contest_id, problem_index))
                          from submission
                         where handle = :handle and verdict = 'OK'
                        """)
                .param("handle", handle)
                .query(Long.class)
                .single();
    }
}
