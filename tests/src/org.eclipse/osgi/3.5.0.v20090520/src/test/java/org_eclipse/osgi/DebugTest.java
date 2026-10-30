/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.osgi.framework.debug.Debug;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebugTest {
    public static class DiagnosticException extends Exception {
        private final Throwable detail;

        public DiagnosticException(String message, Throwable detail) {
            super(message);
            this.detail = detail;
        }

        public Throwable getDetail() {
            return detail;
        }
    }

    @Test
    public void printsNestedDiagnosticExceptions() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream previous = Debug.out;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            Debug.out = output;
            Debug.printStackTrace(new DiagnosticException("outer diagnostic", new IllegalArgumentException("inner diagnostic")));
        } finally {
            Debug.out = previous;
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("outer diagnostic"));
        assertTrue(text.contains("Nested Exception:"));
        assertTrue(text.contains("inner diagnostic"));
    }
}
