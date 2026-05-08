/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_io;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.common.io.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesTest {
    private static final String MISSING_RESOURCE = "com_diffplug_durian/durian_io/missing-resource-73b59e57.txt";

    @Test
    void getResourceByNameUsesContextClassLoader() {
        assertThatThrownBy(() -> Resources.getResource(MISSING_RESOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource " + MISSING_RESOURCE + " not found");
    }

    @Test
    void getResourceRelativeToClassUsesClassResourceLookup() {
        final String relativeResource = "missing-resource-73b59e57.txt";
        final String expectedMessage = "resource " + relativeResource
                + " relative to " + ResourcesTest.class.getName() + " not found";

        assertThatThrownBy(() -> Resources.getResource(ResourcesTest.class, relativeResource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }
}
