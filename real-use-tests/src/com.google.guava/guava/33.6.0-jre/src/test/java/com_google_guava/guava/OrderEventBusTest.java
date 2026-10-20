/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * In-process publish/subscribe with `EventBus` and `AsyncEventBus`: annotated subscriber discovery,
 * dispatch by event type hierarchy, dead events, unregistration, subscriber exception handling,
 * and concurrent asynchronous delivery.
 *
 * An order service publishes domain events on a bus. Inventory, billing, and audit components
 * register themselves as subscribers and react to the events they declare interest in; events
 * nobody listens to are routed to a dead-letter log, and a failing subscriber must not stop
 * delivery to the others.
 */
public class OrderEventBusTest {

    @Test
    void deliversEventsToSubscribersByDeclaredTypeAndSupertype() {
        EventBus bus = new EventBus("orders");
        Inventory inventory = new Inventory();
        AuditLog audit = new AuditLog();
        bus.register(inventory);
        bus.register(audit);

        bus.post(new OrderPlaced("o-1", "sku-keyboard", 2));
        bus.post(new OrderCancelled("o-1", "customer request"));
        bus.post(new OrderPlaced("o-2", "sku-monitor", 1));

        assertEquals(List.of("reserve sku-keyboard x2", "release o-1", "reserve sku-monitor x1"),
                inventory.actions);
        assertEquals(List.of("OrderPlaced o-1", "OrderCancelled o-1", "OrderPlaced o-2"), audit.entries);
        assertEquals("orders", bus.identifier());
    }

    @Test
    void routesUnhandledEventsToDeadEventSubscribersAndStopsAfterUnregister() {
        EventBus bus = new EventBus();
        Inventory inventory = new Inventory();
        DeadLetterLog deadLetters = new DeadLetterLog();
        bus.register(inventory);
        bus.register(deadLetters);

        PaymentCaptured payment = new PaymentCaptured("o-3", 4999L);
        bus.post(payment);
        bus.post(new OrderPlaced("o-4", "sku-mouse", 3));

        assertEquals(1, deadLetters.events.size());
        DeadEvent dead = deadLetters.events.get(0);
        assertSame(payment, dead.getEvent());
        assertSame(bus, dead.getSource());
        assertEquals(List.of("reserve sku-mouse x3"), inventory.actions);

        bus.unregister(inventory);
        bus.post(new OrderPlaced("o-5", "sku-desk", 1));

        assertEquals(List.of("reserve sku-mouse x3"), inventory.actions);
        assertEquals(2, deadLetters.events.size());
        assertEquals(OrderPlaced.class, deadLetters.events.get(1).getEvent().getClass());
        assertThrows(IllegalArgumentException.class, () -> bus.unregister(inventory));
    }

    @Test
    void reportsSubscriberFailuresWithoutInterruptingOtherSubscribers() {
        List<String> failures = new ArrayList<>();
        SubscriberExceptionHandler handler = (Throwable exception, SubscriberExceptionContext context) ->
                failures.add(context.getSubscriberMethod().getName() + " on "
                        + context.getSubscriber().getClass().getSimpleName() + " for "
                        + ((OrderPlaced) context.getEvent()).orderId() + ": " + exception.getMessage());
        EventBus bus = new EventBus(handler);
        FlakyBilling billing = new FlakyBilling();
        Inventory inventory = new Inventory();
        bus.register(billing);
        bus.register(inventory);

        bus.post(new OrderPlaced("o-6", "sku-chair", 1));

        assertEquals(List.of("charge on FlakyBilling for o-6: billing backend unavailable"), failures);
        assertEquals(List.of("reserve sku-chair x1"), inventory.actions);
    }

    @Test
    void deliversEventsConcurrentlyOnAnAsyncBus() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            AsyncEventBus bus = new AsyncEventBus("payments", executor);
            int payments = 50;
            Ledger ledger = new Ledger(payments);
            bus.register(ledger);

            for (int i = 1; i <= payments; i++) {
                bus.post(new PaymentCaptured("o-" + i, i * 100L));
            }

            assertTrue(ledger.done.await(10, TimeUnit.SECONDS));
            assertEquals(127_500L, ledger.totalCents.get());
            assertEquals(payments, ledger.orderIds.size());
            assertTrue(ledger.orderIds.contains("o-50"));
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /** Common supertype of order lifecycle events. */
    interface OrderEvent {
        String orderId();
    }

    record OrderPlaced(String orderId, String sku, int quantity) implements OrderEvent {
    }

    record OrderCancelled(String orderId, String reason) implements OrderEvent {
    }

    record PaymentCaptured(String orderId, long amountCents) {
    }

    /** Reserves and releases stock for specific order events. */
    static final class Inventory {
        final List<String> actions = new ArrayList<>();

        @Subscribe
        void onPlaced(OrderPlaced event) {
            actions.add("reserve " + event.sku() + " x" + event.quantity());
        }

        @Subscribe
        void onCancelled(OrderCancelled event) {
            actions.add("release " + event.orderId());
        }
    }

    /** Records every order event through the shared supertype. */
    static final class AuditLog {
        final List<String> entries = new ArrayList<>();

        @Subscribe
        void onAnyOrderEvent(OrderEvent event) {
            entries.add(event.getClass().getSimpleName() + " " + event.orderId());
        }
    }

    /** Collects events that reached no other subscriber. */
    static final class DeadLetterLog {
        final List<DeadEvent> events = new ArrayList<>();

        @Subscribe
        void onDeadEvent(DeadEvent event) {
            events.add(event);
        }
    }

    /** A billing component whose backend is down. */
    static final class FlakyBilling {
        @Subscribe
        void charge(OrderPlaced event) {
            throw new IllegalStateException("billing backend unavailable");
        }
    }

    /** A thread-safe ledger that accepts concurrent delivery. */
    static final class Ledger {
        final AtomicLong totalCents = new AtomicLong();
        final List<String> orderIds = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch done;

        Ledger(int expected) {
            done = new CountDownLatch(expected);
        }

        @Subscribe
        @AllowConcurrentEvents
        void onPayment(PaymentCaptured event) {
            totalCents.addAndGet(event.amountCents());
            orderIds.add(event.orderId());
            done.countDown();
        }
    }
}
