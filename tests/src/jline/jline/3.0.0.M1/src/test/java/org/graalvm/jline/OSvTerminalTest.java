/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @Test
    @Timeout(10)
    void terminalAttributesRoundTripInputOutputAndLocalFlags() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("attributes-terminal-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            Attributes attributes = terminal.getAttributes();
            attributes.setInputFlags(EnumSet.of(Attributes.InputFlag.IGNCR));
            attributes.setOutputFlags(EnumSet.of(Attributes.OutputFlag.OPOST));
            attributes.setLocalFlag(Attributes.LocalFlag.ECHO, true);

            terminal.setAttributes(attributes);
            Attributes updatedAttributes = terminal.getAttributes();

            assertThat(updatedAttributes.getInputFlag(Attributes.InputFlag.IGNCR)).isTrue();
            assertThat(updatedAttributes.getOutputFlag(Attributes.OutputFlag.OPOST)).isTrue();
            assertThat(updatedAttributes.getLocalFlag(Attributes.LocalFlag.ECHO)).isTrue();
            assertThat(terminal.echo(false)).isTrue();
            assertThat(terminal.echo()).isFalse();
        }
    }
}
