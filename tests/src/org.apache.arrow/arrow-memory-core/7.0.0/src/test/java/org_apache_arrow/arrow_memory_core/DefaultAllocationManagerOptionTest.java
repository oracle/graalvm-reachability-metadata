/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.DefaultAllocationManagerOption;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAllocationManagerOptionTest {
    @Test
    void rootAllocatorUsesConfiguredNettyAllocationManager() {
        String previousType = System.getProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME);
        System.setProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME, "Netty");

        try (BufferAllocator allocator = new RootAllocator(1024)) {
            assertThat(allocator.getName()).isEqualTo("ROOT");
            assertThat(allocator.getLimit()).isEqualTo(1024);
            assertThat(allocator.getAllocatedMemory()).isZero();
        } finally {
            restoreAllocationManagerType(previousType);
        }
    }

    private static void restoreAllocationManagerType(String previousType) {
        if (previousType == null) {
            System.clearProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME);
        } else {
            System.setProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME, previousType);
        }
    }
}
