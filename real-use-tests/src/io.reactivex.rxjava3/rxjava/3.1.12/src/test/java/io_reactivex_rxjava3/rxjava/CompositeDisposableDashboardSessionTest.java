/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CompositeDisposable manages the subscriptions in a dashboard session as views are refreshed. */
public class CompositeDisposableDashboardSessionTest {
    @Test
    @Timeout(60)
    public void clearingAndClosingTheDashboardStopsOldAndNewViews() {
        PublishSubject<ShipmentUpdate> updates = PublishSubject.create();
        CompositeDisposable sessionSubscriptions = new CompositeDisposable();
        List<String> timeline = new ArrayList<>();
        List<String> alerts = new ArrayList<>();

        sessionSubscriptions.add(updates.subscribe(update -> timeline.add(update.summary())));
        sessionSubscriptions.add(updates.filter(ShipmentUpdate::requiresAttention)
                .subscribe(update -> alerts.add(update.summary())));

        updates.onNext(new ShipmentUpdate("ORD-204", "packed", false));
        updates.onNext(new ShipmentUpdate("ORD-205", "delayed", true));

        assertEquals(List.of("ORD-204: packed", "ORD-205: delayed"), timeline);
        assertEquals(List.of("ORD-205: delayed"), alerts);

        sessionSubscriptions.clear();
        updates.onNext(new ShipmentUpdate("ORD-206", "delivered", false));

        assertEquals(List.of("ORD-204: packed", "ORD-205: delayed"), timeline);
        assertEquals(List.of("ORD-205: delayed"), alerts);
        assertTrue(!sessionSubscriptions.isDisposed());

        List<String> refreshedTimeline = new ArrayList<>();
        sessionSubscriptions.add(updates.subscribe(update -> refreshedTimeline.add(update.summary())));
        updates.onNext(new ShipmentUpdate("ORD-207", "picked", false));
        assertEquals(List.of("ORD-207: picked"), refreshedTimeline);

        sessionSubscriptions.dispose();
        updates.onNext(new ShipmentUpdate("ORD-208", "delayed", true));

        assertEquals(List.of("ORD-207: picked"), refreshedTimeline);
        assertTrue(sessionSubscriptions.isDisposed());
    }

    private record ShipmentUpdate(String orderId, String status, boolean requiresAttention) {
        private String summary() {
            return orderId + ": " + status;
        }
    }
}
