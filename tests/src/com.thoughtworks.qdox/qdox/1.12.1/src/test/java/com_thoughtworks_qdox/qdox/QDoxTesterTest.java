/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.thoughtworks.qdox.tools.QDoxTester;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class QDoxTesterTest {
    @Test
    void mainWithoutSourcesPrintsUsageWithTesterClassName() {
        PrintStream originalErr = System.err;
        UsageCapturingPrintStream capture = new UsageCapturingPrintStream();
        try {
            System.setErr(capture);

            Throwable thrown = catchThrowable(() -> QDoxTester.main(new String[0]));

            assertThat(thrown).isInstanceOf(UsageCapturedException.class);
        } finally {
            System.setErr(originalErr);
            capture.close();
        }

        assertThat(capture.lines())
                .contains("Tool that verifies that QDox can parse some Java source.")
                .contains("Usage: java com.thoughtworks.qdox.tools.QDoxTester src1 [src2] [src3]...");
    }

    private static final class UsageCapturingPrintStream extends PrintStream {
        private final List<String> lines = new ArrayList<>();

        private UsageCapturingPrintStream() {
            super(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        }

        @Override
        public void println(String line) {
            lines.add(line);
            if (line.startsWith("Usage: java ")) {
                throw new UsageCapturedException();
            }
        }

        private List<String> lines() {
            return Collections.unmodifiableList(lines);
        }
    }

    private static final class UsageCapturedException extends RuntimeException {
    }
}
