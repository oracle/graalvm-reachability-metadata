/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JGroupsFileBroadcastEndpointTest {
    @Test
    void reportsMissingJGroupsConfigurationThroughDefaultContextClassLoader() throws Exception {
        String missingResource = "missing-jgroups-configuration.xml";
        JGroupsFileBroadcastEndpoint endpoint = new JGroupsFileBroadcastEndpoint(
                null,
                missingResource,
                "metadata-test-channel");

        Throwable failure = catchThrowable(endpoint::createChannel);

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("couldn't find JGroups configuration " + missingResource);
    }

    @Test
    void opensBundledClasspathResourceThroughDefaultContextClassLoader() throws Exception {
        JGroupsFileBroadcastEndpoint endpoint = new JGroupsFileBroadcastEndpoint(
                null,
                "activemq-version.properties",
                "metadata-test-channel");

        Throwable failure = catchThrowable(endpoint::createChannel);

        assertThat(failure).isNotNull();
        assertThat(String.valueOf(failure.getMessage()))
                .doesNotContain("couldn't find JGroups configuration");
    }

    @Test
    void readsJGroupsConfigurationFromContextClassLoaderResource(@TempDir Path temporaryDirectory) throws Exception {
        String resourceName = "jgroups-test.xml";
        Files.writeString(temporaryDirectory.resolve(resourceName), "not a jgroups xml document", StandardCharsets.UTF_8);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL resourceUrl = temporaryDirectory.resolve(resourceName).toUri().toURL();

        try {
            Thread.currentThread().setContextClassLoader(new SingleResourceClassLoader(resourceName, resourceUrl));
            JGroupsFileBroadcastEndpoint endpoint = new JGroupsFileBroadcastEndpoint(
                    null,
                    resourceName,
                    "metadata-test-channel");

            Throwable failure = catchThrowable(endpoint::createChannel);

            assertThat(failure).isNotNull();
            assertThat(String.valueOf(failure.getMessage()))
                    .doesNotContain("couldn't find JGroups configuration");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private SingleResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        protected URL findResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return null;
        }
    }
}
