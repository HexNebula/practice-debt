package dev.practicedebt.mirror;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProblemRepository {

    /** Rows per batch. Large enough to matter, small enough not to hold one giant statement. */
    private static final int BATCH_SIZE = 500;

    private static final String UPSERT = """
            insert into problem (contest_id, problem_index, name, type, rating, points, tags,
                                 solved_count, last_mirrored_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, now())
            on conflict (contest_id, problem_index) do update
               set name             = excluded.name,
                   type             = excluded.type,
                   rating           = excluded.rating,
                   points           = excluded.points,
                   tags             = excluded.tags,
                   solved_count     = excluded.solved_count,
                   last_mirrored_at = now()
            """;

    private static final RowMapper<Problem> MAPPER = (rs, rowNum) -> {
        Array tags = rs.getArray("tags");
        return new Problem(
                rs.getInt("contest_id"),
                rs.getString("problem_index"),
                rs.getString("name"),
                rs.getString("type"),
                (Integer) rs.getObject("rating"),
                rs.getBigDecimal("points"),
                tags == null ? List.of() : List.of((String[]) tags.getArray()),
                (Integer) rs.getObject("solved_count"),
                rs.getObject("first_mirrored_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_mirrored_at", OffsetDateTime.class).toInstant());
    };

    private final JdbcTemplate jdbc;
    private final JdbcClient db;

    public ProblemRepository(JdbcTemplate jdbc, JdbcClient db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    /**
     * Inserts or refreshes every given problem.
     *
     * <p>Upsert rather than truncate-and-load: {@code first_mirrored_at} is the only record of when
     * a problem entered the mirror, and a reload would destroy it. Upstream never deletes problems,
     * so no row here is ever orphaned by a refresh.
     *
     * @return number of rows written
     */
    @Transactional
    public int upsertAll(List<Problem> problems) {
        int written = 0;
        for (int start = 0; start < problems.size(); start += BATCH_SIZE) {
            List<Problem> chunk = problems.subList(start, Math.min(start + BATCH_SIZE, problems.size()));
            int[] counts = jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Problem p = chunk.get(i);
                    ps.setInt(1, p.contestId());
                    ps.setString(2, p.index());
                    ps.setString(3, p.name());
                    ps.setString(4, p.type());
                    setNullableInt(ps, 5, p.rating());
                    setNullableDecimal(ps, 6, p.points());
                    ps.setArray(7, ps.getConnection()
                            .createArrayOf("text", p.tags().toArray(String[]::new)));
                    setNullableInt(ps, 8, p.solvedCount());
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

    /**
     * Records problems learned from a contest's standings rather than from the problemset.
     *
     * <p>Gym and group contests are absent from {@code problemset.problems} entirely, so their
     * problems can only be discovered this way. Deliberately does <em>not</em> touch
     * {@code solved_count}: the standings response carries no such figure, and writing null over a
     * value the problemset mirror already established would quietly destroy it.
     */
    @Transactional
    public int upsertFromContest(List<Problem> problems) {
        int written = 0;
        for (Problem p : problems) {
            written += jdbc.update("""
                            insert into problem (contest_id, problem_index, name, type, rating,
                                                 points, tags, last_mirrored_at)
                            values (?, ?, ?, ?, ?, ?, ?, now())
                            on conflict (contest_id, problem_index) do update
                               set name             = excluded.name,
                                   type             = excluded.type,
                                   rating           = coalesce(excluded.rating, problem.rating),
                                   points           = coalesce(excluded.points, problem.points),
                                   tags             = case
                                                          when cardinality(excluded.tags) > 0
                                                          then excluded.tags
                                                          else problem.tags
                                                      end,
                                   last_mirrored_at = now()
                            """,
                    ps -> {
                        ps.setInt(1, p.contestId());
                        ps.setString(2, p.index());
                        ps.setString(3, p.name());
                        ps.setString(4, p.type());
                        setNullableInt(ps, 5, p.rating());
                        setNullableDecimal(ps, 6, p.points());
                        ps.setArray(7, ps.getConnection()
                                .createArrayOf("text", p.tags().toArray(String[]::new)));
                    });
        }
        return written;
    }

    public long count() {
        return db.sql("select count(*) from problem").query(Long.class).single();
    }

    public Optional<Problem> find(int contestId, String index) {
        return db.sql("select * from problem where contest_id = :contestId and problem_index = :index")
                .param("contestId", contestId)
                .param("index", index)
                .query(MAPPER)
                .optional();
    }

    private static void setNullableInt(PreparedStatement ps, int position, Integer value)
            throws SQLException {
        if (value == null) {
            ps.setNull(position, Types.INTEGER);
        } else {
            ps.setInt(position, value);
        }
    }

    private static void setNullableDecimal(PreparedStatement ps, int position, BigDecimal value)
            throws SQLException {
        if (value == null) {
            ps.setNull(position, Types.NUMERIC);
        } else {
            ps.setBigDecimal(position, value);
        }
    }
}
