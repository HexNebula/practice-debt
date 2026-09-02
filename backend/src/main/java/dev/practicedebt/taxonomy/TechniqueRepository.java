package dev.practicedebt.taxonomy;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TechniqueRepository {

    private final JdbcClient db;

    public TechniqueRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Loads the taxonomy over whatever is already there.
     *
     * <p>Upserted rather than wiped. Deleting the table and reinserting it was correct for the
     * technique rows themselves and catastrophic for everything keyed to them: snapshots and
     * recorded returns cascaded away, so editing the taxonomy file destroyed the history it exists
     * to accumulate. Techniques removed from the file are still removed, but by name rather than by
     * emptying the table.
     */
    @Transactional
    public void replaceTechniques(Taxonomy taxonomy) {
        db.sql("delete from problem_technique").update();

        List<String> defined = taxonomy.techniques().stream().map(Taxonomy.Technique::id).toList();
        db.sql("delete from technique where id not in (:defined)")
                .param("defined", defined)
                .update();

        int order = 0;
        for (Taxonomy.Technique technique : taxonomy.techniques()) {
            db.sql("""
                            insert into technique (id, name, family, summary, taxonomy_version,
                                                   display_order)
                            values (:id, :name, :family, :summary, :version, :order)
                            on conflict (id) do update
                               set name             = excluded.name,
                                   family           = excluded.family,
                                   summary          = excluded.summary,
                                   taxonomy_version = excluded.taxonomy_version,
                                   display_order    = excluded.display_order
                            """)
                    .param("id", technique.id())
                    .param("name", technique.name())
                    .param("family", technique.family())
                    .param("summary", technique.summary())
                    .param("version", taxonomy.version())
                    .param("order", order++)
                    .update();
        }
    }

    @Transactional
    public void replaceAssignments(int taxonomyVersion, List<Assignment> assignments) {
        db.sql("delete from problem_technique").update();
        for (Assignment a : assignments) {
            db.sql("""
                            insert into problem_technique (contest_id, problem_index, technique_id,
                                                           source, taxonomy_version)
                            values (:contestId, :index, :technique, :source, :version)
                            on conflict do nothing
                            """)
                    .param("contestId", a.contestId())
                    .param("index", a.problemIndex())
                    .param("technique", a.techniqueId())
                    .param("source", a.source())
                    .param("version", taxonomyVersion)
                    .update();
        }
    }

    public List<Coverage> coverage() {
        return db.sql("""
                        select t.id, t.name, t.family,
                               count(pt.*)                                          as problems,
                               count(pt.*) filter (where pt.source = 'PINNED')       as pinned,
                               count(*) filter (where p.rating is not null)          as rated,
                               round(avg(p.rating))::integer                         as average_rating
                          from technique t
                          left join problem_technique pt on pt.technique_id = t.id
                          left join problem p on p.contest_id = pt.contest_id
                                             and p.problem_index = pt.problem_index
                         group by t.id, t.name, t.family, t.display_order
                         order by t.display_order
                        """)
                .query((rs, n) -> new Coverage(
                        rs.getString("id"), rs.getString("name"), rs.getString("family"),
                        rs.getLong("problems"), rs.getLong("pinned"),
                        (Integer) rs.getObject("average_rating")))
                .list();
    }

    public long mappedProblemCount() {
        return db.sql("select count(distinct (contest_id, problem_index)) from problem_technique")
                .query(Long.class).single();
    }

    public record Assignment(int contestId, String problemIndex, String techniqueId, String source) {
    }

    public record Coverage(String id, String name, String family, long problems, long pinned,
                           Integer averageRating) {
    }
}
