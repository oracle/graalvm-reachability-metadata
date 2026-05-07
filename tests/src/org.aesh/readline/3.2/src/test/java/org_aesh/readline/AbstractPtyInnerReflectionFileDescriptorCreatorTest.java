/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.jni.JniTerminalProvider;
import org.jline.terminal.spi.Pty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPtyInnerReflectionFileDescriptorCreatorTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void nativePtyOpenCreatesFileDescriptorsUsingReflectionMode() throws Exception {
        String originalMode = System.getProperty(TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE);
        Pty pty = null;

        try {
            System.setProperty(
                    TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE,
                    TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION);

            JniTerminalProvider provider = new JniTerminalProvider();
            Size requestedSize = new Size(80, 24);
            pty = provider.open(new Attributes(), requestedSize);

            assertThat(pty.getProvider()).isSameAs(provider);
            assertThat(pty.getSystemStream()).isNull();
            assertThat(pty.getSize().getColumns()).isEqualTo(requestedSize.getColumns());
            assertThat(pty.getSize().getRows()).isEqualTo(requestedSize.getRows());
        } finally {
            if (pty != null) {
                pty.close();
            }
            restoreProperty(TerminalBuilder.PROP_FILE_DESCRIPTOR_CREATION_MODE, originalMode);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
