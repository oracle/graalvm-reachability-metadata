/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.bootstrap.classloading.MemoryClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;

public class QuarkusClassLoaderTest {

    private static final String TEST_CLASS_RESOURCE =
            QuarkusClassLoaderTest.class.getName().replace('.', '/') + ".class";

    @Test
    void delegatesJdkClassLoadingToParent() throws Exception {
        try (QuarkusClassLoader classLoader = newClassLoader("jdk-delegating-loader", false)) {
            final Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertSame(String.class, loadedClass);
        }
    }

    @Test
    void delegatesParentFirstClassLoadingToParent() throws Exception {
        try (QuarkusClassLoader classLoader = newClassLoader("parent-first-loader", true)) {
            final Class<?> loadedClass = classLoader.loadClass(QuarkusClassLoaderTest.class.getName());

            assertSame(QuarkusClassLoaderTest.class, loadedClass);
        }
    }

    @Test
    void delegatesParentLastClassLoadingToParentWhenLocalClassIsAbsent() throws Exception {
        try (QuarkusClassLoader classLoader = newClassLoader("parent-last-loader", false)) {
            final Class<?> loadedClass = classLoader.loadClass(QuarkusClassLoaderTest.class.getName());

            assertSame(QuarkusClassLoaderTest.class, loadedClass);
        }
    }

    @Test
    void delegatesSingleResourceLookupToParent() {
        try (QuarkusClassLoader classLoader = newClassLoader("single-resource-loader", false)) {
            final URL resource = classLoader.getResource(TEST_CLASS_RESOURCE);

            assertNotNull(resource);
        }
    }

    @Test
    void delegatesResourceStreamLookupToParent() throws Exception {
        try (QuarkusClassLoader classLoader = newClassLoader("stream-resource-loader", false);
                InputStream resourceStream = classLoader.getResourceAsStream(TEST_CLASS_RESOURCE)) {
            assertNotNull(resourceStream);
        }
    }

    @Test
    void delegatesResourceEnumerationLookupToParent() throws Exception {
        try (QuarkusClassLoader classLoader = newClassLoader("resource-enumeration-loader", false)) {
            final Enumeration<URL> resources = classLoader.getResources(TEST_CLASS_RESOURCE);

            assertTrue(resources.hasMoreElements());
        }
    }

    @Test
    void closesLoaderThatLoadedJdbcDriverClass() throws Exception {
        final String driverClassName =
                QuarkusClassLoaderTest.class.getPackageName() + ".QuarkusClassLoaderDriverForClose";
        final String driverResourceName = driverClassName.replace('.', '/') + ".class";
        final byte[] driverBytes = readResource(driverResourceName);

        try (QuarkusClassLoader classLoader = QuarkusClassLoader.builder(
                "driver-cleanup-loader", QuarkusClassLoaderTest.class.getClassLoader(), false)
                .addNormalPriorityElement(new MemoryClassPathElement(
                        Map.of(driverResourceName, driverBytes), true))
                .build()) {
            try {
                final Class<?> loadedDriver = classLoader.loadClass(driverClassName);

                assertSame(classLoader, loadedDriver.getClassLoader());
                assertTrue(Driver.class.isAssignableFrom(loadedDriver));
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        }
    }

    private static QuarkusClassLoader newClassLoader(String name, boolean parentFirst) {
        return QuarkusClassLoader.builder(name, QuarkusClassLoaderTest.class.getClassLoader(), parentFirst)
                .build();
    }

    private static byte[] readResource(String resourceName) throws Exception {
        try (InputStream inputStream = QuarkusClassLoaderTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            return requireNonNull(inputStream, resourceName).readAllBytes();
        }
    }
}

final class QuarkusClassLoaderDriverForClose implements Driver {
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return null;
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return false;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
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
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
}
