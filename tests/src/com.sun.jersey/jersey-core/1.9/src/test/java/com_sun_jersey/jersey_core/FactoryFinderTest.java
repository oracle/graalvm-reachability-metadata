/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

public class FactoryFinderTest {
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/"
            + RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;

    @Test
    void findsRuntimeDelegateFromSystemPropertyWhenContextClassLoaderIsNull() {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        final String originalProperty = System.getProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        try {
            RuntimeDelegate.setInstance(null);
            thread.setContextClassLoader(null);
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY,
                    ProviderRuntimeDelegate.class.getName());

            final RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ProviderRuntimeDelegate.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            restoreRuntimeDelegateProperty(originalProperty);
            RuntimeDelegate.setInstance(null);
        }
    }

    @Test
    void findsRuntimeDelegateFromServiceResourceWithContextClassLoader() {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            RuntimeDelegate.setInstance(null);
            thread.setContextClassLoader(new ServiceResourceClassLoader(ProviderRuntimeDelegate.class.getName()));

            final RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ProviderRuntimeDelegate.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            RuntimeDelegate.setInstance(null);
        }
    }

    @Test
    void fallsBackToFactoryFinderClassLoaderWhenContextClassLoaderCannotLoadServiceClass() {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            RuntimeDelegate.setInstance(null);
            thread.setContextClassLoader(new ServiceResourceClassLoader(ProviderRuntimeDelegate.class.getName()) {
                @Override
                protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                    if (ProviderRuntimeDelegate.class.getName().equals(name)) {
                        throw new ClassNotFoundException(name);
                    }
                    return super.loadClass(name, resolve);
                }
            });

            final RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ProviderRuntimeDelegate.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            RuntimeDelegate.setInstance(null);
        }
    }

    private static void restoreRuntimeDelegateProperty(final String originalProperty) {
        if (originalProperty == null) {
            System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, originalProperty);
        }
    }

    private static class ServiceResourceClassLoader extends ClassLoader {
        private final String providerClassName;

        ServiceResourceClassLoader(final String providerClassName) {
            super(FactoryFinderTest.class.getClassLoader());
            this.providerClassName = providerClassName;
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            if (RUNTIME_DELEGATE_SERVICE.equals(name)) {
                return new ByteArrayInputStream(providerClassName.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }

    public static final class ProviderRuntimeDelegate extends RuntimeDelegate {
        public ProviderRuntimeDelegate() {
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
        public <T> T createEndpoint(final Application application, final Class<T> endpointType)
                throws IllegalArgumentException, UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(final Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }
}
