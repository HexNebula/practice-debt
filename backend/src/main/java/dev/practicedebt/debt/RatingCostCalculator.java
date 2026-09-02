package dev.practicedebt.debt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import dev.practicedebt.mirror.Contest;
import dev.practicedebt.mirror.ContestRepository;
import dev.practicedebt.mirror.ContestResultsMirror;
import dev.practicedebt.mirror.ContestResultsRepository;
import dev.practicedebt.mirror.ContestResultsRepository.RatingChange;
import dev.practicedebt.mirror.ContestResultsRepository.StandingsRow;
import dev.practicedebt.rating.ContestScoring;
import dev.practicedebt.rating.RatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Estimates what abandoning a problem cost in rating.
 *
 * <p>The counterfactual: the author's final submission to the problem passed instead of failing,
 * and nobody else in the contest did anything differently. Their score rises, their rank rises, and
 * the rating formula is re-run over the same field.
 *
 * <h2>The modelling choices, stated</h2>
 *
 * <ul>
 *   <li><b>Solve time</b> is the moment of their last live attempt at the problem. They were
 *       demonstrably still working on it then, and it is a fact from their own history rather than
 *       an assumption about how fast someone else solves.
 *   <li><b>Wrong attempts</b> are the failures <em>before</em> that last one. In the counterfactual
 *       the last submission is the accepted one, so it is not also a penalty.
 *   <li><b>Everyone else is frozen.</b> This is wrong — a stronger showing would have shifted the
 *       whole ranklist — but it is the assumption the spec calls for and it is surfaced rather than
 *       buried.
 *   <li><b>Competitors the author overtakes drop exactly one place.</b> Nobody else's score moves.
 * </ul>
 */
@Service
public class RatingCostCalculator {

    private static final Logger log = LoggerFactory.getLogger(RatingCostCalculator.class);

    private final ContestRepository contests;
    private final ContestResultsRepository results;
    private final ContestResultsMirror mirror;

    public RatingCostCalculator(ContestRepository contests, ContestResultsRepository results,
            ContestResultsMirror mirror) {
        this.contests = contests;
        this.results = results;
        this.mirror = mirror;
    }

    /**
     * @param handle              lowercase handle
     * @param lastAttemptSeconds  seconds from contest start of their final live attempt
     * @param liveWrongAttempts   failed live submissions to the problem, compile errors excluded
     */
    public Optional<RatingCost> compute(String handle, int contestId, String problemIndex,
            long lastAttemptSeconds, int liveWrongAttempts) {

        mirror.ensureMirrored(contestId, false);

        Optional<Contest> contest = contests.find(contestId);
        Optional<StandingsRow> row = results.standingsRowFor(contestId, handle);
        if (contest.isEmpty() || row.isEmpty()) {
            log.debug("No standings row for {} in contest {}", handle, contestId);
            return Optional.empty();
        }

        List<RatingChange> rated = results.ratingChanges(contestId);
        if (rated.isEmpty()) {
            // Unrated contest. The cost is zero because there was no rating at stake, which is a
            // different statement from "we could not work it out".
            return Optional.of(RatingCost.unrated(row.get().rank()));
        }

        int myIndex = indexOf(rated, handle);
        if (myIndex < 0) {
            // In the ranklist but not rated for it - out of competition, or already unrated.
            return Optional.of(RatingCost.unrated(row.get().rank()));
        }

        // In the counterfactual the final failed submission is the accepted one, so it no longer
        // counts against them.
        int assumedWrong = Math.max(0, liveWrongAttempts - 1);
        int counterfactualRank = counterfactualRank(contestId, contest.get(), row.get(),
                problemIndex, lastAttemptSeconds, assumedWrong);

        int[] ratings = rated.stream().mapToInt(RatingChange::oldRating).toArray();
        int[] actualRanks = rated.stream().mapToInt(RatingChange::rank).toArray();
        int actualRank = row.get().rank();

        int[] counterfactualRanks = shiftRanks(actualRanks, myIndex, actualRank, counterfactualRank);

        int modelActual = RatingSystem.deltas(actualRanks, ratings)[myIndex];
        int modelCounterfactual = RatingSystem.deltas(counterfactualRanks, ratings)[myIndex];

        // Both runs share whatever bias the model carries, so the difference is the trustworthy
        // part. Never negative: solving one more problem cannot cost rating.
        int cost = Math.max(0, modelCounterfactual - modelActual);

        return Optional.of(new RatingCost(cost, false, actualRank, counterfactualRank,
                rated.get(myIndex).delta(), modelActual, modelCounterfactual,
                (int) lastAttemptSeconds, assumedWrong));
    }

    /** Where the author lands once the abandoned problem is added to their score. */
    private int counterfactualRank(int contestId, Contest contest, StandingsRow mine,
            String problemIndex, long solvedAtSeconds, int wrongAttempts) {

        BigDecimal maxPoints = results.maxPoints(contestId, problemIndex).orElse(null);
        ContestScoring.Score improved = ContestScoring.afterAlsoSolving(
                contest.type(), mine.points(), mine.penalty(), maxPoints,
                solvedAtSeconds, wrongAttempts);

        int better = 0;
        for (StandingsRow other : results.standings(contestId)) {
            if (other.partyKey().equals(mine.partyKey())) {
                continue;
            }
            ContestScoring.Score theirs = new ContestScoring.Score(other.points(), other.penalty());
            if (theirs.beats(improved)) {
                better++;
            }
        }
        return better + 1;
    }

    /**
     * Everyone the author overtakes drops exactly one place; everyone else is untouched.
     */
    private static int[] shiftRanks(int[] ranks, int myIndex, int actualRank, int newRank) {
        int[] shifted = ranks.clone();
        for (int i = 0; i < shifted.length; i++) {
            if (i == myIndex) {
                shifted[i] = newRank;
            } else if (shifted[i] >= newRank && shifted[i] <= actualRank) {
                shifted[i] = shifted[i] + 1;
            }
        }
        return shifted;
    }

    private static int indexOf(List<RatingChange> rated, String handle) {
        for (int i = 0; i < rated.size(); i++) {
            if (rated.get(i).handle().equals(handle)) {
                return i;
            }
        }
        return -1;
    }
}
