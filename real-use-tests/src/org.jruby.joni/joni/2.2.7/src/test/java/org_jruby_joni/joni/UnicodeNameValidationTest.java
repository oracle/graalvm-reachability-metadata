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
import org.joni.Region;
import org.joni.Regex;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises Joni's Unicode character-property subsystem to parse internationalized profile names. */
public class UnicodeNameValidationTest {
    @Test
    @Timeout(60)
    public void extractsANameWithCombiningMarksFromAProfileMessage() {
        byte[] pattern = "name=([\\p{L}\\p{M}]+)".getBytes(StandardCharsets.UTF_8);
        byte[] message = "Profile imported: name=Zo\u0308e, locale=de-DE".getBytes(StandardCharsets.UTF_8);
        Regex profileName = new Regex(
                pattern,
                0,
                pattern.length,
                Option.DEFAULT,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE);

        Matcher matcher = profileName.matcher(message);
        int matchLength = matcher.search(0, message.length, Option.DEFAULT);

        assertTrue(matchLength > 0);
        Region captures = matcher.getRegion();
        assertEquals("name=Zo\u0308e", matchedText(message, matcher));
        assertEquals("Zo\u0308e", capturedText(message, captures, 1));
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
