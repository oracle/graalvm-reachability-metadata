/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Asynchronous composition and service lifecycles with `com.google.common.util.concurrent`:
 * listening executors, `Futures` and `FluentFuture` chaining, fallbacks, aggregation, timeouts,
 * cancellation, callbacks, and `ServiceManager` coordinating idle, threaded, and scheduled services.
 *
 * A shipment pipeline quotes carriers in parallel, picks the cheapest quote, falls back when a
 * carrier fails, and runs a dispatcher, a tracking poller, and a warehouse connection as managed
 * services that start and stop together.
 */
public class ShipmentPipelineConcurrencyTest {

    @Test
    void composesParallelCarrierQuotesIntoTheCheapestBooking() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(
                Executors.newFixedThreadPool(3, namedThreads("quote-%d")));
        try {
            List<ListenableFuture<Quote>> quotes = new ArrayList<>();
            for (Map.Entry<String, Integer> carrier : ImmutableMap.of("ups", 1250, "dhl", 990, "fedex", 1100)
                    .entrySet()) {
                quotes.add(executor.submit(() -> new Quote(carrier.getKey(), carrier.getValue(),
                        Thread.currentThread().getName())));
            }

            ListenableFuture<Booking> booking = FluentFuture.from(Futures.allAsList(quotes))
                    .transform(all -> Collections.min(all, (a, b) -> Integer.compare(a.cents, b.cents)),
                            MoreExecutors.directExecutor())
                    .transformAsync(cheapest -> executor.submit(() -> new Booking("BK-" + cheapest.carrier,
                            cheapest)), MoreExecutors.directExecutor());

            Booking result = booking.get(10, TimeUnit.SECONDS);
            assertEquals("BK-dhl", result.reference);
            assertEquals(990, result.quote.cents);
            assertTrue(result.quote.thread.startsWith("quote-"), result.quote.thread);
        } finally {
            assertTrue(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(10)));
        }
    }

    @Test
    void fallsBackWhenACarrierFailsAndKeepsSuccessfulQuotes() throws Exception {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        try {
            ListenableFuture<Quote> failing = executor.submit(() -> {
                throw new CarrierUnavailableException("ups");
            });
            ListenableFuture<Quote> healthy = executor.submit(() -> new Quote("dhl", 990, "worker"));

            ListenableFuture<Quote> withFallback = Futures.catching(failing, CarrierUnavailableException.class,
                    error -> new Quote(error.getMessage() + "-ground", 1500, "fallback"),
                    MoreExecutors.directExecutor());
            assertEquals("ups-ground", withFallback.get(10, TimeUnit.SECONDS).carrier);

            List<Quote> successful = Futures.successfulAsList(failing, healthy).get(10, TimeUnit.SECONDS);
            assertEquals(2, successful.size());
            assertEquals(null, successful.get(0));
            assertEquals("dhl", successful.get(1).carrier);

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> Futures.allAsList(failing, healthy).get(10, TimeUnit.SECONDS));
            assertInstanceOf(CarrierUnavailableException.class, failure.getCause());

            UncheckedExecutionException unchecked = assertThrows(UncheckedExecutionException.class,
                    () -> Futures.getUnchecked(failing));
            assertInstanceOf(CarrierUnavailableException.class, unchecked.getCause());

            CarrierUnavailableException checked = assertThrows(CarrierUnavailableException.class,
                    () -> Futures.getChecked(failing, CarrierUnavailableException.class));
            assertInstanceOf(CarrierUnavailableException.class, checked.getCause());
        } finally {
            assertTrue(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(10)));
        }
    }

    @Test
    void timesOutSlowCarriersAndNotifiesCallbacks() throws Exception {
        ListeningScheduledExecutorService scheduler = MoreExecutors.listeningDecorator(
                Executors.newScheduledThreadPool(2, namedThreads("timer-%d")));
        try {
            SettableFuture<Quote> slowCarrier = SettableFuture.create();
            ListenableFuture<Quote> bounded = Futures.withTimeout(slowCarrier, Duration.ofMillis(100), scheduler);

            BlockingQueue<String> outcomes = new LinkedBlockingQueue<>();
            Futures.addCallback(bounded, new FutureCallback<Quote>() {
                @Override
                public void onSuccess(Quote quote) {
                    outcomes.add("quoted " + quote.carrier);
                }

                @Override
                public void onFailure(Throwable error) {
                    outcomes.add(error instanceof TimeoutException ? "timed out" : "failed " + error);
                }
            }, MoreExecutors.directExecutor());

            assertEquals("timed out", outcomes.poll(10, TimeUnit.SECONDS));
            ExecutionException timeout = assertThrows(ExecutionException.class, bounded::get);
            assertInstanceOf(TimeoutException.class, timeout.getCause());
            assertTrue(slowCarrier.isCancelled());

            ListenableFuture<Quote> delayed = scheduler.schedule(() -> new Quote("dhl", 990, "timer"), 20,
                    TimeUnit.MILLISECONDS);
            Futures.addCallback(delayed, new FutureCallback<Quote>() {
                @Override
                public void onSuccess(Quote quote) {
                    outcomes.add("quoted " + quote.carrier);
                }

                @Override
                public void onFailure(Throwable error) {
                    outcomes.add(error instanceof TimeoutException ? "timed out" : "failed " + error);
                }
            }, MoreExecutors.directExecutor());
            assertEquals("quoted dhl", outcomes.poll(10, TimeUnit.SECONDS));
        } finally {
            assertTrue(MoreExecutors.shutdownAndAwaitTermination(scheduler, Duration.ofSeconds(10)));
        }
    }

    @Test
    void propagatesCancellationThroughDerivedFutures() {
        SettableFuture<Quote> quote = SettableFuture.create();
        ListenableFuture<String> label = Futures.transform(quote, q -> q.carrier.toUpperCase(),
                MoreExecutors.directExecutor());
        AtomicInteger listenerRuns = new AtomicInteger();
        quote.addListener(listenerRuns::incrementAndGet, MoreExecutors.directExecutor());

        assertTrue(label.cancel(true));

        assertTrue(quote.isCancelled());
        assertTrue(label.isDone());
        assertEquals(1, listenerRuns.get());
        assertThrows(CancellationException.class, label::get);
        assertFalse(quote.set(new Quote("dhl", 990, "late")));

        SettableFuture<Quote> source = SettableFuture.create();
        SettableFuture<Quote> mirror = SettableFuture.create();
        assertTrue(mirror.setFuture(source));
        Quote delivered = new Quote("fedex", 1100, "main");
        source.set(delivered);
        assertSame(delivered, Futures.getUnchecked(mirror));
    }

    @Test
    void startsAndStopsManagedServicesTogether() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        WarehouseConnection warehouse = new WarehouseConnection(events);
        Dispatcher dispatcher = new Dispatcher(events);
        TrackingPoller poller = new TrackingPoller();

        ServiceManager manager = new ServiceManager(List.of(warehouse, dispatcher, poller));
        CountDownLatch healthy = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        manager.addListener(new ServiceManager.Listener() {
            @Override
            public void healthy() {
                healthy.countDown();
            }

            @Override
            public void stopped() {
                stopped.countDown();
            }
        }, MoreExecutors.directExecutor());

        manager.startAsync().awaitHealthy(Duration.ofSeconds(10));
        assertTrue(healthy.await(10, TimeUnit.SECONDS));
        assertTrue(manager.isHealthy());
        assertEquals(Service.State.RUNNING, warehouse.state());

        dispatcher.orders.put("o-1");
        dispatcher.orders.put("o-2");
        assertTrue(dispatcher.dispatched.await(10, TimeUnit.SECONDS));
        assertTrue(poller.polled.await(10, TimeUnit.SECONDS));

        Map<Service, Duration> startupTimes = manager.startupDurations();
        assertEquals(3, startupTimes.size());
        assertTrue(manager.servicesByState().get(Service.State.RUNNING).contains(poller));

        manager.stopAsync().awaitStopped(Duration.ofSeconds(10));
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        assertEquals(Service.State.TERMINATED, dispatcher.state());
        assertEquals(Service.State.TERMINATED, poller.state());
        assertEquals("warehouse closed", events.get(events.size() - 1));
        assertTrue(events.containsAll(List.of("warehouse opened", "dispatched o-1", "dispatched o-2")));
    }

    @Test
    void reportsAFailedServiceToTheManager() throws Exception {
        Service broken = new AbstractIdleService() {
            @Override
            protected void startUp() {
                throw new IllegalStateException("label printer offline");
            }

            @Override
            protected void shutDown() {
            }
        };
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        WarehouseConnection warehouse = new WarehouseConnection(events);
        ServiceManager manager = new ServiceManager(List.of(broken, warehouse));
        CountDownLatch failed = new CountDownLatch(1);
        manager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(Service service) {
                failed.countDown();
            }
        }, MoreExecutors.directExecutor());

        manager.startAsync();
        assertTrue(failed.await(10, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> manager.awaitHealthy(Duration.ofSeconds(10)));
        assertFalse(manager.isHealthy());
        assertEquals(Service.State.FAILED, broken.state());
        assertEquals("label printer offline", broken.failureCause().getMessage());

        manager.stopAsync().awaitStopped(Duration.ofSeconds(10));
        assertEquals(Service.State.TERMINATED, warehouse.state());
    }

    private static ThreadFactory namedThreads(String format) {
        return new ThreadFactoryBuilder().setNameFormat(format).setDaemon(true).build();
    }

    private static final class Quote {
        private final String carrier;
        private final int cents;
        private final String thread;

        Quote(String carrier, int cents, String thread) {
            this.carrier = carrier;
            this.cents = cents;
            this.thread = thread;
        }
    }

    private static final class Booking {
        private final String reference;
        private final Quote quote;

        Booking(String reference, Quote quote) {
            this.reference = reference;
            this.quote = quote;
        }
    }

    /// `Futures.getChecked` instantiates this type through its public constructors.
    public static final class CarrierUnavailableException extends Exception {
        public CarrierUnavailableException(String carrier) {
            super(carrier);
        }

        public CarrierUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class WarehouseConnection extends AbstractIdleService {
        private final List<String> events;

        WarehouseConnection(List<String> events) {
            this.events = events;
        }

        @Override
        protected void startUp() {
            events.add("warehouse opened");
        }

        @Override
        protected void shutDown() {
            events.add("warehouse closed");
        }
    }

    private static final class Dispatcher extends AbstractExecutionThreadService {
        private final BlockingQueue<String> orders = new LinkedBlockingQueue<>();
        private final CountDownLatch dispatched = new CountDownLatch(2);
        private final List<String> events;

        Dispatcher(List<String> events) {
            this.events = events;
        }

        @Override
        protected void run() throws InterruptedException {
            while (isRunning()) {
                String order = orders.poll(20, TimeUnit.MILLISECONDS);
                if (order != null) {
                    events.add("dispatched " + order);
                    dispatched.countDown();
                }
            }
        }
    }

    private static final class TrackingPoller extends AbstractScheduledService {
        private final CountDownLatch polled = new CountDownLatch(3);

        @Override
        protected void runOneIteration() {
            polled.countDown();
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedDelaySchedule(0, 10, TimeUnit.MILLISECONDS);
        }
    }
}
