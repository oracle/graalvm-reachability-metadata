/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceCoverageTest {
    @Test
    void resourceLooksUpClasspathAndSystemResources() throws Exception {
        Resource classPathResource = Resource.newClassPathResource("/org_eclipse_jetty/jetty_util/sample-resource.txt");
        assertThat(classPathResource).isNotNull();
        classPathResource.close();

        withContextClassLoader(getClass().getClassLoader(), () -> {
            Resource resource = Resource.newSystemResource("/org_eclipse_jetty/jetty_util/sample-resource.txt");
            assertThat(resource).isNotNull();
            resource.close();
        });

        withContextClassLoader(null, () ->
            assertThat(Resource.newSystemResource("/org_eclipse_jetty/jetty_util/missing-resource.txt")).isNull()
        );
    }

    private static void withContextClassLoader(ClassLoader classLoader, ThrowingRunnable action) throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
