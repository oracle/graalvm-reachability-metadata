/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecTerminalProviderInnerReflectionRedirectPipeCreatorTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void systemStreamNameCreatesRedirectPipeUsingReflectionMode() throws Exception {
        String originalMode = System.getProperty(TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE);

        try {
            System.setProperty(
                    TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE,
                    TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION);

            TerminalProvider provider = TerminalProvider.load("exec");
            String streamName = provider.systemStreamName(SystemStream.Output);

            assertThat(provider.name()).isEqualTo("exec");
            assertThat(streamName).satisfiesAnyOf(
                    value -> assertThat(value).isNull(),
                    value -> assertThat(value).isNotBlank());
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
