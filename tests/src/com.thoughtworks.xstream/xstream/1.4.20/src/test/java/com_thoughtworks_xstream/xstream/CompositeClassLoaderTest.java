/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.core.util.CompositeClassLoader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeClassLoaderTest {
    private static final String CONTEXT_ONLY_CLASS_NAME = "context.only.LoadableFixture";

    @Test
    void fallsBackToThreadContextClassLoader() throws ClassNotFoundException {
        CompositeClassLoader loader = new CompositeClassLoader();
        ContextOnlyClassLoader contextOnlyClassLoader = new ContextOnlyClassLoader();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextOnlyClassLoader);

            Class<?> loadedClass = loader.loadClass(CONTEXT_ONLY_CLASS_NAME);

            assertThat(loadedClass).isSameAs(LoadableFixture.class);
            assertThat(contextOnlyClassLoader.requestedName).isEqualTo(CONTEXT_ONLY_CLASS_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class LoadableFixture {
    }

    private static final class ContextOnlyClassLoader extends ClassLoader {
        private String requestedName;

        private ContextOnlyClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedName = name;
            if (CONTEXT_ONLY_CLASS_NAME.equals(name)) {
                return LoadableFixture.class;
            }
            throw new ClassNotFoundException(name);
        }
    }
}
