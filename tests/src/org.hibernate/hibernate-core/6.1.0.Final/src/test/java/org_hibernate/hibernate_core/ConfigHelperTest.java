/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.ConfigHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigHelperTest {

    private static final String HIBERNATE_DTD_RESOURCE = "org/hibernate/hibernate-mapping-3.0.dtd";
    private static final String LEADING_SLASH_HIBERNATE_DTD_RESOURCE = "/" + HIBERNATE_DTD_RESOURCE;

    @Test
    public void findAsResourceUsesContextClassLoader() throws Exception {
        URL url = withContextClassLoader(
                ConfigHelperTest.class.getClassLoader(),
                () -> ConfigHelper.findAsResource(HIBERNATE_DTD_RESOURCE));

        assertThat(url).isNotNull();
        assertThat(url.toExternalForm()).contains(HIBERNATE_DTD_RESOURCE);
    }

    @Test
    public void findAsResourceFallsBackToHibernateClassLoader() throws Exception {
        URL url = withContextClassLoader(
                null,
                () -> ConfigHelper.findAsResource(HIBERNATE_DTD_RESOURCE));

        assertThat(url).isNotNull();
        assertThat(url.toExternalForm()).contains(HIBERNATE_DTD_RESOURCE);
    }

    @Test
    public void findAsResourceChecksSystemClassLoaderAfterOtherLoadersMiss() throws Exception {
        URL url = withContextClassLoader(
                null,
                () -> ConfigHelper.findAsResource("org/hibernate/missing-config-helper-resource.xml"));

        assertThat(url).isNull();
    }

    @Test
    public void getResourceAsStreamUsesContextClassLoader() throws Exception {
        try (InputStream stream = withContextClassLoader(
                ConfigHelperTest.class.getClassLoader(),
                () -> ConfigHelper.getResourceAsStream(HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getResourceAsStreamUsesEnvironmentClassResourceLookup() throws Exception {
        try (InputStream stream = withContextClassLoader(
                null,
                () -> ConfigHelper.getResourceAsStream(LEADING_SLASH_HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getResourceAsStreamUsesEnvironmentClassLoader() throws Exception {
        try (InputStream stream = withContextClassLoader(
                null,
                () -> ConfigHelper.getResourceAsStream(HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getUserResourceAsStreamUsesContextClassLoaderExactResourceName() throws Exception {
        try (InputStream stream = withContextClassLoader(
                ConfigHelperTest.class.getClassLoader(),
                () -> ConfigHelper.getUserResourceAsStream(HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getUserResourceAsStreamUsesContextClassLoaderStrippedResourceName() throws Exception {
        try (InputStream stream = withContextClassLoader(
                ConfigHelperTest.class.getClassLoader(),
                () -> ConfigHelper.getUserResourceAsStream(LEADING_SLASH_HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getUserResourceAsStreamUsesEnvironmentClassLoaderExactResourceName() throws Exception {
        try (InputStream stream = withContextClassLoader(
                null,
                () -> ConfigHelper.getUserResourceAsStream(HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    @Test
    public void getUserResourceAsStreamUsesEnvironmentClassLoaderStrippedResourceName() throws Exception {
        try (InputStream stream = withContextClassLoader(
                null,
                () -> ConfigHelper.getUserResourceAsStream(LEADING_SLASH_HIBERNATE_DTD_RESOURCE))) {
            assertReadable(stream);
        }
    }

    private static void assertReadable(InputStream stream) throws Exception {
        assertThat(stream).isNotNull();
        assertThat(stream.read()).isNotEqualTo(-1);
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            return supplier.get();
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

}
