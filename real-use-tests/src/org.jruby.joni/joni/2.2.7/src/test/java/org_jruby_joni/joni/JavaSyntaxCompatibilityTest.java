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
import org.joni.Region;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises Joni's configurable syntax dialect subsystem to parse a service endpoint with Java-style regular expressions. */
public class JavaSyntaxCompatibilityTest {
    @Test
    @Timeout(60)
    public void parsesEndpointUsingJavaRegularExpressionSyntax() {
        byte[] pattern = "(?<scheme>https?)://(?<host>[a-z0-9.-]+):(?<port>[0-9]{2,5})"
                .getBytes(StandardCharsets.UTF_8);
        byte[] endpoint = "https://api.example.test:8443/v1/orders".getBytes(StandardCharsets.UTF_8);
        Regex endpointPattern = new Regex(
                pattern,
                0,
                pattern.length,
                Option.DEFAULT,
                UTF8Encoding.INSTANCE,
                Syntax.Java,
                WarnCallback.NONE);

        Matcher matcher = endpointPattern.matcher(endpoint);
        int matchLength = matcher.search(0, endpoint.length, Option.DEFAULT);

        assertEquals(0, matchLength);
        Region captures = matcher.getRegion();
        assertEquals("https://api.example.test:8443", matchedText(endpoint, matcher));
        assertEquals("https", capturedText(endpoint, captures, 1));
        assertEquals("api.example.test", capturedText(endpoint, captures, 2));
        assertEquals("8443", capturedText(endpoint, captures, 3));
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }

    private static String capturedText(byte[] input, Region captures, int group) {
        return new String(
                input,
                captures.getBeg(group),
                captures.getEnd(group) - captures.getBeg(group),
                StandardCharsets.UTF_8);
    }
}
