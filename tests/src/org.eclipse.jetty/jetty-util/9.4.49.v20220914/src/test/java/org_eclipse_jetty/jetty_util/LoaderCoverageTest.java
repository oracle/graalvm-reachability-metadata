/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.Locale;
import java.util.ResourceBundle;

import org.eclipse.jetty.util.Loader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderCoverageTest {
    @Test
    void loaderUsesContextAndSystemClassLoaders() throws Exception {
        withContextClassLoader(getClass().getClassLoader(), () -> {
            assertThat(Loader.getResource("org_eclipse_jetty/jetty_util/sample-resource.txt")).isNotNull();
            assertThat(Loader.loadClass("java.lang.String")).isEqualTo(String.class);

            ResourceBundle bundle = Loader.getResourceBundle("org_eclipse_jetty.jetty_util.loaderbundle", true, Locale.ROOT);
            assertThat(bundle.getString("greeting")).isEqualTo("hello");
        });

        assertThat(Loader.loadClass(LoaderCoverageTest.class, "java.lang.Integer")).isEqualTo(Integer.class);

        withContextClassLoader(null, () -> {
            assertThat(Loader.getResource("org_eclipse_jetty/jetty_util/sample-resource.txt")).isNotNull();
            assertThat(Loader.loadClass("java.lang.Long")).isEqualTo(Long.class);

            ResourceBundle bundle = Loader.getResourceBundle("org_eclipse_jetty.jetty_util.loaderbundle", true, Locale.ROOT);
            assertThat(bundle.getString("greeting")).isEqualTo("hello");
        });
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
