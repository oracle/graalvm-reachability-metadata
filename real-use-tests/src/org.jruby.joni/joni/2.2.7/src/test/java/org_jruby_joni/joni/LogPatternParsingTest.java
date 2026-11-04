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

/// Exercises Joni's named-capture matching subsystem to parse structured audit log entries.
public class LogPatternParsingTest {
    @Test
    @Timeout(60)
    public void extractsAuditFieldsFromAnApplicationLogLine() {
        Regex auditEntry = new Regex(
                "(?<level>INFO|WARN) user=(?<user>[a-z]+) action=(?<action>[a-z_]+)");
        byte[] logLine = "2025-02-14T10:15:00Z WARN user=alice action=password_reset"
                .getBytes(StandardCharsets.UTF_8);

        Matcher matcher = auditEntry.matcher(logLine);
        int matchLength = matcher.search(0, logLine.length, Option.DEFAULT);

        assertTrue(matchLength > 0);
        Region captures = matcher.getRegion();
        assertEquals("WARN", capturedText(logLine, captures, 1));
        assertEquals("alice", capturedText(logLine, captures, 2));
        assertEquals("password_reset", capturedText(logLine, captures, 3));
        assertEquals("WARN user=alice action=password_reset", capturedText(logLine, captures, 0));
    }

    private static String capturedText(byte[] input, Region captures, int group) {
        return new String(
                input,
                captures.getBeg(group),
                captures.getEnd(group) - captures.getBeg(group),
                StandardCharsets.UTF_8);
    }
}
