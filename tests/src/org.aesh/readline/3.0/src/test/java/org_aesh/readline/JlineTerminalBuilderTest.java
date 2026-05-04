/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JlineTerminalBuilderTest {

    @Test
    void forcedDumbSystemTerminalDetectsDefaultDumbTerminalType() throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .name("jline fallback terminal")
                .provider(TerminalBuilder.PROP_PROVIDER_DUMB)
                .nativeSignals(false)
                .build();
        try {
            assertThat(terminal.getName()).isEqualTo("jline fallback terminal");
            assertThat(terminal.getType()).startsWith(Terminal.TYPE_DUMB);
        } finally {
            terminal.close();
        }
    }
}
