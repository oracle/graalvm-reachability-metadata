/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HawtjniRuntimeLibraryTest {

    @Test
    void terminalSignalHandlersCanBeRegisteredAndRaised() throws Exception {
        DumbTerminal terminal = new DumbTerminal(
                "signal-handler-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name());

        try {
            List<Terminal.Signal> handledSignals = new ArrayList<Terminal.Signal>();

            Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT, handledSignals::add);
            terminal.raise(Terminal.Signal.INT);

            assertThat(previous).isSameAs(Terminal.SignalHandler.SIG_DFL);
            assertThat(handledSignals).containsExactly(Terminal.Signal.INT);
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }
}
