/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.util.ClassLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilsTest {

    private static final String PROPERTIES_RESOURCE = "org/thymeleaf/thymeleaf.properties";
    private static final String TEMPLATE_ENGINE_CLASS_NAME = "org.thymeleaf.TemplateEngine";

    @Test
    void loadClassUsesContextClassLoaderWhenItCanResolveTheClass() throws Exception {
        ClassLoader delegatingContextClassLoader = new ClassLoader(ClassLoaderUtilsTest.class.getClassLoader()) {
        };

        Class<?> loadedClass = withContextClassLoader(delegatingContextClassLoader,
                () -> ClassLoaderUtils.loadClass(TEMPLATE_ENGINE_CLASS_NAME));

        assertThat(loadedClass).isSameAs(TemplateEngine.class);
    }

    @Test
    void loadClassFallsBackFromContextClassLoaderToLibraryClassLoader() throws Exception {
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
        };

        Class<?> loadedClass = withContextClassLoader(missingContextClassLoader,
                () -> ClassLoaderUtils.loadClass(TEMPLATE_ENGINE_CLASS_NAME));

        assertThat(loadedClass).isSameAs(TemplateEngine.class);
    }

    @Test
    void findResourceFallsBackFromContextClassLoaderToLibraryClassLoader() throws Exception {
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
        };

        assertThat(withContextClassLoader(missingContextClassLoader,
                () -> ClassLoaderUtils.findResource(PROPERTIES_RESOURCE)))
                .isNotNull();
    }

    @Test
    void findResourceAsStreamFallsBackFromContextClassLoaderToLibraryClassLoader() throws Exception {
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
        };

        try (InputStream inputStream = withContextClassLoader(missingContextClassLoader,
                () -> ClassLoaderUtils.findResourceAsStream(PROPERTIES_RESOURCE))) {
            assertThat(inputStream).isNotNull();
            String properties = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(properties).contains("version=");
            assertThat(properties).contains("build.date=");
        }
    }

    private static <T> T withContextClassLoader(
            ClassLoader contextClassLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            return supplier.get();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
