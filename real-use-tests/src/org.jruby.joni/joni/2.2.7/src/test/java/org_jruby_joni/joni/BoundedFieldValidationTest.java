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

/// Exercises Joni's region-bounded matching subsystem to validate a code field inside a message.
public class BoundedFieldValidationTest {
    @Test
    @Timeout(60)
    public void validatesOnlyTheCodeFieldWithinAFramedMessage() {
        byte[] message = "event=ship;code=ABC-1234;backup=XYZ-9999".getBytes(StandardCharsets.UTF_8);
        int codeStart = indexOf(message, "ABC-1234");
        int codeEnd = codeStart + "ABC-1234".length();
        Regex trackingCode = new Regex("[A-Z]{3}-[0-9]{4}");
        Matcher matcher = trackingCode.matcher(message);

        assertEquals(codeStart, matcher.search(codeStart, codeEnd, Option.DEFAULT));
        assertEquals(codeStart, matcher.getBegin());
        assertEquals(codeEnd, matcher.getEnd());
        assertEquals("ABC-1234", matchedText(message, matcher));
        assertEquals(Matcher.FAILED, matcher.search(matcher.getEnd(), codeEnd, Option.DEFAULT));
    }

    private static int indexOf(byte[] input, String value) {
        return new String(input, StandardCharsets.UTF_8).indexOf(value);
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
