/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonsIoIOUtilsTest {
    @Test
    void resolvesResourcesWithDefaultAndExplicitClassLoaders() throws Exception {
        assertThat(IOUtils.resourceToURL("/testcontainers.properties").toString())
            .endsWith("testcontainers.properties");
        assertThat(IOUtils.resourceToURL("testcontainers.properties", getClass().getClassLoader()).toString())
            .endsWith("testcontainers.properties");
    }
}
