/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.ControlChar;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @Test
    void enterRawModeReturnsPreviousAttributesAndUpdatesTerminalAttributes() throws Exception {
        DumbTerminal terminal = new DumbTerminal(
                "raw-mode-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name());

        try {
            Attributes attributes = terminal.getAttributes();
            attributes.setLocalFlag(LocalFlag.ECHO, true);
            attributes.setLocalFlag(LocalFlag.ICANON, true);
            attributes.setControlChar(ControlChar.VMIN, 7);
            terminal.setAttributes(attributes);

            Attributes previous = terminal.enterRawMode();
            Attributes raw = terminal.getAttributes();

            assertThat(previous.getLocalFlag(LocalFlag.ECHO)).isTrue();
            assertThat(previous.getLocalFlag(LocalFlag.ICANON)).isTrue();
            assertThat(raw.getLocalFlag(LocalFlag.ECHO)).isFalse();
            assertThat(raw.getLocalFlag(LocalFlag.ICANON)).isFalse();
            assertThat(raw.getControlChar(ControlChar.VMIN)).isEqualTo(1);
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }
}
