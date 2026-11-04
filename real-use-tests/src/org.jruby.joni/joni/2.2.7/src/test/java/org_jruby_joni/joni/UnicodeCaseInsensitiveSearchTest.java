/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises Joni's UTF-8 encoding subsystem for case-insensitive customer searches. */
public class UnicodeCaseInsensitiveSearchTest {
    @Test
    @Timeout(60)
    public void findsInternationalizedCustomerNameWithoutChangingTheMatchedText() {
        byte[] pattern = "caf\u00e9".getBytes(StandardCharsets.UTF_8);
        byte[] message = "Payment received from CAF\u00c9 today".getBytes(StandardCharsets.UTF_8);
        Regex customerName = new Regex(
                pattern,
                0,
                pattern.length,
                Option.IGNORECASE,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE);

        Matcher matcher = customerName.matcher(message);
        int matchLength = matcher.search(0, message.length, Option.DEFAULT);

        assertTrue(matchLength > 0);
        assertEquals("CAF\u00c9", matchedText(message, matcher));
        assertEquals(5, matcher.getEnd() - matcher.getBegin());
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
