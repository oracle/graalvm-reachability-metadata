/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import org.junit.jupiter.api.Test;

/**
 * Timestamp and duration arithmetic is the scheduling subsystem exercised here.
 *
 * A dispatch service receives RFC 3339 window boundaries, measures the elapsed
 * time, and returns the normalized end time after processing the window.
 */
public class DispatchWindowTimestampTest {

    @Test
    void measuresAndReconstructsDispatchWindow() throws Exception {
        Timestamp windowStart = Timestamps.parse("2024-04-18T09:15:27.125Z");
        Timestamp windowEnd = Timestamps.parse("2024-04-18T10:47:03.875Z");

        Duration elapsed = Timestamps.between(windowStart, windowEnd);

        assertEquals("5496.750s", Durations.toString(elapsed));
        assertEquals(5496L, Durations.toSeconds(elapsed));
        assertEquals(0.75, elapsed.getNanos() / 1_000_000_000.0);
        assertTrue(Timestamps.compare(windowStart, windowEnd) < 0);

        Timestamp reconstructedEnd = Timestamps.add(windowStart, elapsed);

        assertEquals("2024-04-18T10:47:03.875Z", Timestamps.toString(reconstructedEnd));
        assertEquals(windowEnd, reconstructedEnd);
        assertEquals(elapsed, Timestamps.between(windowStart, reconstructedEnd));
    }
}
