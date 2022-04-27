/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BasicTerminalTests {

    @Test
    public void testNewlines() throws IOException, InterruptedException {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream outIn = new PipedOutputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExternalTerminal terminal = new ExternalTerminal("foo", "ansi", in, out, StandardCharsets.UTF_8);

        Attributes attributes = terminal.getAttributes();
        attributes.setLocalFlag(LocalFlag.ECHO, true);
        attributes.setInputFlag(InputFlag.IGNCR, true);
        attributes.setOutputFlags(EnumSet.of(OutputFlag.OPOST));
        terminal.setAttributes(attributes);

        String text = "Testing input and output with newlines\r\nSecond line.";
        String expected = "Testing input and output with newlines\nSecond line.";

        outIn.write(text.getBytes());
        while (out.size() < expected.length()) {
            Thread.sleep(100);
        }

        assertEquals(expected, out.toString());
    }

    @Test
    public void testCursor() throws IOException {
        PipedInputStream in = new PipedInputStream();
        final PipedOutputStream outIn = new PipedOutputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExternalTerminal terminal = new ExternalTerminal("foo", "ansi", in, out, StandardCharsets.UTF_8);

        outIn.write(new byte[]{'\033', '[', '2', ';', '3', 'R', 'f'});
        outIn.flush();

        Cursor cursor = terminal.getCursorPosition(c -> {
        });
        assertNotNull(cursor);
        assertEquals(2, cursor.getX());
        assertEquals(1, cursor.getY());
    }
}
