/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.AbstractPosixTerminal;
import org.jline.terminal.impl.jansi.JansiNativePty;
import org.jline.terminal.spi.Pty;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JansiNativePtyTest {

    @Test
    public void opensJansiBackedPseudoTerminal() throws Exception {
        Size requestedSize = new Size(80, 24);

        try (Terminal terminal = TerminalBuilder.builder()
                .name("jansi-terminal")
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .attributes(new Attributes())
                .size(requestedSize)
                .jna(false)
                .jansi(true)
                .exec(false)
                .paused(true)
                .build()) {
            assertTrue(terminal instanceof AbstractPosixTerminal);
            Pty pty = ((AbstractPosixTerminal) terminal).getPty();
            assertTrue(pty instanceof JansiNativePty);
            assertTrue(((JansiNativePty) pty).getMaster() > 0);
            assertEquals(requestedSize, pty.getSize());
        }
    }
}
