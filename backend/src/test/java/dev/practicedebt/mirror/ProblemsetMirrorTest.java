package dev.practicedebt.mirror;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.practicedebt.cf.CodeforcesClient;
import dev.practicedebt.cf.CodeforcesUnavailableException;
import dev.practicedebt.cf.dto.CfProblem;
import dev.practicedebt.cf.dto.CfProblemStatistics;
import dev.practicedebt.cf.dto.CfProblemset;
import dev.practicedebt.config.CodeforcesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemsetMirrorTest {

    private CodeforcesClient codeforces;
    private ProblemRepository problems;
    private MirrorRunRepository runs;
    private ProblemsetMirror mirror;

    @BeforeEach
    void setUp() {
        codeforces = mock(CodeforcesClient.class);
        problems = mock(ProblemRepository.class);
        runs = mock(MirrorRunRepository.class);
        when(runs.start(anyString())).thenReturn(7L);

        mirror = new ProblemsetMirror(codeforces, problems, runs, props(Duration.ofHours(12)));
    }

    private static CodeforcesProperties props(Duration maxAge) {
        return new CodeforcesProperties(
                "https://codeforces.com/api",
                "",
                "",
                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO,
                new CodeforcesProperties.Mirror(true, maxAge, Duration.ofHours(6)));
    }

    @Test
    void joinsSolvedCountsOntoTheMatchingProblem() {
        when(codeforces.problemset()).thenReturn(new CfProblemset(
                List.of(problem(2258, "F", 3000.0, null, List.of("dp", "trees")),
                        problem(1985, "A", null, 800, List.of("strings"))),
                List.of(new CfProblemStatistics(2258, "F", 116),
                        new CfProblemStatistics(1985, "A", 42000))));
        when(problems.upsertAll(anyList())).thenReturn(2);

        mirror.refresh();

        List<Problem> written = capture();
        assertThat(written).hasSize(2);
        assertThat(written.get(0).solvedCount()).isEqualTo(116);
        assertThat(written.get(0).points()).isEqualByComparingTo("3000.00");
        assertThat(written.get(0).rating()).isNull();
        assertThat(written.get(1).solvedCount()).isEqualTo(42000);
        assertThat(written.get(1).rating()).isEqualTo(800);
        assertThat(written.get(1).displayId()).isEqualTo("1985A");
    }

    @Test
    void dropsProblemsThatBelongToNoContest() {
        // ACMSGURU problems arrive with no contestId. They cannot be failed in a live contest and
        // there is no contest for a reason string to name, so they are not mirrored.
        CfProblem acmsguru = new CfProblem(null, "ACMSGURU", "125", "Shtirlits", "PROGRAMMING",
                null, 1900, List.of("dfs and similar"));

        when(codeforces.problemset()).thenReturn(new CfProblemset(
                List.of(acmsguru, problem(1985, "A", null, 800, List.of("strings"))),
                List.of()));
        when(problems.upsertAll(anyList())).thenReturn(1);

        ProblemsetMirror.Result result = mirror.refresh();

        assertThat(capture()).extracting(Problem::displayId).containsExactly("1985A");
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void survivesAProblemWithNoStatisticsRow() {
        when(codeforces.problemset()).thenReturn(new CfProblemset(
                List.of(problem(1985, "A", null, 800, List.of("strings"))),
                List.of()));
        when(problems.upsertAll(anyList())).thenReturn(1);

        mirror.refresh();

        assertThat(capture().get(0).solvedCount()).isNull();
    }

    @Test
    void marksTheRunFailedWhenCodeforcesIsUnreachable() {
        when(codeforces.problemset())
                .thenThrow(new CodeforcesUnavailableException("problemset.problems: timeout"));

        assertThatThrownBy(() -> mirror.refresh())
                .isInstanceOf(CodeforcesUnavailableException.class);

        verify(runs).fail(anyLong(), anyString());
        verify(runs, never()).succeed(anyLong(), anyInt());
        verify(problems, never()).upsertAll(anyList());
    }

    @Test
    void skipsTheFetchEntirelyWhenTheMirrorIsStillFresh() {
        when(runs.lastSuccessAt(ProblemsetMirror.SOURCE))
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(1))));

        assertThat(mirror.refreshIfStale()).isEmpty();

        verify(codeforces, never()).problemset();
    }

    @Test
    void refreshesWhenTheMirrorIsOlderThanMaxAge() {
        when(runs.lastSuccessAt(ProblemsetMirror.SOURCE))
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(20))));
        when(codeforces.problemset()).thenReturn(new CfProblemset(List.of(), List.of()));
        when(problems.upsertAll(anyList())).thenReturn(0);

        assertThat(mirror.refreshIfStale()).isPresent();

        verify(codeforces).problemset();
    }

    @Test
    void refreshesWhenTheMirrorHasNeverRun() {
        when(runs.lastSuccessAt(ProblemsetMirror.SOURCE)).thenReturn(Optional.empty());
        when(codeforces.problemset()).thenReturn(new CfProblemset(List.of(), List.of()));
        when(problems.upsertAll(anyList())).thenReturn(0);

        assertThat(mirror.refreshIfStale()).isPresent();

        verify(codeforces).problemset();
    }

    @SuppressWarnings("unchecked")
    private List<Problem> capture() {
        ArgumentCaptor<List<Problem>> captor = ArgumentCaptor.forClass(List.class);
        verify(problems).upsertAll(captor.capture());
        return captor.getValue();
    }

    private static CfProblem problem(Integer contestId, String index, Double points, Integer rating,
            List<String> tags) {
        return new CfProblem(contestId, null, index, "problem " + index, "PROGRAMMING",
                points, rating, tags);
    }
}
