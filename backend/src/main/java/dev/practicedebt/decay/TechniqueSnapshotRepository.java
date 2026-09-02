package dev.practicedebt.decay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import dev.practicedebt.mirror.SubmissionMirror;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point-in-time records of where each technique stood.
 *
 * <p>Mandatory rather than stylistic. Decay is a function of elapsed time, so a stateless view can
 * only ever describe today; without these rows there is no way to see that a technique has been
 * sliding for six months, and no way to check later whether the model's guess was any good.
 */
@Repository
public class TechniqueSnapshotRepository {

    private final JdbcClient db;

    public TechniqueSnapshotRepository(JdbcClient db) {
        this.db = db;
    }

    @Transactional
    public int take(String rawHandle, List<TechniqueActivity> activity, int taxonomyVersion) {
        String handle = SubmissionMirror.normalise(rawHandle);
        int written = 0;

        for (TechniqueActivity a : activity) {
            db.sql("""
                            insert into technique_snapshot (handle, technique_id, solved_count,
                                    last_solved_at, days_since_last, retention, half_life_days,
                                    taxonomy_version)
                            values (:handle, :technique, :solved, :lastSolved, :days, :retention,
                                    :halfLife, :version)
                            """)
                    .param("handle", handle)
                    .param("technique", a.techniqueId())
                    .param("solved", a.solvedCount())
                    .param("lastSolved", a.lastSolvedAt() == null
                            ? null
                            : java.sql.Timestamp.from(a.lastSolvedAt()))
                    .param("days", a.daysSinceLast())
                    .param("retention", BigDecimal.valueOf(a.retention())
                            .setScale(4, RoundingMode.HALF_UP))
                    .param("halfLife", DecayPolicy.HALF_LIFE_DAYS)
                    .param("version", taxonomyVersion)
                    .update();
            written++;
        }
        return written;
    }

    /** How a single technique has moved over time, oldest first. */
    public List<Point> history(String rawHandle, String techniqueId) {
        return db.sql("""
                        select taken_at, solved_count, days_since_last, retention, half_life_days
                          from technique_snapshot
                         where handle = :handle and technique_id = :technique
                         order by taken_at
                        """)
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .param("technique", techniqueId)
                .query((rs, n) -> new Point(
                        rs.getObject("taken_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getInt("solved_count"),
                        (Integer) rs.getObject("days_since_last"),
                        rs.getBigDecimal("retention"),
                        rs.getInt("half_life_days")))
                .list();
    }

    public long countFor(String rawHandle) {
        return db.sql("select count(*) from technique_snapshot where handle = :handle")
                .param("handle", SubmissionMirror.normalise(rawHandle))
                .query(Long.class)
                .single();
    }

    /**
     * @param halfLifeDays the guess in force when this snapshot was taken, kept so that changing
     *                     the guess later cannot silently rewrite what was recorded
     */
    public record Point(Instant takenAt, int solvedCount, Integer daysSinceLast,
                        BigDecimal retention, int halfLifeDays) {
    }
}
