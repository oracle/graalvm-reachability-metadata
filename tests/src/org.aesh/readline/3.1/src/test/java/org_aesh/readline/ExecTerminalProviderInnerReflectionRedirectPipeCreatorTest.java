/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.impl.exec.ExecTerminalProvider;
import org.jline.terminal.spi.SystemStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ExecTerminalProviderInnerReflectionRedirectPipeCreatorTest {
    private static final String REDIRECT_PIPE_CREATION_MODE_PROPERTY =
            "org.jline.terminal.exec.redirectPipeCreationMode";
    private static final String REFLECTION_MODE = "reflection";

    @Test
    void systemStreamNameCreatesRedirectPipeUsingReflectionMode() {
        String originalMode = System.getProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY);

        try {
            System.setProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY, REFLECTION_MODE);

            ExecTerminalProvider provider = new ExecTerminalProvider();

            assertThat(provider.name()).isEqualTo("exec");
            assertThatCode(() -> provider.systemStreamName(SystemStream.Output)).doesNotThrowAnyException();
        } finally {
            restoreProperty(REDIRECT_PIPE_CREATION_MODE_PROPERTY, originalMode);
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
