/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mortbay.util.Loader;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderTest {
    private static final String RESOURCE_NAME = "org_mortbay_jetty/jetty_util/loader-resource.txt";
    private static final String MISSING_RESOURCE_NAME = "org_mortbay_jetty/jetty_util/missing-loader-resource.txt";
    private static final String BUNDLE_NAME = "org_mortbay_jetty.jetty_util.loader_messages";
    private static final String TARGET_CLASS_NAME = "java.lang.String";

    @Test
    void locatesResourceWithContextClassLoader() throws Exception {
        withContextClassLoader(LoaderTest.class.getClassLoader(), () -> {
            URL resource = Loader.getResource(null, RESOURCE_NAME, false);

            assertThat(resource).isNotNull();
        });
    }

    @Test
    void locatesResourceWithLoadClassClassLoader() throws Exception {
        withContextClassLoader(null, () -> {
            URL resource = Loader.getResource(LoaderTest.class, RESOURCE_NAME, false);

            assertThat(resource).isNotNull();
        });
    }

    @Test
    void checksSystemResourcesWhenOtherLoadersAreUnavailable() throws Exception {
        withContextClassLoader(null, () -> {
            URL resource = Loader.getResource(null, MISSING_RESOURCE_NAME, false);

            assertThat(resource).isNull();
        });
    }

    @Test
    void loadsClassWithContextClassLoader() throws Exception {
        withContextClassLoader(LoaderTest.class.getClassLoader(), () -> {
            try {
                Class<?> loadedClass = Loader.loadClass(null, TARGET_CLASS_NAME, false);

                assertThat(loadedClass).isSameAs(String.class);
            } catch (Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
        });
    }

    @Test
    void loadsClassWithLoadClassClassLoader() throws Exception {
        withContextClassLoader(null, () -> {
            try {
                Class<?> loadedClass = Loader.loadClass(LoaderTest.class, TARGET_CLASS_NAME, false);

                assertThat(loadedClass).isSameAs(String.class);
            } catch (Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
        });
    }

    @Test
    void loadsClassWithDefaultClassLookup() throws Exception {
        withContextClassLoader(null, () -> {
            try {
                Class<?> loadedClass = Loader.loadClass(null, TARGET_CLASS_NAME, false);

                assertThat(loadedClass).isSameAs(String.class);
            } catch (Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
        });
    }

    @Test
    void loadsResourceBundleWithContextClassLoader() throws Exception {
        withContextClassLoader(LoaderTest.class.getClassLoader(), () -> {
            ResourceBundle bundle = Loader.getResourceBundle(null, BUNDLE_NAME, false, Locale.ROOT);

            assertThat(bundle.getString("message")).isEqualTo("loaded by Loader");
        });
    }

    @Test
    void loadsResourceBundleWithLoadClassClassLoader() throws Exception {
        withContextClassLoader(null, () -> {
            ResourceBundle bundle = Loader.getResourceBundle(LoaderTest.class, BUNDLE_NAME, false, Locale.ROOT);

            assertThat(bundle.getString("message")).isEqualTo("loaded by Loader");
        });
    }

    @Test
    void loadsResourceBundleWithDefaultLookup() throws Exception {
        withContextClassLoader(null, () -> {
            ResourceBundle bundle = Loader.getResourceBundle(null, BUNDLE_NAME, false, Locale.ROOT);

            assertThat(bundle.getString("message")).isEqualTo("loaded by Loader");
        });
    }

    private static void withContextClassLoader(ClassLoader classLoader, ThrowingRunnable runnable) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            runnable.run();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
