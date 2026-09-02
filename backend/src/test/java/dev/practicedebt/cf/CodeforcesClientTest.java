package dev.practicedebt.cf;

import java.time.Duration;
import java.util.List;

import dev.practicedebt.cf.dto.CfProblemset;
import dev.practicedebt.cf.dto.CfSubmission;
import dev.practicedebt.cf.dto.ParticipantType;
import dev.practicedebt.config.CodeforcesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The payloads here are trimmed copies of real responses. Field shapes were confirmed against the
 * live API first; see {@code docs/cf-api-notes.md}.
 */
class CodeforcesClientTest {

    private static final String BASE = "https://codeforces.com/api";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private CodeforcesClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CodeforcesClient(builder.build(), new RequestPacer(Duration.ZERO), props(3));
    }

    private static CodeforcesProperties props(int maxAttempts) {
        return new CodeforcesProperties(
                BASE,
                "",
                "",
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                maxAttempts,
                Duration.ofMillis(1),
                new CodeforcesProperties.Mirror(false, Duration.ofHours(12), Duration.ofHours(6)));
    }

    @Test
    void unwrapsTheOkEnvelopeAndKeepsOptionalFieldsNull() {
        server.expect(requestTo(BASE + "/problemset.problems"))
                .andRespond(withSuccess("""
                        {"status":"OK","result":{
                          "problems":[
                            {"contestId":2258,"index":"F","name":"Plus Minus Tree","type":"PROGRAMMING",
                             "points":3000.0,"tags":["dp","trees"]},
                            {"contestId":1985,"index":"A","name":"Creating Words","type":"PROGRAMMING",
                             "rating":800,"tags":["strings"]}
                          ],
                          "problemStatistics":[
                            {"contestId":2258,"index":"F","solvedCount":116}
                          ]}}
                        """, MediaType.APPLICATION_JSON));

        CfProblemset result = client.problemset();

        assertThat(result.problems()).hasSize(2);
        // Verified upstream: rating is absent for some problems, points for ICPC-scored ones.
        assertThat(result.problems().get(0).rating()).isNull();
        assertThat(result.problems().get(1).points()).isNull();
        assertThat(result.problems().get(0).tagsOrEmpty()).containsExactly("dp", "trees");
        assertThat(result.problemStatistics()).hasSize(1);
        server.verify();
    }

    @Test
    void readsParticipantTypeBecauseAbandonedDebtDependsOnIt() {
        server.expect(requestTo(BASE + "/user.status?handle=tourist&from=1&count=2"))
                .andRespond(withSuccess("""
                        {"status":"OK","result":[
                          {"id":1,"contestId":2245,"creationTimeSeconds":1784221884,
                           "problem":{"contestId":2245,"index":"G","name":"NPC Challenge","type":"PROGRAMMING"},
                           "author":{"contestId":2245,"participantType":"CONTESTANT",
                                     "members":[{"handle":"tourist"}],"ghost":false},
                           "verdict":"WRONG_ANSWER"},
                          {"id":2,"contestId":2245,"creationTimeSeconds":1784321884,
                           "problem":{"contestId":2245,"index":"G","name":"NPC Challenge","type":"PROGRAMMING"},
                           "author":{"contestId":2245,"participantType":"PRACTICE",
                                     "members":[{"handle":"tourist"}],"ghost":false},
                           "verdict":"OK"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<CfSubmission> submissions = client.userStatus("tourist", 1, 2);

        assertThat(submissions).hasSize(2);
        assertThat(submissions.get(0).author().participantType()).isEqualTo(ParticipantType.CONTESTANT);
        assertThat(submissions.get(0).accepted()).isFalse();
        assertThat(submissions.get(1).accepted()).isTrue();
        server.verify();
    }

    @Test
    void aRefusalIsPermanentAndKeepsUpstreamsComment() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/user.rating?handle=nosuchhandle"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":"FAILED","comment":"handles: User with handle nosuchhandle not found"}
                                """));

        assertThatThrownBy(() -> client.userRating("nosuchhandle"))
                .isInstanceOf(CodeforcesApiException.class)
                .satisfies(e -> assertThat(((CodeforcesApiException) e).comment())
                        .contains("not found"));

        // One attempt only: retrying a refusal just spends rate limit.
        server.verify();
    }

    @Test
    void retriesTheRateLimitRefusalAndSucceeds() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/problemset.problems"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Limit exceeded"));
        server.expect(ExpectedCount.once(), requestTo(BASE + "/problemset.problems"))
                .andRespond(withSuccess("""
                        {"status":"OK","result":{"problems":[],"problemStatistics":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.problemset().problems()).isEmpty();
        server.verify();
    }

    @Test
    void givesUpAsUnavailableRatherThanRetryingForever() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/problemset.problems"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

        assertThatThrownBy(() -> client.problemset())
                .isInstanceOf(CodeforcesUnavailableException.class)
                .hasMessageContaining("after 3 attempts");

        server.verify();
    }
}
