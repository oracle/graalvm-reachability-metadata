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

/** Exercises Joni's capture policy subsystem to validate redaction tokens without retaining groups. */
public class CapturePolicyValidationTest {
    @Test
    @Timeout(60)
    public void validatesASecretTokenWithoutCreatingCaptureGroups() {
        byte[] pattern = "(secret|token)-[0-9]+".getBytes(StandardCharsets.UTF_8);
        byte[] message = "Authorization secret-4821 accepted".getBytes(StandardCharsets.UTF_8);
        Regex tokenPattern = new Regex(
                pattern,
                0,
                pattern.length,
                Option.DONT_CAPTURE_GROUP,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE);

        Matcher matcher = tokenPattern.matcher(message);
        int matchStart = matcher.search(0, message.length, Option.DEFAULT);

        assertEquals(14, matchStart);
        assertEquals(0, tokenPattern.numberOfCaptures());
        assertEquals(14, matcher.getBegin());
        assertEquals(25, matcher.getEnd());
        assertEquals("secret-4821", matchedText(message, matcher));
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
