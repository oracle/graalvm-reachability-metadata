/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.memory.ByteBufferManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryManagerInitializerTest {
    private static final String DEFAULT_MEMORY_MANAGER_PROPERTY =
            "org.glassfish.grizzly.DEFAULT_MEMORY_MANAGER";

    @Test
    void defaultMemoryManagerUsesConfiguredPublicManagerClass() {
        String configuredManagerClass = ByteBufferManager.class.getName();

        assertThat(System.getProperty(DEFAULT_MEMORY_MANAGER_PROPERTY)).isEqualTo(configuredManagerClass);
        assertThat(MemoryManager.DEFAULT_MEMORY_MANAGER).isExactlyInstanceOf(ByteBufferManager.class);
    }
}
