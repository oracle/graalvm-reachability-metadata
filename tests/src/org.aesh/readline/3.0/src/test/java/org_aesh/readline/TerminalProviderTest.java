/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.spi.TerminalProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalProviderTest {
    @Test
    void loadInstantiatesProviderDeclaredByServiceResource() throws Exception {
        TerminalProvider provider = TerminalProvider.load("exec");

        assertThat(provider.name()).isEqualTo("exec");
        assertThat(provider.getClass().getName()).isEqualTo("org.jline.terminal.impl.exec.ExecTerminalProvider");
    }
}
