/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.ScreenTerminal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScreenTerminalTest {

    @Test
    public void allocatesWritesAndResizesScreenBuffers() {
        ScreenTerminal terminal = new ScreenTerminal(5, 2);

        assertTrue(terminal.write("hello"));
        assertTrue(terminal.setSize(8, 3));
        String[] rows = terminal.toString().split("\n");
        assertEquals(3, rows.length);
        assertEquals(8, rows[0].length());
        assertTrue(rows[0].startsWith("hello"));
    }
}
