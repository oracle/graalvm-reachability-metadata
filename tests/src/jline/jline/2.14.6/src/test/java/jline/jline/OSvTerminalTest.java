/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.jline;

import com.cloudius.util.Stty;
import jline.OSvTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OSvTerminalTest {

    private OSvTerminal terminal;

    @BeforeEach
    void setUp() {
        Stty.resetState();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.terminal != null) {
            this.terminal.restore();
        }
        this.terminal = null;
        Stty.resetState();
    }

    @Test
    void initAndRestoreInvokeSttyMethodsThroughPublicApi() throws Exception {
        this.terminal = new OSvTerminal();

        assertTrue(this.terminal.isAnsiSupported());
        assertEquals(1, Stty.constructorCalls);
        assertEquals(0, Stty.jlineModeCalls);
        assertEquals(0, Stty.resetCalls);

        this.terminal.init();

        assertEquals(1, Stty.jlineModeCalls);
        assertEquals(0, Stty.resetCalls);

        this.terminal.restore();
        this.terminal = null;

        assertEquals(1, Stty.jlineModeCalls);
        assertEquals(1, Stty.resetCalls);
    }
}
