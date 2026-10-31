/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.InputStream;
import java.util.Properties;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceTest {
    @Test
    void opensClasspathResources() throws Exception {
        try (Resource resource = Resource.newClassPathResource("/org/eclipse/jetty/version/build.properties")) {
            assertBuildProperties(resource);
        }
    }

    @Test
    void opensSystemResourcesWithAndWithoutLeadingSlash() throws Exception {
        try (Resource resource = Resource.newSystemResource("org/eclipse/jetty/version/build.properties")) {
            assertBuildProperties(resource);
        }
        try (Resource resource = Resource.newSystemResource("/org/eclipse/jetty/version/build.properties")) {
            assertBuildProperties(resource);
        }
    }

    @Test
    void usesTheLibraryLoaderWithoutAContextLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            try (Resource resource = Resource.newSystemResource("/org/eclipse/jetty/version/build.properties")) {
                assertBuildProperties(resource);
            }
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void returnsNullWhenNoLoaderCanFindTheResource() throws Exception {
        assertNull(Resource.newSystemResource("/org/eclipse/jetty/version/nonexistent-build-settings.properties"));
    }

    private static void assertBuildProperties(Resource resource) throws Exception {
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertFalse(resource.isDirectory());
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        }
        assertNotNull(properties.getProperty("version"));
        assertFalse(properties.getProperty("version").isBlank());
    }
}
