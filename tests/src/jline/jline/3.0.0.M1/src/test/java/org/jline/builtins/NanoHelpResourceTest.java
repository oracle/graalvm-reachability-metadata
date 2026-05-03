/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jline.builtins;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class NanoHelpResourceTest {

    @Test
    @Timeout(10)
    void helpLoadsBundledEditorHelpResource() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = new ExternalTerminal(
                "nano-help-test",
                "ansi",
                new ByteArrayInputStream(new byte[] {0x18}),
                output,
                StandardCharsets.UTF_8.name())) {
            terminal.setSize(new Size(80, 24));
            Nano nano = new Nano(terminal, Files.createTempDirectory("jline-nano-help"));
            nano.size.copy(terminal.getSize());
            nano.display.resize(nano.size.getRows(), nano.size.getColumns());

            nano.help("/org/jline/editor/nano-main-help.txt");
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name()))
                .contains("Main nano help text")
                .contains("Control-key sequences");
    }
}
