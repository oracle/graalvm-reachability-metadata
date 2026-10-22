/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.NativeLongByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises JNA's structures passed by reference to render UTC audit timestamps through C time functions.
 */
public class NativeUtcAuditTimestampTest {

    @Test
    @Timeout(60)
    public void rendersAuditEventsAcrossLeapDayWithoutUsingTheHostTimeZone() {
        NativeTime clock = Native.load(Platform.C_LIBRARY_NAME, NativeTime.class);
        BrokenDownTime calendar = new BrokenDownTime();
        byte[] output = new byte[64];
        List<String> eventTimes = List.of(
                "2024-02-29T23:59:59Z",
                "2024-03-01T00:00:00Z",
                "1970-01-01T00:00:00Z");

        for (String eventTime : eventTimes) {
            Instant event = Instant.parse(eventTime);
            NativeLongByReference seconds = new NativeLongByReference(new NativeLong(event.getEpochSecond()));
            // gmtime_r writes into caller-owned struct tm storage; JNA reads its fields back after the call.
            assertNotNull(clock.gmtime_r(seconds, calendar), "The event must have a representable UTC calendar time");
            OffsetDateTime expected = event.atOffset(ZoneOffset.UTC);
            assertEquals(expected.getYear(), calendar.tm_year + 1900);
            assertEquals(expected.getMonthValue(), calendar.tm_mon + 1);
            assertEquals(expected.getDayOfMonth(), calendar.tm_mday);
            assertEquals(expected.getDayOfYear(), calendar.tm_yday + 1);
            assertEquals(expected.getDayOfWeek().getValue() % 7, calendar.tm_wday);
            assertEquals(0, calendar.tm_isdst, "UTC timestamps must not acquire a daylight-saving adjustment");

            // Passing the same structure to strftime must retain the native-written hour, minute and second.
            SizeT written = clock.strftime(output, new SizeT(output.length), "%Y-%m-%dT%H:%M:%SZ", calendar);
            assertEquals(eventTime.length(), written.longValue(), "The audit timestamp must fit without truncation");
            assertEquals(eventTime, Native.toString(output, StandardCharsets.US_ASCII.name()),
                    "Audit output must preserve the event instant, including the leap-day rollover");
        }
    }

    public interface NativeTime extends Library {
        Pointer gmtime_r(NativeLongByReference seconds, BrokenDownTime output);

        SizeT strftime(byte[] output, SizeT capacity, String format, BrokenDownTime calendar);
    }

    // POSIX struct tm, including the UTC offset and zone fields used by Linux and macOS libc.
    @FieldOrder({"tm_sec", "tm_min", "tm_hour", "tm_mday", "tm_mon", "tm_year",
            "tm_wday", "tm_yday", "tm_isdst", "tm_gmtoff", "tm_zone"})
    public static class BrokenDownTime extends Structure {
        public int tm_sec;
        public int tm_min;
        public int tm_hour;
        public int tm_mday;
        public int tm_mon;
        public int tm_year;
        public int tm_wday;
        public int tm_yday;
        public int tm_isdst;
        public NativeLong tm_gmtoff;
        public Pointer tm_zone;
    }

    public static class SizeT extends IntegerType {
        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }
}
