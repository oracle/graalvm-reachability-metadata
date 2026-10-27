/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class LineReaderImplTest {

    @Test
    public void editsCommandFileAndQueuesItsContents() throws Exception {
        Path commandFile = Files.createTempFile("jline-commands", ".txt");
        Files.writeString(commandFile, "first command\nsecond command\n", StandardCharsets.UTF_8);

        try (Terminal terminal = new DumbTerminal(
                "command-editor-terminal",
                Terminal.TYPE_DUMB,
                new ByteArrayInputStream(new byte[] {0x18}),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8)) {
            terminal.setSize(new Size(80, 24));
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

            reader.editAndAddInBuffer(commandFile.toFile());

            assertEquals("first command", reader.readLine());
            assertEquals("second command", reader.readLine());
        } finally {
            Files.deleteIfExists(commandFile);
        }
    }
}
