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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the prefix-matching subsystem to parse a batch header before handing off its payload.
 */
public class BatchHeaderParsingTest {

    @Test
    @Timeout(60)
    void parsesHeaderAndLeavesPayloadForTheBatchConsumer() {
        String batch = "BATCH id=orders-eu count=2\n"
                + "order=ORD-2048;total=19.95\norder=ORD-2049;total=7.50";
        BatchParser parser = new BatchParser();

        Batch parsed = parser.parse(batch);

        assertEquals("orders-eu", parsed.id());
        assertEquals(2, parsed.count());
        assertEquals(
                "order=ORD-2048;total=19.95\norder=ORD-2049;total=7.50",
                parsed.payload());
    }

    /** Application parser that validates a batch header and forwards the remaining body unchanged. */
    private static final class BatchParser {

        private final Pattern header = Pattern.compile(
                "BATCH\\s+id=(?<id>[a-z-]+)\\s+count=(?<count>[0-9]+)\\n");

        Batch parse(String batch) {
            Matcher matcher = header.matcher(batch);
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("Invalid batch header");
            }
            return new Batch(
                    matcher.group("id"),
                    Integer.parseInt(matcher.group("count")),
                    batch.substring(matcher.end()));
        }
    }

    private record Batch(String id, int count, String payload) {
    }
}
