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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AbstractPtyInnerReflectionFileDescriptorCreatorTest {

    @Test(timeout = 30000L)
    public void createsFileDescriptorsForJansiPseudoTerminal() throws Exception {
        String property = TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE;
        String originalMode = System.getProperty(property);
        System.setProperty(property, TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION);
        Size requestedSize = new Size(82, 26);

        try (Terminal terminal = TerminalBuilder.builder()
                .provider(TerminalBuilder.PROP_PROVIDER_JANSI)
                .name("jansi-reflection-terminal")
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .attributes(new Attributes())
                .size(requestedSize)
                .paused(true)
                .build()) {
            assertTrue(terminal instanceof AbstractPosixTerminal);
            Pty pty = ((AbstractPosixTerminal) terminal).getPty();
            assertTrue(pty instanceof JansiNativePty);
            JansiNativePty nativePty = (JansiNativePty) pty;
            assertTrue(nativePty.getMaster() > 0);
            assertNotNull(nativePty.getMasterFD());
            assertNotNull(nativePty.getSlaveFD());
            assertEquals(requestedSize, pty.getSize());
        } finally {
            if (originalMode == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, originalMode);
            }
        }
    }
}
