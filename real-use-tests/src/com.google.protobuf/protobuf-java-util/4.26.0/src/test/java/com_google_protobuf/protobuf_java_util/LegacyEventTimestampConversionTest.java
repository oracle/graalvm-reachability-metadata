/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Conversion between legacy date values and protobuf timestamps is the event-ingestion subsystem
 * exercised here.
 *
 * An ingestion service accepts timestamps from a legacy Java API, converts them to protobuf values,
 * and forwards them to a service that also uses microsecond epoch timestamps.
 */
public class LegacyEventTimestampConversionTest {

    @Test
    void convertsLegacyDateAndMicrosecondEpochValues() {
        Date legacyReceivedAt = Date.from(Instant.parse("2024-04-18T10:47:03.125Z"));
        Timestamp receivedAt = Timestamps.fromDate(legacyReceivedAt);

        assertTrue(Timestamps.isValid(receivedAt));
        assertEquals("2024-04-18T10:47:03.125Z", Timestamps.toString(receivedAt));
        assertEquals(legacyReceivedAt.getTime(), Timestamps.toMillis(receivedAt));
        assertEquals(receivedAt, Timestamps.fromMillis(legacyReceivedAt.getTime()));

        long receivedAtMicros = 1_713_437_223_125_123L;
        Timestamp preciseReceivedAt = Timestamps.fromMicros(receivedAtMicros);

        assertEquals(receivedAtMicros, Timestamps.toMicros(preciseReceivedAt));
        assertEquals("2024-04-18T10:47:03.125123Z", Timestamps.toString(preciseReceivedAt));
        assertEquals(1_713_437_223_125L, Timestamps.toMillis(preciseReceivedAt));
    }
}
