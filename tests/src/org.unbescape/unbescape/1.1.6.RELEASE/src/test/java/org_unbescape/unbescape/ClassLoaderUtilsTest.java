/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_unbescape.unbescape;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.unbescape.Unbescape;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilsTest {

    private static final String VERSION_PROPERTIES_RESOURCE = "org/unbescape/unbescape.properties";

    @Test
    void loadsVersionPropertiesWhenContextClassLoaderCannotFindThem() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingResourceClassLoader = new ClassLoader(null) {
        };

        Thread.currentThread().setContextClassLoader(missingResourceClassLoader);
        try {
            assertThat(Unbescape.VERSION).isNotBlank();
            assertThat(Unbescape.BUILD_TIMESTAMP).isNotBlank();
            assertThat(Unbescape.VERSION_MAJOR).isPositive();
            assertThat(Unbescape.VERSION_MINOR).isGreaterThanOrEqualTo(0);
            assertThat(Unbescape.VERSION_BUILD).isGreaterThanOrEqualTo(0);
            assertThat(Unbescape.VERSION_TYPE).isNotBlank();
            assertThat(Unbescape.isVersionStableRelease()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadsVersionPropertiesViaSystemClassLoaderWhenOtherLoadersMissThem() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
        };
        URL libraryLocation = Unbescape.class.getProtectionDomain().getCodeSource().getLocation();

        try (InputStream systemResourceStream = ClassLoader.getSystemResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
            assertThat(systemResourceStream).isNotNull();
        }

        try (URLClassLoader isolatedLibraryClassLoader = new URLClassLoader(new URL[]{libraryLocation}, null) {
            @Override
            public InputStream getResourceAsStream(final String name) {
                if (VERSION_PROPERTIES_RESOURCE.equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }
        }) {
            Thread.currentThread().setContextClassLoader(missingContextClassLoader);

            try (InputStream isolatedResourceStream = isolatedLibraryClassLoader.getResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
                assertThat(isolatedResourceStream).isNull();
            }

            Class<?> isolatedUnbescape;
            try {
                isolatedUnbescape = Class.forName("org.unbescape.Unbescape", true, isolatedLibraryClassLoader);
            } catch (ClassNotFoundException exception) {
                if (isNativeImageRuntime()) {
                    throw new TestAbortedException(
                            "Native image runtime does not support reloading application classes via isolated URLClassLoader",
                            exception
                    );
                }
                throw exception;
            }

            assertThat(isolatedUnbescape.getField("VERSION").get(null)).isInstanceOf(String.class).isNotNull();
            assertThat(isolatedUnbescape.getField("BUILD_TIMESTAMP").get(null)).isInstanceOf(String.class).isNotNull();
            assertThat((Integer) isolatedUnbescape.getField("VERSION_MAJOR").get(null)).isPositive();
            assertThat((Integer) isolatedUnbescape.getField("VERSION_MINOR").get(null)).isGreaterThanOrEqualTo(0);
            assertThat((Integer) isolatedUnbescape.getField("VERSION_BUILD").get(null)).isGreaterThanOrEqualTo(0);
            assertThat(isolatedUnbescape.getField("VERSION_TYPE").get(null)).isInstanceOf(String.class).isNotNull();
            assertThat(isolatedUnbescape.getMethod("isVersionStableRelease").invoke(null)).isEqualTo(true);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
