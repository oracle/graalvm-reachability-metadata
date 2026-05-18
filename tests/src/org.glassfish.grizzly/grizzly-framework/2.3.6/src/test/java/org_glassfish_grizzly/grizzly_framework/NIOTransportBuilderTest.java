/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.nio.SelectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NIOTransportBuilderTest {
    @Test
    void tcpBuilderCreatesTransportWithDefaultNioConfiguration() {
        TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();

        TCPNIOTransport transport = builder.build();
        ThreadPoolConfig workerConfig = transport.getWorkerThreadPoolConfig();
        ThreadPoolConfig kernelConfig = transport.getKernelThreadPoolConfig();
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        assertThat(transport).isNotNull();
        assertThat(transport.getIOStrategy()).isSameAs(WorkerThreadIOStrategy.getInstance());
        assertThat(transport.getMemoryManager()).isSameAs(MemoryManager.DEFAULT_MEMORY_MANAGER);
        assertThat(transport.getAttributeBuilder()).isSameAs(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER);
        assertThat(transport.getSelectorHandler()).isSameAs(SelectorHandler.DEFAULT_SELECTOR_HANDLER);
        assertThat(transport.getSelectionKeyHandler()).isSameAs(SelectionKeyHandler.DEFAULT_SELECTION_KEY_HANDLER);
        assertThat(workerConfig).isNotNull();
        assertThat(workerConfig.getCorePoolSize()).isEqualTo(availableProcessors * 2);
        assertThat(workerConfig.getMaxPoolSize()).isEqualTo(availableProcessors * 2);
        assertThat(kernelConfig).isNotNull();
        assertThat(transport.getSelectorRunnersCount()).isEqualTo(availableProcessors + 1);
    }
}
