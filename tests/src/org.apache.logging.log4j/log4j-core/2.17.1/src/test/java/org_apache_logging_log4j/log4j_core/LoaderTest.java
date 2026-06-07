/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import org.apache.logging.log4j.core.util.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class LoaderTest {
    private static final String CONTEXT_RESOURCE = "log4j-core-loader-context-resource.txt";
    private static final String DEFAULT_RESOURCE = "log4j-core-loader-default-resource.txt";
    private static final String MISSING_RESOURCE = "log4j-core-loader-missing-resource.txt";

    @Test
    @Timeout(20)
    void findsResourcesWithContextLoader() throws Exception {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new TestResourceClassLoader(CONTEXT_RESOURCE));

            final URL resource = Loader.getResource(CONTEXT_RESOURCE, null);

            assertThat(resource).isNotNull();
            assertThat(resource.toString()).endsWith(CONTEXT_RESOURCE);
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void findsResourcesWithDefaultLoaderAfterApplicationLoaderMiss() throws Exception {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            final ClassLoader defaultLoader = new TestResourceClassLoader(DEFAULT_RESOURCE);

            final URL resource = Loader.getResource(DEFAULT_RESOURCE, defaultLoader);

            assertThat(resource).isNotNull();
            assertThat(resource.toString()).endsWith(DEFAULT_RESOURCE);
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void fallsBackToSystemResourceLookupWhenOtherLoadersMiss() {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            final URL resource = Loader.getResource(MISSING_RESOURCE, null);

            assertThat(resource).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void opensResourceStreamsWithContextLoader() throws Exception {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new TestResourceClassLoader(CONTEXT_RESOURCE));

            try (InputStream stream = Loader.getResourceAsStream(CONTEXT_RESOURCE, null)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(CONTEXT_RESOURCE);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void opensResourceStreamsWithDefaultLoaderAfterApplicationLoaderMiss() throws Exception {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            final ClassLoader defaultLoader = new TestResourceClassLoader(DEFAULT_RESOURCE);

            try (InputStream stream = Loader.getResourceAsStream(DEFAULT_RESOURCE, defaultLoader)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(DEFAULT_RESOURCE);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void fallsBackToSystemResourceStreamLookupWhenOtherLoadersMiss() throws Exception {
        final ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            try (InputStream stream = Loader.getResourceAsStream(MISSING_RESOURCE, null)) {
                assertThat(stream).isNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Test
    @Timeout(20)
    void loadsClassesThroughLoaderUtilities() throws Exception {
        assertSame(String.class, Loader.initializeClass(String.class.getName(), null));
        assertSame(String.class, Loader.loadClass(String.class.getName(), LoaderTest.class.getClassLoader()));
        assertSame(String.class, Loader.loadSystemClass(String.class.getName()));
    }

    @Test
    @Timeout(20)
    void loadsLog4jClassAfterSystemLoaderMiss() throws Exception {
        final Class<?> loadedClass = Loader.loadSystemClass(Loader.class.getName());

        assertSame(Loader.class, loadedClass);
    }

    @Test
    @Timeout(20)
    @SuppressWarnings("removal")
    void fallsBackToClassForNameWhenSystemClassLoaderIsUnavailable() throws Exception {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean installed = installSecurityManager(new SystemClassLoaderDenyingSecurityManager());
        try {
            final Class<?> loadedClass = Loader.loadSystemClass(String.class.getName());

            assertSame(String.class, loadedClass);
        } finally {
            if (installed || previousSecurityManager != null) {
                restoreSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    @Timeout(20)
    void reportsMissingSystemClassAfterFallbackLookup() {
        assertThrows(ClassNotFoundException.class, () -> Loader.loadSystemClass("not.a.RealLog4jCoreLoaderClass"));
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManager(final SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private static void restoreSecurityManager(final SecurityManager previousSecurityManager) {
        try {
            System.setSecurityManager(previousSecurityManager);
        } catch (SecurityException | UnsupportedOperationException exception) {
            assertSame(previousSecurityManager, System.getSecurityManager());
        }
    }

    private static final class TestResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private TestResourceClassLoader(final String resourceName) throws MalformedURLException {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = new URL("file:/log4j-core-loader-test/" + resourceName);
        }

        @Override
        public URL getResource(final String name) {
            return resourceName.equals(name) ? resourceUrl : null;
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            if (!resourceName.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(resourceName.getBytes(StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("removal")
    private static final class SystemClassLoaderDenyingSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
            if (permission instanceof RuntimePermission && "getClassLoader".equals(permission.getName())) {
                throw new SecurityException("System class loader access denied for Loader fallback coverage");
            }
        }
    }
}
