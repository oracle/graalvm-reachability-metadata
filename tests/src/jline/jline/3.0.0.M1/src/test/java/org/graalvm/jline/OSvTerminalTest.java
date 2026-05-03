/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.ControlChar;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @Test
    @Timeout(10)
    void terminalRaisesConfiguredSignalHandlersForControlCharacters() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Signal> handledSignals = new ArrayList<Signal>();

        try (ExternalTerminal terminal = new ExternalTerminal(
                "signal-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                output,
                StandardCharsets.UTF_8.name())) {
            Attributes attributes = terminal.getAttributes();
            attributes.setLocalFlag(LocalFlag.ISIG, true);
            attributes.setLocalFlag(LocalFlag.ECHO, true);
            attributes.setControlChar(ControlChar.VINTR, 3);
            terminal.setAttributes(attributes);
            terminal.handle(Signal.INT, handledSignals::add);

            terminal.processInputByte(3);
            terminal.raise(Signal.WINCH);
        }

        assertThat(handledSignals).containsExactly(Signal.INT);
        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("^C");
    }
}
