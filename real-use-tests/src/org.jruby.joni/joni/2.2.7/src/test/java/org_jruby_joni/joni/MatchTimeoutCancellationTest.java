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
import org.joni.exception.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises Joni's matching timeout subsystem to bound a user-supplied pattern search. */
public class MatchTimeoutCancellationTest {
    @Test
    @Timeout(60)
    public void stopsAPathologicalUserPatternWhenItsBudgetExpires() {
        Regex userPattern = new Regex("(?:a+)+$");
        byte[] input = ("a".repeat(1000) + "b").getBytes(StandardCharsets.UTF_8);
        Matcher matcher = userPattern.matcher(input);
        matcher.setTimeout(0);

        assertThrows(
                TimeoutException.class,
                () -> matcher.searchInterruptible(0, input.length, Option.DEFAULT));
    }
}
