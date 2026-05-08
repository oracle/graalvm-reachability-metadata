/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.bootstrap.classloading.MemoryClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;

public class QuarkusClassLoaderTest {

    @Test
    void delegatesMissingResourcesToParentClassLoader() throws Exception {
        ClassLoader parent = QuarkusClassLoaderTest.class.getClassLoader();
        String parentResource = resourceName(QuarkusClassLoaderTest.class);

        try (QuarkusClassLoader classLoader = QuarkusClassLoader.builder("resources", parent, false).build()) {
            Enumeration<URL> resources = classLoader.getResources(parentResource);
            assertThat(resources.hasMoreElements()).isTrue();

            assertThat(classLoader.getResource(parentResource)).isNotNull();

            try (InputStream stream = classLoader.getResourceAsStream(parentResource)) {
                assertThat(stream).isNotNull();
                assertThat(stream.read()).isNotEqualTo(-1);
            }
        }
    }

    @Test
    void delegatesJdkParentFirstAndMissingClassesToParentClassLoader() throws Exception {
        ClassLoader parent = QuarkusClassLoaderTest.class.getClassLoader();
        String parentFirstResource = resourceName(QuarkusClassLoaderTestParentFirstFixture.class);
        MemoryClassPathElement parentFirstElement = new MemoryClassPathElement(
                Map.of(parentFirstResource, new byte[] { 0 }), true);

        try (QuarkusClassLoader classLoader = QuarkusClassLoader.builder("parent-delegation", parent, false)
                .addParentFirstElement(parentFirstElement)
                .build()) {
            assertThat(classLoader.loadClass(String.class.getName())).isSameAs(String.class);
            assertThat(classLoader.loadClass(QuarkusClassLoaderTestParentOnlyFixture.class.getName()))
                    .isSameAs(QuarkusClassLoaderTestParentOnlyFixture.class);
            assertThat(classLoader.loadClass(QuarkusClassLoaderTestParentFirstFixture.class.getName()))
                    .isSameAs(QuarkusClassLoaderTestParentFirstFixture.class);
        }
    }

    @Test
    void loadsLocalClassAndCleansUpDriverClassesOnClose() throws Exception {
        try {
            ClassLoader parent = QuarkusClassLoaderTest.class.getClassLoader();
            Map<String, byte[]> resources = new HashMap<>();
            resources.put(resourceName(QuarkusClassLoaderTestChildFixture.class),
                    classBytes(QuarkusClassLoaderTestChildFixture.class));
            resources.put(resourceName(QuarkusClassLoaderTestDriver.class),
                    classBytes(QuarkusClassLoaderTestDriver.class));

            MemoryClassPathElement localElement = new MemoryClassPathElement(resources, true);
            QuarkusClassLoader classLoader = QuarkusClassLoader.builder("local-classes", parent, false)
                    .addNormalPriorityElement(localElement)
                    .build();
            try {
                Class<?> childClass = classLoader.loadClass(QuarkusClassLoaderTestChildFixture.class.getName());
                assertThat(childClass.getClassLoader()).isSameAs(classLoader);
                assertThat(classLoader.loadClass(QuarkusClassLoaderTestChildFixture.class.getName()))
                        .isSameAs(childClass);

                Class<?> driverClass = classLoader.loadClass(QuarkusClassLoaderTestDriver.class.getName());
                assertThat(driverClass.getClassLoader()).isSameAs(classLoader);
                assertThat(Driver.class).isAssignableFrom(driverClass);
            } finally {
                classLoader.close();
            }
            assertThat(classLoader.isClosed()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static byte[] classBytes(Class<?> fixtureClass) throws IOException {
        String resourceName = resourceName(fixtureClass);
        try (InputStream stream = fixtureClass.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(stream).as(resourceName).isNotNull();
            return stream.readAllBytes();
        }
    }

    private static String resourceName(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }
}

class QuarkusClassLoaderTestParentOnlyFixture {
}

class QuarkusClassLoaderTestParentFirstFixture {
}

class QuarkusClassLoaderTestChildFixture {
}

class QuarkusClassLoaderTestDriver implements Driver {

    @Override
    public boolean acceptsURL(String url) {
        return false;
    }

    @Override
    public Connection connect(String url, Properties info) {
        return null;
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }
}
