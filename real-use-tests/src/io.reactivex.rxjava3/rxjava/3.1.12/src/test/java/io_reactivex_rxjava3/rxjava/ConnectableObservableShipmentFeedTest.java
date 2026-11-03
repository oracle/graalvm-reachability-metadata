/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.core.Observable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ConnectableObservable multicasts one shipment feed to independent application views. */
public class ConnectableObservableShipmentFeedTest {
    @Test
    @Timeout(60)
    public void shipmentFeedConnectsAfterBothViewsSubscribeAndSharesOneSource() throws Exception {
        AtomicInteger sourceSubscriptions = new AtomicInteger();
        List<ShipmentEvent> events = List.of(
                new ShipmentEvent("ORD-204", "picked", false),
                new ShipmentEvent("ORD-204", "packed", false),
                new ShipmentEvent("ORD-204", "delivered", true));
        ConnectableObservable<ShipmentEvent> published = Observable.fromIterable(events)
                .doOnSubscribe(ignored -> sourceSubscriptions.incrementAndGet())
                .publish();
        Observable<ShipmentEvent> shared = published.autoConnect(2);
        RecordingObserver<String> warehouseView = new RecordingObserver<>();
        RecordingObserver<String> customerView = new RecordingObserver<>();

        shared.map(ShipmentEvent::status).subscribe(warehouseView);
        shared.filter(ShipmentEvent::customerVisible)
                .map(event -> event.orderId() + ": " + event.status())
                .subscribe(customerView);

        assertTrue(warehouseView.completed.await(10, TimeUnit.SECONDS));
        assertTrue(customerView.completed.await(10, TimeUnit.SECONDS));
        assertNull(warehouseView.error.get());
        assertNull(customerView.error.get());
        assertEquals(List.of("picked", "packed", "delivered"), warehouseView.values);
        assertEquals(List.of("ORD-204: delivered"), customerView.values);
        assertEquals(1, sourceSubscriptions.get());
    }

    private record ShipmentEvent(String orderId, String status, boolean customerVisible) {
    }

    private static final class RecordingObserver<T> implements Observer<T> {
        private final List<T> values = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onSubscribe(Disposable disposable) {
        }

        @Override
        public void onNext(T value) {
            values.add(value);
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
    }
}
