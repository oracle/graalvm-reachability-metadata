/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the event-executor and promise subsystem of netty-common: `DefaultEventExecutorGroup`
 * task submission, `Promise` completion and listeners, `PromiseCombiner` aggregation, scheduled
 * and periodic tasks, and graceful shutdown of the executors.
 *
 * A travel site asks several airline suppliers for a fare on a group of supplier executors and
 * combines their answers on one aggregator executor. The cheapest fare wins when every supplier
 * answers, one failing supplier fails the whole search, and an abandoned search is cancelled. A
 * delayed retry and a periodic price-refresh run on the aggregator's schedule, tasks on one
 * executor run in submission order on its single thread, and a shut-down executor refuses work.
 */
class QuoteAggregationEventExecutorTest {

    private static final long WAIT_SECONDS = 10;

    private DefaultEventExecutorGroup suppliers;

    private DefaultEventExecutor aggregator;

    @BeforeEach
    void startExecutors() {
        suppliers = new DefaultEventExecutorGroup(3, new DefaultThreadFactory("fare-supplier"));
        aggregator = new DefaultEventExecutor(new DefaultThreadFactory("fare-aggregator"));
    }

    @AfterEach
    void stopExecutors() throws Exception {
        suppliers.shutdownGracefully(0, 1, TimeUnit.SECONDS).await(WAIT_SECONDS, TimeUnit.SECONDS);
        aggregator.shutdownGracefully(0, 1, TimeUnit.SECONDS).await(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void cheapestFareWinsWhenEverySupplierAnswers() throws Exception {
        FareSearch search = new FareSearch(suppliers, aggregator);

        Future<Fare> best = search.cheapest("LHR-JFK", Map.of("aurora", 640, "borealis", 515, "cirrus", 702));

        assertThat(best.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(best.isSuccess()).isTrue();
        assertThat(best.getNow()).isEqualTo(new Fare("borealis", "LHR-JFK", 515));
        assertThat(search.answered()).containsOnlyKeys("aurora", "borealis", "cirrus");
        assertThat(search.answered().values()).allSatisfy(thread -> assertThat(thread).startsWith("fare-supplier"));
        assertThat(search.combinedOn()).startsWith("fare-aggregator");
    }

    @Test
    void oneFailingSupplierFailsTheWholeSearch() throws Exception {
        FareSearch search = new FareSearch(suppliers, aggregator);

        Future<Fare> best = search.cheapest("CDG-NRT", Map.of("aurora", 990, "borealis", -1, "cirrus", 870));

        assertThat(best.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(best.isSuccess()).isFalse();
        assertThat(best.isCancelled()).isFalse();
        assertThat(best.getNow()).isNull();
        assertThat(best.cause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("borealis has no fare for CDG-NRT");
        assertThatThrownBy(best::sync).isSameAs(best.cause());
    }

    @Test
    void abandonedSearchIsCancelledAndItsListenerSeesIt() throws Exception {
        Promise<Fare> pending = aggregator.newPromise();
        CountDownLatch notified = new CountDownLatch(1);
        AtomicReference<Throwable> seenCause = new AtomicReference<>();
        AtomicReference<String> notifiedOn = new AtomicReference<>();
        pending.addListener((FutureListener<Fare>) future -> {
            seenCause.set(future.cause());
            notifiedOn.set(Thread.currentThread().getName());
            notified.countDown();
        });

        assertThat(pending.isCancellable()).isTrue();
        assertThat(pending.cancel(false)).isTrue();
        assertThat(notified.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(pending.isCancelled()).isTrue();
        assertThat(pending.isDone()).isTrue();
        assertThat(seenCause.get()).isInstanceOf(CancellationException.class);
        assertThat(notifiedOn.get()).startsWith("fare-aggregator");
        assertThat(pending.trySuccess(new Fare("aurora", "LHR-JFK", 1))).isFalse();
        assertThat(pending.getNow()).isNull();
    }

    @Test
    void delayedRetryAndPeriodicRefreshRunOnTheAggregatorSchedule() throws Exception {
        ScheduledFuture<Fare> retry = aggregator.schedule(
                () -> new Fare("cirrus", "SFO-SYD", 1180), 20, TimeUnit.MILLISECONDS);

        assertThat(retry.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(retry.getNow()).isEqualTo(new Fare("cirrus", "SFO-SYD", 1180));

        List<Integer> refreshed = new ArrayList<>();
        CountDownLatch threeRefreshes = new CountDownLatch(3);
        ScheduledFuture<?> refresh = aggregator.scheduleAtFixedRate(() -> {
            synchronized (refreshed) {
                refreshed.add(refreshed.size() + 1);
            }
            threeRefreshes.countDown();
        }, 0, 10, TimeUnit.MILLISECONDS);

        assertThat(threeRefreshes.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(refresh.cancel(false)).isTrue();
        assertThat(refresh.isCancelled()).isTrue();
        synchronized (refreshed) {
            assertThat(refreshed).startsWith(1, 2, 3);
        }
    }

    @Test
    void tasksOnOneExecutorRunInSubmissionOrderOnItsThread() throws Exception {
        List<String> log = new ArrayList<>();
        List<Future<?>> submitted = new ArrayList<>();
        for (String step : List.of("reserve", "price", "confirm", "email")) {
            submitted.add(aggregator.submit(() -> {
                log.add(step + "@" + Thread.currentThread().getName() + "@" + aggregator.inEventLoop());
            }));
        }
        for (Future<?> task : submitted) {
            assertThat(task.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(task.isSuccess()).isTrue();
        }

        assertThat(aggregator.inEventLoop()).isFalse();
        String thread = aggregator.submit(() -> Thread.currentThread().getName()).get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(thread).startsWith("fare-aggregator");
        assertThat(log).containsExactly(
                "reserve@" + thread + "@true",
                "price@" + thread + "@true",
                "confirm@" + thread + "@true",
                "email@" + thread + "@true");
    }

    @Test
    void waitingOnAPromiseFromItsOwnExecutorIsRefused() throws Exception {
        Promise<Fare> inner = aggregator.newPromise();
        Future<Throwable> attempt = aggregator.submit(() -> {
            try {
                inner.await();
                return null;
            } catch (BlockingOperationException refused) {
                return refused;
            }
        });

        assertThat(attempt.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(BlockingOperationException.class);
        assertThat(inner.isDone()).isFalse();
        assertThat(inner.trySuccess(new Fare("aurora", "LHR-JFK", 640))).isTrue();
        assertThat(inner.sync().getNow()).isEqualTo(new Fare("aurora", "LHR-JFK", 640));
    }

    @Test
    void shutDownExecutorTerminatesAndRefusesNewWork() throws Exception {
        DefaultEventExecutor reporting = new DefaultEventExecutor(new DefaultThreadFactory("fare-report"));
        Future<String> report = reporting.submit(() -> "daily report");
        assertThat(report.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("daily report");

        Future<?> termination = reporting.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        assertThat(termination).isSameAs(reporting.terminationFuture());
        assertThat(termination.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(termination.isSuccess()).isTrue();
        assertThat(reporting.isShuttingDown()).isTrue();
        assertThat(reporting.isShutdown()).isTrue();
        assertThat(reporting.isTerminated()).isTrue();
        assertThatThrownBy(() -> reporting.execute(() -> { }))
                .isInstanceOf(RejectedExecutionException.class);
    }

    /// A fare offered by one airline supplier for a route.
    private record Fare(String supplier, String route, int price) {
    }

    /// Asks every supplier for a fare on the supplier group and combines the answers on the
    /// aggregator executor, which `PromiseCombiner` requires to be its own event loop.
    private static final class FareSearch {

        private final DefaultEventExecutorGroup suppliers;

        private final EventExecutor aggregator;

        private final Map<String, String> answered = new TreeMap<>();

        private volatile String combinedOn;

        FareSearch(DefaultEventExecutorGroup suppliers, EventExecutor aggregator) {
            this.suppliers = suppliers;
            this.aggregator = aggregator;
        }

        /// A negative price in `offers` makes that supplier fail instead of answering.
        Future<Fare> cheapest(String route, Map<String, Integer> offers) {
            Promise<Fare> best = aggregator.newPromise();
            aggregator.execute(() -> {
                PromiseCombiner combiner = new PromiseCombiner(aggregator);
                List<Future<Fare>> quotes = new ArrayList<>();
                for (Map.Entry<String, Integer> offer : new TreeMap<>(offers).entrySet()) {
                    Future<Fare> quote = suppliers.next().submit(() -> quote(offer.getKey(), route, offer.getValue()));
                    quotes.add(quote);
                    combiner.add(quote);
                }
                Promise<Void> all = aggregator.newPromise();
                all.addListener(done -> {
                    combinedOn = Thread.currentThread().getName();
                    if (!done.isSuccess()) {
                        best.setFailure(done.cause());
                        return;
                    }
                    Fare cheapest = null;
                    for (Future<Fare> quote : quotes) {
                        Fare fare = quote.getNow();
                        if (cheapest == null || fare.price() < cheapest.price()) {
                            cheapest = fare;
                        }
                    }
                    best.setSuccess(cheapest);
                });
                combiner.finish(all);
            });
            return best;
        }

        private Fare quote(String supplier, String route, int price) {
            synchronized (answered) {
                answered.put(supplier, Thread.currentThread().getName());
            }
            if (price < 0) {
                throw new IllegalStateException(supplier + " has no fare for " + route);
            }
            return new Fare(supplier, route, price);
        }

        Map<String, String> answered() {
            synchronized (answered) {
                return Map.copyOf(answered);
            }
        }

        String combinedOn() {
            return combinedOn;
        }
    }
}
