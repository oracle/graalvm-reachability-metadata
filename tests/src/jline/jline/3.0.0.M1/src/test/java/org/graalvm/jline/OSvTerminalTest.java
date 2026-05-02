/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import com.cloudius.util.Stty;
import jline.OSvTerminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @BeforeEach
    void setUp() {
        Stty.clear();
    }

    @Test
    void initAndRestoreInvokeTheLoadedSttyMethods() throws Exception {
        OSvTerminal terminal = new OSvTerminal();

        assertThat(terminal.sttyClass).isEqualTo(Stty.class);
        assertThat(terminal.stty).isInstanceOf(Stty.class);
        assertThat(terminal.isAnsiSupported()).isTrue();

        terminal.init();
        terminal.restore();

        assertThat(Stty.jlineModeCalls).isEqualTo(1);
        assertThat(Stty.resetCalls).isEqualTo(1);
    }
}
