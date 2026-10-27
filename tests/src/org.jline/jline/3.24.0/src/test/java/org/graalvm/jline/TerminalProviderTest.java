/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.jansi.JansiTerminalProvider;
import org.jline.terminal.spi.TerminalProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerminalProviderTest {

    @Test
    public void loadsJansiProviderFromProviderDescriptor() throws Exception {
        TerminalProvider provider = TerminalProvider.load(TerminalBuilder.PROP_PROVIDER_JANSI);

        assertTrue(provider instanceof JansiTerminalProvider);
        assertEquals(TerminalBuilder.PROP_PROVIDER_JANSI, provider.name());
        assertEquals("TerminalProvider[jansi]", provider.toString());
    }
}
