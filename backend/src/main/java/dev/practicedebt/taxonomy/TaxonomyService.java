package dev.practicedebt.taxonomy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Loads the taxonomy file and maps the mirrored problemset onto it.
 *
 * <p>Rerunnable and deterministic. Editing the file and applying it again is the whole workflow, so
 * applying it twice must produce exactly the same mapping.
 */
@Service
public class TaxonomyService {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyService.class);

    private final TaxonomyLoader loader;
    private final TechniqueRepository techniques;
    private final JdbcClient db;

    public TaxonomyService(TaxonomyLoader loader, TechniqueRepository techniques, JdbcClient db) {
        this.loader = loader;
        this.techniques = techniques;
        this.db = db;
    }

    public Result apply() {
        Taxonomy taxonomy = loader.load();
        techniques.replaceTechniques(taxonomy);

        TechniqueMatcher matcher = new TechniqueMatcher(taxonomy);
        List<TechniqueRepository.Assignment> assignments = new ArrayList<>();
        long problems = 0;
        long unmapped = 0;

        for (MirroredProblem problem : allProblems()) {
            problems++;
            List<TechniqueMatcher.Assignment> matched =
                    matcher.match(problem.problemId(), problem.tags(), problem.rating());
            if (matched.isEmpty()) {
                unmapped++;
                continue;
            }
            for (TechniqueMatcher.Assignment a : matched) {
                assignments.add(new TechniqueRepository.Assignment(
                        problem.contestId(), problem.problemIndex(), a.techniqueId(),
                        a.source().name()));
            }
        }

        techniques.replaceAssignments(taxonomy.version(), assignments);

        log.info("Taxonomy v{} applied: {} techniques, {} problems, {} assignments, {} unmapped",
                taxonomy.version(), taxonomy.techniques().size(), problems, assignments.size(),
                unmapped);

        return new Result(taxonomy.version(), taxonomy.revised(), taxonomy.techniques().size(),
                problems, assignments.size(), unmapped, techniques.coverage());
    }

    /** The current mapping, without recomputing it. */
    public Result current() {
        Taxonomy taxonomy = loader.load();
        long mapped = techniques.mappedProblemCount();
        long problems = db.sql("select count(*) from problem").query(Long.class).single();
        return new Result(taxonomy.version(), taxonomy.revised(), taxonomy.techniques().size(),
                problems, -1, problems - mapped, techniques.coverage());
    }

    private List<MirroredProblem> allProblems() {
        return db.sql("select contest_id, problem_index, rating, tags from problem")
                .query((rs, n) -> {
                    java.sql.Array tags = rs.getArray("tags");
                    return new MirroredProblem(
                            rs.getInt("contest_id"),
                            rs.getString("problem_index"),
                            (Integer) rs.getObject("rating"),
                            tags == null ? List.of() : List.of((String[]) tags.getArray()));
                })
                .list();
    }

    private record MirroredProblem(int contestId, String problemIndex, Integer rating,
                                   List<String> tags) {

        String problemId() {
            return contestId + problemIndex;
        }
    }

    /**
     * @param unmapped problems no technique claimed. Reported rather than hidden: it is the honest
     *                 measure of how much of the problemset the taxonomy actually covers.
     */
    public record Result(int version, String revised, int techniques, long problems,
                         long assignments, long unmapped,
                         List<TechniqueRepository.Coverage> coverage) {
    }
}
