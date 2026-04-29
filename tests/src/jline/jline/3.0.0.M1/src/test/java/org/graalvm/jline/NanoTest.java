/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Nano;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class NanoTest {

    @TempDir
    Path root;

    @Test
    void runDisplaysHelpLoadedFromClassResource() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] input = new byte[] {7, 24, 24};

        try (Terminal terminal = TerminalBuilder.builder()
                .name("nano-help-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(input), output)
                .build()) {
            Nano nano = new Nano(terminal, root);

            nano.run();
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name()))
                .contains("Reachability metadata nano help");
    }
}
