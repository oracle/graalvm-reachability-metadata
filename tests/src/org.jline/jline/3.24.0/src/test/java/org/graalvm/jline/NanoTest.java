/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Nano;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class NanoTest {

    @Test(timeout = 30000L)
    public void displaysBundledHelpInInteractiveEditor() throws Exception {
        Path root = Files.createTempDirectory("jline-nano-help");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] input = {0x07, 0x18, 0x18};

        try (Terminal terminal = new DumbTerminal(
                "nano-terminal",
                Terminal.TYPE_DUMB,
                new ByteArrayInputStream(input),
                output,
                StandardCharsets.UTF_8)) {
            terminal.setSize(new Size(80, 24));
            new Nano(terminal, root).run();
        } finally {
            Files.deleteIfExists(root);
        }

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Main nano help text"));
    }
}
