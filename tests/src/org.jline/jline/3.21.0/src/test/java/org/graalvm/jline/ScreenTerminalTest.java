/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.ScreenTerminal;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ScreenTerminalTest {

    @Test
    public void allocatesAndResizesScreenBuffers() {
        ScreenTerminal terminal = new ScreenTerminal(4, 2);

        assertTrue(terminal.write("ab"));
        assertTrue(terminal.setSize(6, 3));
        assertTrue(terminal.toString().startsWith("ab"));
    }
}
