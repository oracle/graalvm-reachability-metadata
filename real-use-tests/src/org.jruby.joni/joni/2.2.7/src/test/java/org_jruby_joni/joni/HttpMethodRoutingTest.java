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

/** Exercises Joni's longest-match search subsystem for routing HTTP-like commands. */
public class HttpMethodRoutingTest {
    @Test
    @Timeout(60)
    public void selectsTheMostSpecificMethodBeforeRoutingARequest() {
        byte[] methodPattern = "(?:GET|GETTING|POST)".getBytes(StandardCharsets.UTF_8);
        byte[] request = "GETTING /orders/42".getBytes(StandardCharsets.UTF_8);
        Regex methodMatcher = new Regex(
                methodPattern,
                0,
                methodPattern.length,
                Option.FIND_LONGEST,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE);

        Matcher matcher = methodMatcher.matcher(request);
        int matchLength = matcher.search(0, request.length, Option.DEFAULT);

        assertEquals(0, matchLength);
        assertEquals(0, matcher.getBegin());
        assertEquals("GETTING", matchedText(request, matcher));
        assertEquals("/orders/42", new String(
                request,
                matcher.getEnd() + 1,
                request.length - matcher.getEnd() - 1,
                StandardCharsets.UTF_8));
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
