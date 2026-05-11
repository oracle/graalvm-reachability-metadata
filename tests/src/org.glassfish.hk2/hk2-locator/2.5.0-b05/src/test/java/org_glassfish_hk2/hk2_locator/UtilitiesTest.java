/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_locator;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.internal.Collector;
import org.jvnet.hk2.internal.Utilities;

public class UtilitiesTest {
    @Test
    void loadClassUsesDescriptorDefaultClassLoader() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        final Collector collector = new Collector();

        final Class<?> loadedClass = Utilities.loadClass(String.class.getName(), descriptor, collector);

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(collector.hasErrors()).isFalse();
    }

    @Test
    void loadClassUsesUtilitiesClassLoaderForNullInjectee() {
        final Class<?> loadedClass = Utilities.loadClass(Integer.class.getName(), null);

        assertThat(loadedClass).isEqualTo(Integer.class);
    }

    @Test
    void loadClassFallsBackToContextClassLoader() {
        final ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader contextOnlyLoader = new ContextOnlyClassLoader(originalContextLoader);
        Thread.currentThread().setContextClassLoader(contextOnlyLoader);

        try {
            final Class<?> loadedClass = Utilities.loadClass(ContextOnlyClassLoader.CONTEXT_ONLY_NAME, null);

            assertThat(loadedClass).isEqualTo(ContextOnlyType.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }
    }

    @Test
    void getFactoryProvideMethodFindsPublicProvideMethod() {
        final Method provideMethod = Utilities.getFactoryProvideMethod(StringFactory.class);

        assertThat(provideMethod).isNotNull();
        assertThat(provideMethod.getName()).isEqualTo("provide");
        assertThat(provideMethod.getParameterCount()).isZero();
    }

    @Test
    void proxiesAvailableChecksForJavassistProxySupport() {
        final boolean available = Utilities.proxiesAvailable();

        assertThat(available).isIn(true, false);
    }

    private static final class ContextOnlyClassLoader extends ClassLoader {
        private static final String CONTEXT_ONLY_NAME = "example.ContextOnlyType";

        private ContextOnlyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_NAME.equals(name)) {
                return ContextOnlyType.class;
            }

            return super.loadClass(name);
        }
    }

    private static final class ContextOnlyType {
    }

    public static final class StringFactory implements Factory<String> {
        @Override
        public String provide() {
            return "created";
        }

        @Override
        public void dispose(String instance) {
        }
    }
}
