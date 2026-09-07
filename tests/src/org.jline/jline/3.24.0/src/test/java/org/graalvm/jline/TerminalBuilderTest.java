/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerminalBuilderTest {

    @Test(timeout = 30000L)
    public void buildsForcedSystemFallbackAfterDeterminingTerminalColorSupport() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .name("fallback-terminal")
                .system(true)
                .provider(TerminalBuilder.PROP_PROVIDER_DUMB)
                .build()) {
            assertTrue(terminal instanceof DumbTerminal);
            assertEquals("fallback-terminal", terminal.getName());
            assertEquals(Terminal.TYPE_DUMB, terminal.getType());
        }
    }
}
