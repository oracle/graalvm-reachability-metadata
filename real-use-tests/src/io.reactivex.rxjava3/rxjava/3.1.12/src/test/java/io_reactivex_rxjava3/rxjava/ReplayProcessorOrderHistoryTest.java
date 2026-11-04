/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Replays the recent order history to a late subscriber and continues with live updates. */
public class ReplayProcessorOrderHistoryTest {
    @Test
    @Timeout(60)
    public void lateSubscriberReceivesRecentHistoryAndLiveOrderUpdates() throws Exception {
        ReplayProcessor<OrderEvent> orderHistory = ReplayProcessor.createWithSize(2);
        orderHistory.onNext(new OrderEvent("ORD-100", "created"));
        orderHistory.onNext(new OrderEvent("ORD-100", "paid"));
        orderHistory.onNext(new OrderEvent("ORD-100", "packed"));

        RecordingSubscriber subscriber = new RecordingSubscriber();
        orderHistory.subscribe(subscriber);
        subscriber.request(3);

        orderHistory.onNext(new OrderEvent("ORD-100", "shipped"));
        orderHistory.onComplete();

        assertTrue(subscriber.completed.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("paid", "packed", "shipped"), subscriber.statuses);
        assertNull(subscriber.error);
    }

    private record OrderEvent(String orderId, String status) {
    }

    private static final class RecordingSubscriber implements Subscriber<OrderEvent> {
        private final List<String> statuses = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private Subscription subscription;
        private Throwable error;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(OrderEvent event) {
            statuses.add(event.status());
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
