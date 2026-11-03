/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Notification;
import io.reactivex.rxjava3.core.Observable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Notification materialization records an order feed and restores it for a consumer. */
public class NotificationOrderAuditTest {
    @Test
    @Timeout(60)
    public void orderAuditPreservesEventsAndCompletionWhenFeedIsReplayed() {
        Observable<OrderEvent> feed = Observable.fromIterable(List.of(
                new OrderEvent("ORD-204", "picked"),
                new OrderEvent("ORD-204", "packed"),
                new OrderEvent("ORD-204", "shipped")));

        List<Notification<OrderEvent>> journal = feed.materialize().toList().blockingGet();
        List<String> auditEntries = journal.stream()
                .map(NotificationOrderAuditTest::auditEntry)
                .toList();
        List<String> replayedStatuses = Observable.fromIterable(journal)
                .dematerialize(notification -> notification)
                .map(OrderEvent::status)
                .toList()
                .blockingGet();

        assertEquals(List.of(
                "event ORD-204 picked",
                "event ORD-204 packed",
                "event ORD-204 shipped",
                "feed complete"), auditEntries);
        assertEquals(List.of("picked", "packed", "shipped"), replayedStatuses);
    }

    private static String auditEntry(Notification<OrderEvent> notification) {
        if (notification.isOnNext()) {
            OrderEvent event = notification.getValue();
            return "event " + event.orderId() + " " + event.status();
        }
        return notification.isOnComplete() ? "feed complete" : "feed failed";
    }

    private record OrderEvent(String orderId, String status) {
    }
}
