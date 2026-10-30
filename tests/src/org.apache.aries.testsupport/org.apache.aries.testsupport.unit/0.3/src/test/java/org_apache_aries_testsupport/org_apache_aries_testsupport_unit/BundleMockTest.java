/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.apache.aries.mocks.BundleMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BundleMockTest {
    @Test
    void findsAnExistingClasspathResource() throws Exception {
        BundleMock bundle = new BundleMock("fixture.bundle", new MapDictionary<String, String>());
        try (URLClassLoader loader = (URLClassLoader) bundle.getClassLoader()) {
            URL resource = bundle.getResource("aries-fixtures/payload.txt");

            assertNotNull(resource);
            try (InputStream input = resource.openStream()) {
                assertEquals("Aries fixture payload\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void enumeratesExistingClasspathResources() throws Exception {
        BundleMock bundle = new BundleMock("fixture.bundle", new MapDictionary<String, String>());
        try (URLClassLoader loader = (URLClassLoader) bundle.getClassLoader()) {
            Enumeration<URL> resources = bundle.getResources("aries-fixtures/payload.txt");

            assertTrue(resources.hasMoreElements());
            int found = 0;
            while (resources.hasMoreElements()) {
                try (InputStream input = resources.nextElement().openStream()) {
                    assertEquals("Aries fixture payload\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                found++;
            }
            assertTrue(found > 0);
        }
    }

    @Test
    void resolvesAnAlreadyAvailableClassThroughTheParentLoader() throws Exception {
        BundleMock bundle = new BundleMock("fixture.bundle", new MapDictionary<String, String>());
        try (URLClassLoader loader = (URLClassLoader) bundle.getClassLoader()) {
            assertSame(String.class, bundle.loadClass("java.lang.String"));
        }
    }
}
