/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the matcher-reset subsystem to reuse one compiled pattern while validating telemetry
 * records.
 */
public class ReusableMatcherTelemetryTest {

    @Test
    @Timeout(60)
    void readsValidTelemetryRecordsFromOneReusableMatcher() {
        TelemetryReader reader = new TelemetryReader();

        List<String> readings = reader.read(List.of(
                "sensor=boiler-7;value=82;unit=C",
                "sensor=boiler-7;value=not-a-number;unit=C",
                "sensor=tank-2;value=101;unit=kPa"));

        assertEquals(List.of("boiler-7=82 C", "tank-2=101 kPa"), readings);
    }

    /** Application reader that validates telemetry records received in a batch. */
    private static final class TelemetryReader {

        private final Pattern record = Pattern.compile("sensor=([a-z]+-[0-9]+);value=([0-9]+);unit=(C|kPa)");

        List<String> read(List<String> records) {
            Matcher matcher = record.matcher("");
            return records.stream()
                    .filter(record -> matcher.reset(record).matches())
                    .map(record -> record(matcher))
                    .toList();
        }

        private String record(Matcher matcher) {
            return matcher.group(1) + "=" + matcher.group(2) + " " + matcher.group(3);
        }
    }
}
