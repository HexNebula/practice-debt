package dev.practicedebt.mirror;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fetch bookkeeping.
 *
 * <p>This is not logging. Decay is a function of elapsed time, so when data last arrived is itself
 * data - and staleness of the mirror has to be answerable without reading a log file.
 */
@Repository
public class MirrorRunRepository {

    private final JdbcClient db;

    public MirrorRunRepository(JdbcClient db) {
        this.db = db;
    }

    public long start(String source) {
        return db.sql("""
                        insert into mirror_run (source, status)
                        values (:source, 'RUNNING')
                        returning id
                        """)
                .param("source", source)
                .query(Long.class)
                .single();
    }

    public void succeed(long id, int itemCount) {
        db.sql("""
                        update mirror_run
                           set status = 'SUCCESS', finished_at = now(), item_count = :itemCount
                         where id = :id
                        """)
                .param("itemCount", itemCount)
                .param("id", id)
                .update();
    }

    public void fail(long id, String error) {
        db.sql("""
                        update mirror_run
                           set status = 'FAILED', finished_at = now(), error = :error
                         where id = :id
                        """)
                .param("error", error)
                .param("id", id)
                .update();
    }

    /** When the given source last completed successfully, if it ever has. */
    public Optional<Instant> lastSuccessAt(String source) {
        return db.sql("""
                        select max(finished_at)
                          from mirror_run
                         where source = :source and status = 'SUCCESS'
                        """)
                .param("source", source)
                .query(Instant.class)
                .optional();
    }

    /** Most recent runs, newest first, for the status endpoint. */
    public List<MirrorRun> recent(int limit) {
        return db.sql("""
                        select id, source, status, started_at, finished_at, item_count, error
                          from mirror_run
                         order by started_at desc
                         limit :limit
                        """)
                .param("limit", limit)
                .query((rs, rowNum) -> new MirrorRun(
                        rs.getLong("id"),
                        rs.getString("source"),
                        MirrorRun.Status.valueOf(rs.getString("status")),
                        rs.getObject("started_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("finished_at", java.time.OffsetDateTime.class) == null
                                ? null
                                : rs.getObject("finished_at", java.time.OffsetDateTime.class).toInstant(),
                        (Integer) rs.getObject("item_count"),
                        rs.getString("error")))
                .list();
    }
}
