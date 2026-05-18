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
        ThreadPoolConfig selectorConfig = builder.getSelectorThreadPoolConfig();
        int expectedSelectorRunnerCount = Runtime.getRuntime().availableProcessors();

        assertThat(transport).isNotNull();
        assertThat(builder.getIOStrategy()).isSameAs(WorkerThreadIOStrategy.getInstance());
        assertThat(builder.getMemoryManager()).isSameAs(MemoryManager.DEFAULT_MEMORY_MANAGER);
        assertThat(builder.getAttributeBuilder()).isSameAs(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER);
        assertThat(builder.getSelectorHandler()).isSameAs(SelectorHandler.DEFAULT_SELECTOR_HANDLER);
        assertThat(builder.getSelectionKeyHandler()).isSameAs(SelectionKeyHandler.DEFAULT_SELECTION_KEY_HANDLER);
        assertThat(builder.getWorkerThreadPoolConfig()).isNotNull();
        assertThat(selectorConfig.getCorePoolSize()).isEqualTo(expectedSelectorRunnerCount);
        assertThat(selectorConfig.getMaxPoolSize()).isEqualTo(expectedSelectorRunnerCount);
        assertThat(transport.getSelectorRunnersCount()).isEqualTo(expectedSelectorRunnerCount);
    }
}
