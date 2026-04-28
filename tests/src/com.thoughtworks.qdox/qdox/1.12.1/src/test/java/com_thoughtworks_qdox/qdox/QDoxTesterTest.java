/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import com.thoughtworks.qdox.tools.QDoxTester;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QDoxTesterTest {
    @Test
    void mainWithoutArgumentsPrintsUsageForToolClass() {
        PrintStream originalErr = System.err;
        UsageInterruptingPrintStream interceptingErr = new UsageInterruptingPrintStream();
        System.setErr(interceptingErr);
        try {
            UsageLinePrintedException exception = assertThrows(
                    UsageLinePrintedException.class,
                    () -> QDoxTester.main(new String[0]));

            assertThat(exception.getLine()).isEqualTo("Usage: java "
                    + QDoxTester.class.getName()
                    + " src1 [src2] [src3]...");
        } finally {
            System.setErr(originalErr);
            interceptingErr.close();
        }
    }

    private static final class UsageInterruptingPrintStream extends PrintStream {
        private UsageInterruptingPrintStream() {
            super(new ByteArrayOutputStream(), true);
        }

        @Override
        public void println(String value) {
            super.println(value);
            if (value != null && value.startsWith("Usage: java ")) {
                throw new UsageLinePrintedException(value);
            }
        }
    }

    private static final class UsageLinePrintedException extends RuntimeException {
        private final String line;

        private UsageLinePrintedException(String line) {
            this.line = line;
        }

        private String getLine() {
            return line;
        }
    }
}
