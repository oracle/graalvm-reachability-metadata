/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalProviderTest {

    @Test
    void loadsProviderDeclaredInServiceResource() throws Exception {
        TerminalProvider provider = TerminalProvider.load(TerminalBuilder.PROP_PROVIDER_DUMB);

        assertThat(provider.name()).isEqualTo(TerminalBuilder.PROP_PROVIDER_DUMB);
        assertThat(provider.isSystemStream(SystemStream.Output)).isFalse();
        assertThat(provider.systemStreamName(SystemStream.Output)).isNull();
    }
}
