package dev.practicedebt.mirror;

import java.math.BigDecimal;
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

/** Mirrored ranklists, problem values and rating changes. */
@Repository
public class ContestResultsRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final JdbcClient db;

    public ContestResultsRepository(JdbcTemplate jdbc, JdbcClient db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    @Transactional
    public void replaceStandings(int contestId, List<StandingsRow> rows,
            List<ContestProblem> problems) {
        // Standings are replaced wholesale rather than upserted: a rejudge can remove a party, and
        // a stale row left behind would silently distort every rank computed against it.
        db.sql("delete from standings_row where contest_id = :id").param("id", contestId).update();
        db.sql("delete from contest_problem where contest_id = :id").param("id", contestId).update();

        batch("""
                insert into standings_row (contest_id, party_key, rank, points, penalty)
                values (?, ?, ?, ?, ?)
                """, rows, (ps, r) -> {
            ps.setInt(1, contestId);
            ps.setString(2, r.partyKey());
            ps.setInt(3, r.rank());
            ps.setBigDecimal(4, r.points());
            ps.setInt(5, r.penalty());
        });

        batch("""
                insert into contest_problem (contest_id, problem_index, max_points)
                values (?, ?, ?)
                """, problems, (ps, p) -> {
            ps.setInt(1, contestId);
            ps.setString(2, p.problemIndex());
            if (p.maxPoints() == null) {
                ps.setNull(3, Types.NUMERIC);
            } else {
                ps.setBigDecimal(3, p.maxPoints());
            }
        });
    }

    @Transactional
    public void replaceRatingChanges(int contestId, List<RatingChange> changes) {
        db.sql("delete from rating_change where contest_id = :id").param("id", contestId).update();
        batch("""
                insert into rating_change (contest_id, handle, rank, old_rating, new_rating)
                values (?, ?, ?, ?, ?)
                """, changes, (ps, c) -> {
            ps.setInt(1, contestId);
            ps.setString(2, c.handle());
            ps.setInt(3, c.rank());
            ps.setInt(4, c.oldRating());
            ps.setInt(5, c.newRating());
        });
    }

    /**
     * Records a handle's own rating history, contest by contest.
     *
     * <p>Shares a table with the per-contest rating changes because it is the same fact seen from
     * the other side. Upserted rather than replaced: these rows and the ones from
     * {@code contest.ratingChanges} fill in different parts of the same picture.
     */
    @Transactional
    public int upsertRatingHistory(List<RatingChange> rows, int[] contestIds) {
        int written = 0;
        for (int i = 0; i < rows.size(); i++) {
            RatingChange row = rows.get(i);
            written += db.sql("""
                            insert into rating_change (contest_id, handle, rank, old_rating, new_rating)
                            values (:contestId, :handle, :rank, :oldRating, :newRating)
                            on conflict (contest_id, handle) do update
                               set rank = excluded.rank,
                                   old_rating = excluded.old_rating,
                                   new_rating = excluded.new_rating
                            """)
                    .param("contestId", contestIds[i])
                    .param("handle", row.handle())
                    .param("rank", row.rank())
                    .param("oldRating", row.oldRating())
                    .param("newRating", row.newRating())
                    .update();
        }
        return written;
    }

    public boolean hasStandings(int contestId) {
        return db.sql("select exists (select 1 from standings_row where contest_id = :id)")
                .param("id", contestId).query(Boolean.class).single();
    }

    public boolean hasRatingChanges(int contestId) {
        return db.sql("select exists (select 1 from rating_change where contest_id = :id)")
                .param("id", contestId).query(Boolean.class).single();
    }

    public List<StandingsRow> standings(int contestId) {
        return db.sql("""
                        select party_key, rank, points, penalty
                          from standings_row where contest_id = :id
                        """)
                .param("id", contestId)
                .query((rs, n) -> new StandingsRow(rs.getString("party_key"), rs.getInt("rank"),
                        rs.getBigDecimal("points"), rs.getInt("penalty")))
                .list();
    }

    /** The row a handle occupies, including team rows where they are one of several members. */
    public Optional<StandingsRow> standingsRowFor(int contestId, String handle) {
        return db.sql("""
                        select party_key, rank, points, penalty
                          from standings_row
                         where contest_id = :id
                           and (party_key = :handle
                                or party_key like :prefix
                                or party_key like :middle
                                or party_key like :suffix)
                         order by rank
                         limit 1
                        """)
                .param("id", contestId)
                .param("handle", handle)
                .param("prefix", handle + ",%")
                .param("middle", "%," + handle + ",%")
                .param("suffix", "%," + handle)
                .query((rs, n) -> new StandingsRow(rs.getString("party_key"), rs.getInt("rank"),
                        rs.getBigDecimal("points"), rs.getInt("penalty")))
                .optional();
    }

    public List<RatingChange> ratingChanges(int contestId) {
        return db.sql("""
                        select handle, rank, old_rating, new_rating
                          from rating_change where contest_id = :id
                        """)
                .param("id", contestId)
                .query((rs, n) -> new RatingChange(rs.getString("handle"), rs.getInt("rank"),
                        rs.getInt("old_rating"), rs.getInt("new_rating")))
                .list();
    }

    public Optional<BigDecimal> maxPoints(int contestId, String problemIndex) {
        return db.sql("""
                        select max_points from contest_problem
                         where contest_id = :id and problem_index = :index
                        """)
                .param("id", contestId).param("index", problemIndex)
                .query(BigDecimal.class)
                .optional();
    }

    private <T> void batch(String sql, List<T> items, Setter<T> setter) {
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            List<T> chunk = items.subList(start, Math.min(start + BATCH_SIZE, items.size()));
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    setter.set(ps, chunk.get(i));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    @FunctionalInterface
    private interface Setter<T> {
        void set(PreparedStatement ps, T item) throws SQLException;
    }

    /** A party's line in a ranklist. Only what a rank recount needs. */
    public record StandingsRow(String partyKey, int rank, BigDecimal points, int penalty) {
    }

    public record ContestProblem(String problemIndex, BigDecimal maxPoints) {
    }

    /** A rated participant's pre-contest rating and outcome. */
    public record RatingChange(String handle, int rank, int oldRating, int newRating) {

        public int delta() {
            return newRating - oldRating;
        }
    }
}
