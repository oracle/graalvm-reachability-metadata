/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.internal.JDK6Console;
import java.io.PrintWriter;
import java.io.Writer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDK6ConsoleTest {
    @Test
    void delegatesPrintingAndPasswordReadingToConsoleMethods() throws Exception {
        RecordingConsole recordingConsole = new RecordingConsole("visible input", "hidden input".toCharArray());
        JDK6Console console = new JDK6Console(recordingConsole);

        console.print("prompt");
        console.println(" next");

        assertThat(recordingConsole.contents()).isEqualTo("prompt next" + System.lineSeparator());
        assertThat(String.valueOf(console.readPassword(true))).isEqualTo("visible input");
        assertThat(String.valueOf(console.readPassword(false))).isEqualTo("hidden input");
        assertThat(recordingConsole.readLineCalls()).isEqualTo(1);
        assertThat(recordingConsole.readPasswordCalls()).isEqualTo(1);
        assertThat(recordingConsole.flushCalls()).isEqualTo(2);
    }

    public static final class RecordingConsole {
        private final CountingWriter writer = new CountingWriter();
        private final PrintWriter printWriter = new PrintWriter(writer);
        private final String line;
        private final char[] password;
        private int readLineCalls;
        private int readPasswordCalls;

        RecordingConsole(String line, char[] password) {
            this.line = line;
            this.password = password.clone();
        }

        public PrintWriter writer() {
            return printWriter;
        }

        public String readLine() {
            readLineCalls++;
            return line;
        }

        public char[] readPassword() {
            readPasswordCalls++;
            return password.clone();
        }

        String contents() {
            return writer.contents();
        }

        int flushCalls() {
            return writer.flushCalls();
        }

        int readLineCalls() {
            return readLineCalls;
        }

        int readPasswordCalls() {
            return readPasswordCalls;
        }
    }

    private static final class CountingWriter extends Writer {
        private final StringBuilder output = new StringBuilder();
        private int flushCalls;

        @Override
        public void write(char[] buffer, int offset, int length) {
            output.append(buffer, offset, length);
        }

        @Override
        public void flush() {
            flushCalls++;
        }

        @Override
        public void close() {
            // The in-memory writer has no external resource to close.
        }

        String contents() {
            return output.toString();
        }

        int flushCalls() {
            return flushCalls;
        }
    }
}
