/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.exec.ExecTerminalProvider;
import org.jline.terminal.spi.SystemStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExecTerminalProviderInnerReflectionRedirectPipeCreatorTest {

    @Test(timeout = 30000L)
    public void inspectsRedirectedOutputUsingReflectionBackedRedirect() {
        String property = TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE;
        String originalMode = System.getProperty(property);
        System.setProperty(property, TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION);

        try {
            ExecTerminalProvider provider = new ExecTerminalProvider();
            String streamName = provider.systemStreamName(SystemStream.Output);

            assertEquals(TerminalBuilder.PROP_PROVIDER_EXEC, provider.name());
            assertTrue(streamName == null || !streamName.isBlank());
        } finally {
            if (originalMode == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, originalMode);
            }
        }
    }
}
