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

/** Exercises Joni's backreference matching subsystem to detect duplicate words. */
public class DuplicateWordDetectionTest {
    @Test
    @Timeout(60)
    public void identifiesAnAccidentalDuplicateInUserSubmittedText() {
        Regex duplicateWord = new Regex("([a-z]+) \\1");
        byte[] text = "Please review the the deployment plan".getBytes(StandardCharsets.UTF_8);

        Matcher matcher = duplicateWord.matcher(text);
        int matchLength = matcher.search(0, text.length, Option.DEFAULT);

        assertTrue(matchLength > 0);
        Region captures = matcher.getRegion();
        assertEquals("the the", matchedText(text, matcher.getBegin(), matcher.getEnd()));
        assertEquals("the", capturedText(text, captures, 1));
    }

    private static String matchedText(byte[] input, int begin, int end) {
        return new String(input, begin, end - begin, StandardCharsets.UTF_8);
    }

    private static String capturedText(byte[] input, Region captures, int group) {
        return matchedText(input, captures.getBeg(group), captures.getEnd(group));
    }
}
