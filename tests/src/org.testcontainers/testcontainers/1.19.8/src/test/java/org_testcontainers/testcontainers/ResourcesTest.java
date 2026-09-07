/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {
    @Test
    void resolvesResourcesFromAClassLoaderAndAClass() throws Exception {
        assertThat(Resources.toString(Resources.getResource("testcontainers.properties"), StandardCharsets.UTF_8))
            .contains("ryuk.container.image");
        assertThat(Resources.getResource(ResourcesTest.class, "/testcontainers.properties")).isNotNull();
    }
}
