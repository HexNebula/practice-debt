package dev.practicedebt.debt;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stored rating costs, keyed by handle and problem. */
@Repository
public class DebtRatingCostRepository {

    private final JdbcClient db;

    public DebtRatingCostRepository(JdbcClient db) {
        this.db = db;
    }

    public void save(String handle, int contestId, String problemIndex, RatingCost cost) {
        db.sql("""
                        insert into debt_rating_cost (handle, contest_id, problem_index, rating_cost,
                                unrated, actual_rank, counterfactual_rank, actual_delta,
                                model_actual_delta, model_counterfactual_delta,
                                assumed_solve_seconds, assumed_wrong_attempts, computed_at)
                        values (:handle, :contestId, :index, :cost, :unrated, :actualRank,
                                :cfRank, :actualDelta, :modelActual, :modelCf, :solveSeconds,
                                :wrongAttempts, now())
                        on conflict (handle, contest_id, problem_index) do update
                           set rating_cost                = excluded.rating_cost,
                               unrated                    = excluded.unrated,
                               actual_rank                = excluded.actual_rank,
                               counterfactual_rank        = excluded.counterfactual_rank,
                               actual_delta               = excluded.actual_delta,
                               model_actual_delta         = excluded.model_actual_delta,
                               model_counterfactual_delta = excluded.model_counterfactual_delta,
                               assumed_solve_seconds      = excluded.assumed_solve_seconds,
                               assumed_wrong_attempts     = excluded.assumed_wrong_attempts,
                               computed_at                = now()
                        """)
                .param("handle", handle)
                .param("contestId", contestId)
                .param("index", problemIndex)
                .param("cost", cost.ratingCost())
                .param("unrated", cost.unrated())
                .param("actualRank", cost.actualRank())
                .param("cfRank", cost.counterfactualRank())
                .param("actualDelta", cost.actualDelta())
                .param("modelActual", cost.modelActualDelta())
                .param("modelCf", cost.modelCounterfactualDelta())
                .param("solveSeconds", cost.assumedSolveSeconds())
                .param("wrongAttempts", cost.assumedWrongAttempts())
                .update();
    }

    /** Everything computed for a handle, keyed {@code contestId + problemIndex}. */
    public Map<String, RatingCost> findAll(String handle) {
        Map<String, RatingCost> byProblem = new HashMap<>();
        db.sql("""
                        select contest_id, problem_index, rating_cost, unrated, actual_rank,
                               counterfactual_rank, actual_delta, model_actual_delta,
                               model_counterfactual_delta, assumed_solve_seconds,
                               assumed_wrong_attempts
                          from debt_rating_cost
                         where handle = :handle
                        """)
                .param("handle", handle)
                .query((rs, n) -> {
                    String key = rs.getInt("contest_id") + rs.getString("problem_index");
                    byProblem.put(key, new RatingCost(
                            (Integer) rs.getObject("rating_cost"),
                            rs.getBoolean("unrated"),
                            (Integer) rs.getObject("actual_rank"),
                            (Integer) rs.getObject("counterfactual_rank"),
                            (Integer) rs.getObject("actual_delta"),
                            (Integer) rs.getObject("model_actual_delta"),
                            (Integer) rs.getObject("model_counterfactual_delta"),
                            (Integer) rs.getObject("assumed_solve_seconds"),
                            (Integer) rs.getObject("assumed_wrong_attempts")));
                    return key;
                })
                .list();
        return byProblem;
    }
}
