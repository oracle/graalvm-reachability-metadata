/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.impl.jna.JnaNativePty;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JnaNativePtyTest {

    @Test
    public void opensJnaBackedPseudoTerminal() throws Exception {
        Size requestedSize = new Size(100, 30);

        try (JnaNativePty pty = JnaNativePty.open(new Attributes(), requestedSize)) {
            assertTrue(pty.getMaster() > 0);
            assertTrue(pty.getSlave() > 0);
            assertNotNull(pty.getMasterFD());
            assertNotNull(pty.getSlaveFD());
            assertEquals(requestedSize, pty.getSize());
        }
    }
}
