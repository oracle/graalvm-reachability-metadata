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
import org.jline.terminal.Size;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalFactoryTest {

    @Test
    @Timeout(10)
    void externalTerminalProcessesInputLineDisciplineAndSizeChanges() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ExternalTerminal terminal = new ExternalTerminal(
                "external-terminal-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                output,
                StandardCharsets.UTF_8.name())) {
            Attributes attributes = terminal.getAttributes();
            attributes.setLocalFlag(LocalFlag.ECHO, true);
            attributes.setInputFlag(InputFlag.ICRNL, true);
            terminal.setAttributes(attributes);
            terminal.setSize(new Size(100, 40));

            terminal.processInputByte('A');
            terminal.processInputByte('\r');

            assertThat(terminal.getName()).isEqualTo("external-terminal-test");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(terminal.getWidth()).isEqualTo(100);
            assertThat(terminal.getHeight()).isEqualTo(40);
            assertThat(terminal.input().read()).isEqualTo((int) 'A');
            assertThat(terminal.input().read()).isEqualTo((int) '\n');
            assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("A");
        }
    }

    @Test
    @Timeout(10)
    void echoModeCanBeToggledThroughTerminalAttributes() throws Exception {
        try (ExternalTerminal terminal = new ExternalTerminal(
                "echo-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name())) {
            boolean originalEcho = terminal.echo();

            boolean previousEcho = terminal.echo(!originalEcho);

            assertThat(previousEcho).isEqualTo(originalEcho);
            assertThat(terminal.echo()).isEqualTo(!originalEcho);
            terminal.echo(originalEcho);
            assertThat(terminal.echo()).isEqualTo(originalEcho);
        }
    }
}
