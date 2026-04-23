/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import com.sun.jna.Platform;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.impl.jna.JnaNativePty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JnaNativePtyTest {

    @Test
    void openCreatesNativeFileDescriptors() throws Exception {
        if (!Platform.isLinux() && !Platform.isMac() && !Platform.isFreeBSD() && !Platform.isSolaris()) {
            assertThatThrownBy(() -> JnaNativePty.open(null, null))
                    .isInstanceOf(UnsupportedOperationException.class);
            return;
        }

        Attributes attributes = new Attributes();
        Size size = new Size(80, 24);
        try (JnaNativePty pty = JnaNativePty.open(attributes, size)) {
            assertThat(pty.getMaster()).isGreaterThan(0);
            assertThat(pty.getSlave()).isGreaterThan(0);
            assertThat(pty.getMasterFD()).isNotNull();
            assertThat(pty.getSlaveFD()).isNotNull();
            assertThat(pty.getName()).isNotEmpty();
            assertThat(pty.getAttr()).isNotNull();
            assertThat(pty.getSize()).isNotNull();
            assertThat(pty.toString()).contains(pty.getName());
        }
    }
}
