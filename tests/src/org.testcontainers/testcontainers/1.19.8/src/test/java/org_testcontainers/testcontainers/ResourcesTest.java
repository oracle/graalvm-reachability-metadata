/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {
    @Test
    void readsAnAbsoluteClasspathResource() throws Exception {
        URL resource = Resources.getResource("org_testcontainers/testcontainers/coverage-message.txt");
        assertThat(Resources.toString(resource, StandardCharsets.UTF_8)).isEqualTo("queue\nworker\nresult\n");
    }

    @Test
    void readsLinesFromAPackageRelativeResource() throws Exception {
        URL resource = Resources.getResource(ResourcesTest.class, "coverage-message.txt");
        assertThat(Resources.readLines(resource, StandardCharsets.UTF_8)).containsExactly("queue", "worker", "result");
    }
}
