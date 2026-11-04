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

/// Exercises Joni's backreference matching subsystem to validate a repeated token in an import record.
public class RepeatedTokenBackreferenceTest {
    @Test
    @Timeout(60)
    public void acceptsOnlyRecordsWithTheSameTokenOnBothSides() {
        Regex repeatedToken = new Regex("\\A([A-Z]{2,6})-\\1\\z");
        byte[] matchingRecord = "ABCD-ABCD".getBytes(StandardCharsets.UTF_8);
        byte[] mismatchedRecord = "ABCD-ABCE".getBytes(StandardCharsets.UTF_8);

        Matcher matching = repeatedToken.matcher(matchingRecord);
        assertEquals(
                matchingRecord.length,
                matching.match(0, matchingRecord.length, Option.DEFAULT));
        assertEquals("ABCD-ABCD", matchingText(matchingRecord, matching));

        Matcher mismatched = repeatedToken.matcher(mismatchedRecord);
        assertEquals(
                Matcher.FAILED,
                mismatched.match(0, mismatchedRecord.length, Option.DEFAULT));
    }

    private static String matchingText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
