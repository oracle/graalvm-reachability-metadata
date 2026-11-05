/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Timestamp comparator based event ordering is the event timeline subsystem exercised here.
 *
 * An event processor receives records from several producers, orders them by their protobuf
 * timestamps, and emits the identifiers in the order in which the records should be handled.
 */
public class TimestampEventOrderingTest {

    @Test
    void ordersEventsWithSubsecondPrecision() throws Exception {
        List<TimestampedEvent> received =
                List.of(
                        new TimestampedEvent(
                                "payment-authorized",
                                Timestamps.parse("2024-04-18T10:00:00.250Z")),
                        new TimestampedEvent(
                                "order-created", Timestamps.parse("2024-04-18T09:59:59.999Z")),
                        new TimestampedEvent(
                                "inventory-reserved",
                                Timestamps.parse("2024-04-18T10:00:00.125Z")));

        Comparator<Timestamp> timelineOrder = Timestamps.comparator();
        List<String> processingOrder =
                received.stream()
                        .sorted((left, right) -> timelineOrder.compare(left.time(), right.time()))
                        .map(TimestampedEvent::name)
                        .toList();

        assertEquals(
                List.of("order-created", "inventory-reserved", "payment-authorized"),
                processingOrder);
        assertEquals(
                -1,
                timelineOrder.compare(received.get(2).time(), received.get(0).time()));
    }

    private record TimestampedEvent(String name, Timestamp time) {}
}
