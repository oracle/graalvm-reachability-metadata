/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPtyInnerReflectionFileDescriptorCreatorTest {
    private static final String FILE_DESCRIPTOR_CREATION_MODE_PROPERTY =
            "org.jline.terminal.pty.fileDescriptorCreationMode";
    private static final String REFLECTION_MODE = "reflection";

    @Test
    void jniPtyTerminalCreatesFileDescriptorsUsingReflectionMode() throws Exception {
        String originalMode = System.getProperty(FILE_DESCRIPTOR_CREATION_MODE_PROPERTY);
        Terminal terminal = null;

        try {
            System.setProperty(FILE_DESCRIPTOR_CREATION_MODE_PROPERTY, REFLECTION_MODE);

            terminal = TerminalBuilder.builder()
                    .name("reflection descriptor pty")
                    .type("xterm")
                    .provider("jni")
                    .system(false)
                    .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                    .nativeSignals(false)
                    .paused(true)
                    .attributes(new Attributes())
                    .size(new Size(80, 24))
                    .build();

            assertThat(terminal.getName()).isEqualTo("reflection descriptor pty");
        } finally {
            if (terminal != null) {
                terminal.close();
            }
            restoreProperty(FILE_DESCRIPTOR_CREATION_MODE_PROPERTY, originalMode);
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
