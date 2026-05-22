/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import java.io.InputStream;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.class.getName();
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/" + RUNTIME_DELEGATE_PROPERTY;
    private static final String TEST_DELEGATE_CLASS_NAME = TestRuntimeDelegate.class.getName();

    @Test
    public void findsRuntimeDelegateFromSystemPropertyWhenContextClassLoaderIsNull() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        final String originalProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

        RuntimeDelegate.setInstance(null);
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, TEST_DELEGATE_CLASS_NAME);
        currentThread.setContextClassLoader(null);
        try {
            final RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(TestRuntimeDelegate.class);
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreSystemProperty(originalProperty);
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    public void fallsBackToApplicationClassLoaderWhenContextClassLoaderCannotLoadDelegateClass() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        final String originalProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        final ClassLoader contextClassLoader = new DelegateHidingClassLoader(originalContextClassLoader);

        RuntimeDelegate.setInstance(null);
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, TEST_DELEGATE_CLASS_NAME);
        currentThread.setContextClassLoader(contextClassLoader);
        try {
            final RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(TestRuntimeDelegate.class);
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreSystemProperty(originalProperty);
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreSystemProperty(String originalProperty) {
        if (originalProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalProperty);
        }
    }

    public static final class TestRuntimeDelegate extends RuntimeDelegate {
        public TestRuntimeDelegate() {
        }

        @Override
        public UriBuilder createUriBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseBuilder createResponseBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VariantListBuilder createVariantListBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DelegateHidingClassLoader extends ClassLoader {
        private DelegateHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RUNTIME_DELEGATE_SERVICE.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (TEST_DELEGATE_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
