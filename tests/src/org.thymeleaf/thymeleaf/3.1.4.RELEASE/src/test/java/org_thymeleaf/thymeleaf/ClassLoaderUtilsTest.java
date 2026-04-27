/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.InputStream;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.util.ClassLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilsTest {

    private static final String VERSION_PROPERTIES_RESOURCE = "org/thymeleaf/thymeleaf.properties";

    @Test
    void loadClassUsesContextClassLoaderWhenItCanResolveTheType() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = new ClassLoader(ClassLoaderUtilsTest.class.getClassLoader()) {
        };

        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            Class<?> loadedClass = ClassLoaderUtils.loadClass(ContextLoadedType.class.getName());
            assertThat(loadedClass).isEqualTo(ContextLoadedType.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToLibraryClassLoaderWhenContextClassLoaderMissesTheType() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(final String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };

        Thread.currentThread().setContextClassLoader(missingContextClassLoader);
        try {
            Class<?> loadedClass = ClassLoaderUtils.loadClass(TemplateEngine.class.getName());
            assertThat(loadedClass).isEqualTo(TemplateEngine.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void findResourceUsesContextClassLoaderWhenItCanResolveTheResource() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = new ClassLoader(ClassLoaderUtilsTest.class.getClassLoader()) {
        };

        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            URL resource = ClassLoaderUtils.findResource(VERSION_PROPERTIES_RESOURCE);
            assertThat(resource).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void resourceLookupsFallBackToLibraryClassLoaderWhenContextClassLoaderMissesThem() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader missingContextClassLoader = new ClassLoader(null) {
            @Override
            public URL getResource(final String name) {
                return null;
            }

            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        Thread.currentThread().setContextClassLoader(missingContextClassLoader);
        try {
            URL resource = ClassLoaderUtils.findResource(VERSION_PROPERTIES_RESOURCE);
            assertThat(resource).isNotNull();

            try (InputStream inputStream = ClassLoaderUtils.findResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
                assertThat(inputStream).isNotNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class ContextLoadedType {
    }
}
