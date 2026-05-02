/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.DefaultAllocationManagerOption;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckAllocatorTest {
    @Test
    void rootAllocatorDiscoversDefaultAllocationManagerFromClasspath() {
        String previousType = System.getProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME);
        System.setProperty(DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME, "Unknown");

        try (BufferAllocator allocator = new RootAllocator(64); ArrowBuf buffer = allocator.buffer(8)) {
            buffer.setByte(0, 42);

            assertThat(allocator.getName()).isEqualTo("ROOT");
            assertThat(allocator.getLimit()).isEqualTo(64);
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(8);
            assertThat(buffer.getByte(0)).isEqualTo((byte) 42);
            assertThat(allocator.getAllocatedMemory()).isGreaterThan(0);
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
