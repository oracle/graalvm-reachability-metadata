/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    @Test
    void findsRuntimeDelegateWithContextClassLoader() {
        RuntimeDelegate delegate = findRuntimeDelegate(FactoryFinderTest.class.getClassLoader());

        assertThat(delegate).isInstanceOf(TestRuntimeDelegate.class);
    }

    @Test
    void fallsBackToApplicationClassLoaderWhenContextClassLoaderCannotLoadDelegate() {
        RuntimeDelegate delegate = findRuntimeDelegate(new IsolatedClassLoader());

        assertThat(delegate).isInstanceOf(TestRuntimeDelegate.class);
    }

    @Test
    void findsRuntimeDelegateWithSystemResourceLookupWhenContextClassLoaderIsNull() {
        RuntimeDelegate delegate = findRuntimeDelegate(null);

        assertThat(delegate).isInstanceOf(TestRuntimeDelegate.class);
    }

    private static RuntimeDelegate findRuntimeDelegate(ClassLoader contextClassLoader) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String propertyName = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
        String originalProperty = System.getProperty(propertyName);
        try {
            RuntimeDelegate.setInstance(null);
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            System.setProperty(propertyName, TestRuntimeDelegate.class.getName());

            return RuntimeDelegate.getInstance();
        } finally {
            RuntimeDelegate.setInstance(null);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            if (originalProperty == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalProperty);
            }
        }
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader() {
            super(null);
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
        @SuppressWarnings("unchecked")
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            return (HeaderDelegate<T>) NoOpHeaderDelegate.INSTANCE;
        }
    }

    private enum NoOpHeaderDelegate implements HeaderDelegate<Object> {
        INSTANCE;

        @Override
        public Object fromString(String value) {
            return value;
        }

        @Override
        public String toString(Object value) {
            return String.valueOf(value);
        }
    }
}
