/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.exec.ExecTerminalProvider;
import org.junit.jupiter.api.Test;

import java.io.FileDescriptor;
import java.lang.ProcessBuilder.Redirect;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecTerminalProviderInnerReflectionRedirectPipeCreatorTest extends ExecTerminalProvider {

    @Test
    void createsRedirectPipeWithReflectionMode() {
        String originalMode = System.getProperty(TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE);
        try {
            System.setProperty(
                    TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE,
                    TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION);

            Redirect redirect = newDescriptor(FileDescriptor.out);

            assertThat(redirect).isNotNull();
            assertThat(redirect.type()).isEqualTo(Redirect.Type.PIPE);
        } finally {
            restoreProperty(TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE, originalMode);
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
