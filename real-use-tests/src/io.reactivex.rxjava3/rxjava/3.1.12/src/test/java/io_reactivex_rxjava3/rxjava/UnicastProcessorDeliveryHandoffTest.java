/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.processors.UnicastProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UnicastProcessor hands a buffered delivery batch to its single consumer. */
public class UnicastProcessorDeliveryHandoffTest {
    @Test
    @Timeout(60)
    public void bufferedDeliveryBatchIsDrainedByTheSingleConsumer() throws Exception {
        UnicastProcessor<DeliveryUpdate> handoff = UnicastProcessor.create(8);
        handoff.onNext(new DeliveryUpdate("ORD-204", "picked"));
        handoff.onNext(new DeliveryUpdate("ORD-204", "packed"));
        handoff.onNext(new DeliveryUpdate("ORD-204", "delivered"));
        handoff.onComplete();

        RecordingSubscriber subscriber = new RecordingSubscriber();
        handoff.subscribe(subscriber);
        subscriber.request(3);

        assertTrue(subscriber.completed.await(10, TimeUnit.SECONDS));
        assertNull(subscriber.error.get());
        assertEquals(List.of("ORD-204: picked", "ORD-204: packed", "ORD-204: delivered"),
                subscriber.updates);
    }

    private record DeliveryUpdate(String orderId, String status) {
        private String summary() {
            return orderId + ": " + status;
        }
    }

    private static final class RecordingSubscriber implements Subscriber<DeliveryUpdate> {
        private final List<String> updates = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(DeliveryUpdate update) {
            updates.add(update.summary());
        }

        @Override
        public void onError(Throwable failure) {
            error.set(failure);
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
