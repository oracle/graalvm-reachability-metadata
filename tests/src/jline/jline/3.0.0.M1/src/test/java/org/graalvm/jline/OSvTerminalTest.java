/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.InputFlag;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.Attributes.OutputFlag;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @Test
    void externalTerminalAppliesConfiguredNewlineTranslation() throws Exception {
        try (PipedInputStream terminalInput = new PipedInputStream();
             PipedOutputStream inputWriter = new PipedOutputStream(terminalInput);
             ByteArrayOutputStream terminalOutput = new ByteArrayOutputStream();
             ExternalTerminal terminal = new ExternalTerminal(
                     "newline-terminal",
                     "ansi",
                     terminalInput,
                     terminalOutput,
                     StandardCharsets.UTF_8.name())) {
            Attributes attributes = terminal.getAttributes();
            attributes.setLocalFlag(LocalFlag.ECHO, true);
            attributes.setInputFlag(InputFlag.IGNCR, true);
            attributes.setOutputFlags(EnumSet.of(OutputFlag.OPOST));
            terminal.setAttributes(attributes);

            String text = "Testing input and output with newlines\r\nSecond line.";
            String expected = "Testing input and output with newlines\nSecond line.";

            inputWriter.write(text.getBytes(StandardCharsets.UTF_8));
            inputWriter.flush();

            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (terminalOutput.size() < expected.length() && Instant.now().isBefore(deadline)) {
                Thread.sleep(50L);
            }

            assertThat(terminalOutput.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
        }
    }
}
