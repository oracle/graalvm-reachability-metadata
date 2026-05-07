/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.internal.JDK6Console;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDK6ConsoleTest {
    @Test
    void delegatesToConsoleWriterAndReadsWithEcho() throws Exception {
        RecordingConsole console = new RecordingConsole("typed value", "secret".toCharArray());
        JDK6Console jdk6Console = new JDK6Console(console);

        jdk6Console.print("prompt: ");
        char[] password = jdk6Console.readPassword(true);
        jdk6Console.println("done");

        assertThat(password).containsExactly('t', 'y', 'p', 'e', 'd', ' ', 'v', 'a', 'l', 'u', 'e');
        assertThat(console.output()).isEqualTo("prompt: done" + System.lineSeparator());
        assertThat(console.readLineCalls).isEqualTo(1);
        assertThat(console.readPasswordCalls).isZero();
    }

    @Test
    void delegatesToConsoleReadPasswordWhenEchoIsDisabled() throws Exception {
        RecordingConsole console = new RecordingConsole("ignored", "secret".toCharArray());
        JDK6Console jdk6Console = new JDK6Console(console);

        char[] password = jdk6Console.readPassword(false);

        assertThat(password).containsExactly('s', 'e', 'c', 'r', 'e', 't');
        assertThat(console.readLineCalls).isZero();
        assertThat(console.readPasswordCalls).isEqualTo(1);
    }

    public static class RecordingConsole {
        private final String line;
        private final char[] password;
        private final StringWriter output = new StringWriter();
        private final PrintWriter writer = new PrintWriter(output);
        private int readLineCalls;
        private int readPasswordCalls;

        RecordingConsole(String line, char[] password) {
            this.line = line;
            this.password = password;
        }

        public PrintWriter writer() {
            return writer;
        }

        public String readLine() {
            readLineCalls++;
            return line;
        }

        public char[] readPassword() {
            readPasswordCalls++;
            return password;
        }

        String output() {
            writer.flush();
            return output.toString();
        }
    }
}
