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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class RuntimeDelegateTest {
    @Test
    void rejectsConfiguredProviderThatDoesNotImplementRuntimeDelegate() {
        RuntimeDelegate replacementDelegate = new UnsupportedRuntimeDelegate();
        Throwable failure = findRuntimeDelegateWith(NonRuntimeDelegateProvider.class);

        assertThat(failure).isNotNull();
        try {
            RuntimeDelegate.setInstance(replacementDelegate);
            assertThat(RuntimeDelegate.getInstance()).isSameAs(replacementDelegate);
        } finally {
            RuntimeDelegate.setInstance(null);
        }
    }

    private static Throwable findRuntimeDelegateWith(Class<?> providerClass) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String propertyName = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
        String originalProperty = System.getProperty(propertyName);
        try {
            RuntimeDelegate.setInstance(null);
            Thread.currentThread().setContextClassLoader(RuntimeDelegateTest.class.getClassLoader());
            System.setProperty(propertyName, providerClass.getName());

            return catchThrowable(RuntimeDelegate::getInstance);
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

    public static final class NonRuntimeDelegateProvider {
        public NonRuntimeDelegateProvider() {
        }
    }

    private static final class UnsupportedRuntimeDelegate extends RuntimeDelegate {
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
}
