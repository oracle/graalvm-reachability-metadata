/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Processes an inventory event stream with Flowable operators, backpressure, and a controlled scheduler. */
public class FlowableInventoryPipelineTest {
    @Test
    @Timeout(60)
    public void inventoryAlertsRespectBackpressureAndUseFallbackAfterMalformedEvent() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        Flowable<InventoryEvent> events = Flowable.create(emitter -> {
            emitter.setCancellable(() -> sourceCancelled.set(true));
            emitter.onNext(new InventoryEvent("SKU-17", "7"));
            emitter.onNext(new InventoryEvent("SKU-23", "11"));
            emitter.onNext(new InventoryEvent("SKU-29", "unknown"));
            emitter.onNext(new InventoryEvent("SKU-31", "15"));
            emitter.onComplete();
        }, BackpressureStrategy.BUFFER);

        Flowable<String> alerts = events
                .observeOn(scheduler, false, 2)
                .map(event -> new ParsedInventory(event.sku(), Integer.parseInt(event.quantity())))
                .filter(event -> event.quantity() >= 10)
                .map(event -> event.sku() + " needs replenishment")
                .onErrorReturnItem("inventory stream unavailable")
                .take(2);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        alerts.subscribe(subscriber);

        subscriber.request(2);
        scheduler.triggerActions();

        assertTrue(subscriber.completed.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("SKU-23 needs replenishment", "inventory stream unavailable"),
                subscriber.values);
        assertNull(subscriber.error);
        assertTrue(sourceCancelled.get());
    }

    private record InventoryEvent(String sku, String quantity) {
    }

    private record ParsedInventory(String sku, int quantity) {
    }

    private static final class RecordingSubscriber implements Subscriber<String> {
        private final List<String> values = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private Subscription subscription;
        private Throwable error;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(String value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            completed.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }

        private void request(long amount) {
            subscription.request(amount);
        }
    }
}
