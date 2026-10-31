/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NIOTransportBuilderTest {
    @Test
    void constructsATcpTransportWithTheRequestedConfiguration() {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setName("configured-tcp")
                .setIOStrategy(SameThreadIOStrategy.getInstance())
                .setReadBufferSize(4096)
                .setWriteBufferSize(8192)
                .setTcpNoDelay(true)
                .setKeepAlive(true)
                .build();

        assertThat(transport.getName()).isEqualTo("configured-tcp");
        assertThat(transport.getReadBufferSize()).isEqualTo(4096);
        assertThat(transport.getWriteBufferSize()).isEqualTo(8192);
        assertThat(transport.isTcpNoDelay()).isTrue();
        assertThat(transport.isKeepAlive()).isTrue();
        assertThat(transport.getIOStrategy()).isSameAs(SameThreadIOStrategy.getInstance());
        assertThat(transport.getMemoryManager()).isSameAs(MemoryManager.DEFAULT_MEMORY_MANAGER);
        assertThat(transport.isStopped()).isTrue();
    }

    @Test
    void constructsAUdpTransportWithTheRequestedConfiguration() {
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance()
                .setName("configured-udp")
                .setReadBufferSize(2048)
                .setWriteBufferSize(4096)
                .setReuseAddress(true)
                .build();

        assertThat(transport.getName()).isEqualTo("configured-udp");
        assertThat(transport.getReadBufferSize()).isEqualTo(2048);
        assertThat(transport.getWriteBufferSize()).isEqualTo(4096);
        assertThat(transport.isReuseAddress()).isTrue();
        assertThat(transport.getSelectorHandler()).isNotNull();
        assertThat(transport.getSelectionKeyHandler()).isNotNull();
        assertThat(transport.isStopped()).isTrue();
    }
}
