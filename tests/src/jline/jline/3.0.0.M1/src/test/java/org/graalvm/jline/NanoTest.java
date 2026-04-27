/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Nano;
import org.jline.terminal.Size;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class NanoTest {

    @Test
    void helpScreenLoadsItsClasspathResource() throws Exception {
        byte[] input = new byte[] {7, 24, 24};
        ByteArrayOutputStream terminalOutput = new ByteArrayOutputStream();

        try (ExternalTerminal terminal = new ExternalTerminal(
                "nano-test",
                "ansi",
                new ByteArrayInputStream(input),
                terminalOutput,
                StandardCharsets.UTF_8.name())) {
            terminal.setSize(new Size(24, 80));

            Nano nano = new Nano(terminal, Path.of("."));
            nano.run();
        }

        String output = terminalOutput.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Nano help loaded from the test classpath.");
        assertThat(output).contains("JLine Nano");
    }
}
