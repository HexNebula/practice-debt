package dev.practicedebt.mirror;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ContestRepository {

    private static final int BATCH_SIZE = 500;

    private static final String UPSERT = """
            insert into contest (id, name, type, phase, frozen, duration_seconds,
                                 start_time_seconds, last_mirrored_at)
            values (?, ?, ?, ?, ?, ?, ?, now())
            on conflict (id) do update
               set name               = excluded.name,
                   type               = excluded.type,
                   phase              = excluded.phase,
                   frozen             = excluded.frozen,
                   duration_seconds   = excluded.duration_seconds,
                   start_time_seconds = excluded.start_time_seconds,
                   last_mirrored_at   = now()
            """;

    private final JdbcTemplate jdbc;
    private final JdbcClient db;

    public ContestRepository(JdbcTemplate jdbc, JdbcClient db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    @Transactional
    public int upsertAll(List<Contest> contests) {
        int written = 0;
        for (int start = 0; start < contests.size(); start += BATCH_SIZE) {
            List<Contest> chunk = contests.subList(start, Math.min(start + BATCH_SIZE, contests.size()));
            int[] counts = jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Contest c = chunk.get(i);
                    ps.setInt(1, c.id());
                    ps.setString(2, c.name());
                    ps.setString(3, c.type());
                    ps.setString(4, c.phase());
                    if (c.frozen() == null) {
                        ps.setNull(5, Types.BOOLEAN);
                    } else {
                        ps.setBoolean(5, c.frozen());
                    }
                    setNullableLong(ps, 6, c.durationSeconds());
                    setNullableLong(ps, 7, c.startTimeSeconds());
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

    public long count() {
        return db.sql("select count(*) from contest").query(Long.class).single();
    }

    public Optional<Contest> find(int id) {
        return db.sql("""
                        select id, name, type, phase, frozen, duration_seconds, start_time_seconds
                          from contest where id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new Contest(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("phase"),
                        (Boolean) rs.getObject("frozen"),
                        (Long) rs.getObject("duration_seconds"),
                        (Long) rs.getObject("start_time_seconds")))
                .optional();
    }

    private static void setNullableLong(PreparedStatement ps, int position, Long value)
            throws SQLException {
        if (value == null) {
            ps.setNull(position, Types.BIGINT);
        } else {
            ps.setLong(position, value);
        }
    }
}
