/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the matcher replacement subsystem to redact credentials from audit log lines.
 */
public class AuditLogRedactionTest {

    @Test
    @Timeout(60)
    void redactsCredentialsWhilePreservingAuditContext() {
        AuditLogRedactor redactor = new AuditLogRedactor();

        String sanitized = redactor.redact(
                "user=alice;token=tok_live_9f31;action=upload;password=correct-horse");

        assertEquals("user=alice;token=[REDACTED];action=upload;password=[REDACTED]", sanitized);
    }

    /** Application code that removes secrets before sending audit records downstream. */
    private static final class AuditLogRedactor {

        private final Pattern credential = Pattern.compile("(?<key>token|password)=[^;]+(;|$)");

        String redact(String auditLine) {
            Matcher matcher = credential.matcher(auditLine);
            return matcher.replaceAll("$1=[REDACTED]$2");
        }
    }
}
