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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the UTF-8 byte-array matching subsystem to route order events without decoding them
 * first.
 */
public class Utf8EventRoutingTest {

    @Test
    @Timeout(60)
    void routesAnOrderEventFromItsUtf8WireRepresentation() {
        byte[] event = "source=mobile;order=ORD-2048;state=ready".getBytes(StandardCharsets.UTF_8);
        EventRouter router = new EventRouter();

        OrderEvent routed = router.route(event);

        assertEquals("ORD-2048", routed.orderId());
        assertEquals("ready", routed.state());
        assertEquals("order=ORD-2048;state=ready", routed.original());
    }

    /** Application code that routes events received as UTF-8 bytes. */
    private static final class EventRouter {

        private final Pattern orderEvent = Pattern.compile(
                "order=(?<id>[A-Z]+-[0-9]+);state=(?<state>[a-z]+)");

        OrderEvent route(byte[] event) {
            Matcher matcher = orderEvent.matcher(event);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Not an order event");
            }
            return new OrderEvent(matcher.group("id"), matcher.group("state"), matcher.group());
        }
    }

    /** The values an order consumer needs after routing one event. */
    private record OrderEvent(String orderId, String state, String original) {
    }
}
