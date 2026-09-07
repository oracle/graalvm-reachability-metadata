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
import org.jline.terminal.Cursor;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BasicTerminalTests {

    @Test(timeout = 30000L)
    public void testNewlines() throws IOException, InterruptedException {
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream inputWriter = new PipedOutputStream(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ExternalTerminal terminal = new ExternalTerminal(
                        "foo", "ansi", input, output, StandardCharsets.UTF_8)) {
            Attributes attributes = terminal.getAttributes();
            attributes.setLocalFlag(LocalFlag.ECHO, true);
            attributes.setInputFlag(InputFlag.IGNCR, true);
            attributes.setOutputFlags(EnumSet.of(OutputFlag.OPOST));
            terminal.setAttributes(attributes);

            String text = "Testing input and output with newlines\r\nSecond line.";
            String expected = "Testing input and output with newlines\nSecond line.";

            inputWriter.write(text.getBytes(StandardCharsets.UTF_8));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            while (output.size() < expected.length() && System.nanoTime() < deadline) {
                Thread.sleep(100L);
            }

            assertEquals(expected, output.toString(StandardCharsets.UTF_8));
        }
    }

    @Test(timeout = 30000L)
    public void testCursor() throws IOException {
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream inputWriter = new PipedOutputStream(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ExternalTerminal terminal = new ExternalTerminal(
                        "foo", "ansi", input, output, StandardCharsets.UTF_8)) {
            inputWriter.write(new byte[] {'\033', '[', '2', ';', '3', 'R', 'f'});
            inputWriter.flush();

            Cursor cursor = terminal.getCursorPosition(discarded -> {
            });
            assertNotNull(cursor);
            assertEquals(2, cursor.getX());
            assertEquals(1, cursor.getY());
        }
    }
}
