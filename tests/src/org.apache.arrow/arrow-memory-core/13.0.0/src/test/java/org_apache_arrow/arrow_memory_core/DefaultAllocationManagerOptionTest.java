/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAllocationManagerOptionTest {
    private static final String ALLOCATION_MANAGER_TYPE_PROPERTY_NAME = "arrow.allocation.manager.type";

    @Test
    void rootAllocatorLoadsConfiguredNettyAllocationManagerFactory() {
        String previousType = System.getProperty(ALLOCATION_MANAGER_TYPE_PROPERTY_NAME);
        System.setProperty(ALLOCATION_MANAGER_TYPE_PROPERTY_NAME, "Netty");
        try {
            try (BufferAllocator allocator = new RootAllocator(64)) {
                assertThat(allocator.getRoot()).isSameAs(allocator);
                assertThat(allocator.getLimit()).isEqualTo(64L);
            }
        } finally {
            restoreProperty(previousType);
        }
    }

    private static void restoreProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(ALLOCATION_MANAGER_TYPE_PROPERTY_NAME);
        } else {
            System.setProperty(ALLOCATION_MANAGER_TYPE_PROPERTY_NAME, previousValue);
        }
    }
}
