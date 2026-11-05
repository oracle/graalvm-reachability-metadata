/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import org.joni.Matcher;
import org.joni.Option;
import org.joni.Region;
import org.joni.Regex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Joni's named capture lookup subsystem to route an order identifier by its named fields.
public class NamedCaptureLookupTest {
    @Test
    @Timeout(60)
    public void resolvesNamedFieldsWhenRoutingAnOrderRequest() {
        Regex orderRequest = new Regex("(?<prefix>[A-Z]+)-(?<number>[0-9]+)");
        byte[] request = "ORD-4821".getBytes(StandardCharsets.UTF_8);

        Matcher matcher = orderRequest.matcher(request);
        assertTrue(matcher.search(0, request.length, Option.DEFAULT) >= 0);
        Region captures = matcher.getRegion();

        assertEquals(2, orderRequest.numberOfNames());
        assertEquals("ORD", capturedText(request, captures, orderRequest, "prefix"));
        assertEquals("4821", capturedText(request, captures, orderRequest, "number"));
    }

    private static String capturedText(byte[] input, Region captures, Regex regex, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        int group = regex.nameToBackrefNumber(nameBytes, 0, nameBytes.length, captures);
        return new String(
                input,
                captures.getBeg(group),
                captures.getEnd(group) - captures.getBeg(group),
                StandardCharsets.UTF_8);
    }
}
