/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises Joni's exact-match subsystem to validate order identifiers at an application boundary. */
public class OrderIdentifierValidationTest {
    @Test
    @Timeout(60)
    public void acceptsOnlyACompleteOrderIdentifier() {
        Regex orderIdentifier = new Regex("\\AORD-[0-9]{4}\\z");
        byte[] validOrder = "ORD-4821".getBytes(StandardCharsets.UTF_8);
        byte[] invalidOrder = "ORD-48A1".getBytes(StandardCharsets.UTF_8);

        Matcher validMatcher = orderIdentifier.matcher(validOrder);
        assertEquals(validOrder.length, validMatcher.match(0, validOrder.length, Option.DEFAULT));
        assertEquals(0, validMatcher.getBegin());
        assertEquals(validOrder.length, validMatcher.getEnd());

        Matcher invalidMatcher = orderIdentifier.matcher(invalidOrder);
        assertEquals(Matcher.FAILED, invalidMatcher.match(0, invalidOrder.length, Option.DEFAULT));
    }
}
