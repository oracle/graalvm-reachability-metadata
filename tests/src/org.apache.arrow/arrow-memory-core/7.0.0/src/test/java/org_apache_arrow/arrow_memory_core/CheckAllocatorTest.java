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

public class CheckAllocatorTest {
    @Test
    void rootAllocatorScansClasspathWhenAllocationManagerTypeIsUnknown() {
        String propertyName = DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME;
        String previousType = System.getProperty(propertyName);
        System.setProperty(propertyName, DefaultAllocationManagerOption.AllocationManagerType.Unknown.name());
        try {
            try (BufferAllocator allocator = new RootAllocator(128)) {
                assertThat(allocator.getRoot()).isSameAs(allocator);
                assertThat(allocator.getLimit()).isEqualTo(128L);
            }
        } finally {
            restoreProperty(propertyName, previousType);
        }
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
