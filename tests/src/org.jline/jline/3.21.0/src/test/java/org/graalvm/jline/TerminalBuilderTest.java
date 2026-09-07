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

    @Test
    public void buildsSystemDumbTerminalAfterInspectingParentProcess() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .name("parent-process-terminal")
                .system(true)
                .jna(false)
                .jansi(true)
                .exec(false)
                .dumb(true)
                .build()) {
            assertTrue(terminal instanceof DumbTerminal);
            assertEquals("parent-process-terminal", terminal.getName());
        }
    }
}
