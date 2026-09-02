package dev.practicedebt.cf;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPacerTest {

    @Test
    void spacesSequentialAcquisitionsByAtLeastTheInterval() throws Exception {
        RequestPacer pacer = new RequestPacer(Duration.ofMillis(50));

        List<Long> stamps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pacer.acquire();
            stamps.add(System.nanoTime());
        }

        for (int i = 1; i < stamps.size(); i++) {
            long gapMillis = (stamps.get(i) - stamps.get(i - 1)) / 1_000_000;
            assertThat(gapMillis)
                    .as("gap between call %d and %d", i - 1, i)
                    .isGreaterThanOrEqualTo(45);
        }
    }

    @Test
    void appliesTheIntervalAcrossThreadsBecauseCodeforcesLimitsPerIp() throws Exception {
        RequestPacer pacer = new RequestPacer(Duration.ofMillis(50));
        int threads = 4;

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    ready.countDown();
                    go.await();
                    pacer.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        long start = System.nanoTime();
        go.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        // Four callers, 50ms apart, means the last one cannot leave before t+150ms.
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(140);
    }

    @Test
    void doesNotBlockWhenIntervalIsZero() throws Exception {
        RequestPacer pacer = new RequestPacer(Duration.ZERO);

        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            pacer.acquire();
        }

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500);
    }
}
