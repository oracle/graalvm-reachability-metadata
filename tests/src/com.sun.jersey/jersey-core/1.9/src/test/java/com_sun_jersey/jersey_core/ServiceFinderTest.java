/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.impl.provider.header.MediaTypeProvider;
import com.sun.jersey.spi.HeaderDelegateProvider;
import com.sun.jersey.spi.service.ServiceFinder;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceFinderTest {
    @BeforeAll
    static void setRuntimeDelegate() {
        RuntimeDelegate.setInstance(new MinimalRuntimeDelegate());
    }

    @Test
    void discoversHeaderDelegateProvidersAsInstancesAndClasses() {
        ClassLoader classLoader = ServiceFinderTest.class.getClassLoader();
        ServiceFinder<HeaderDelegateProvider> finder = ServiceFinder.find(
                HeaderDelegateProvider.class, classLoader);

        HeaderDelegateProvider[] providers = finder.toArray();
        Class<HeaderDelegateProvider>[] providerClasses = finder.toClassArray();
        List<String> providerDescriptions = Arrays.stream(providers)
                .map(provider -> provider.getClass().getName() + ":" + provider.supports(MediaType.class))
                .toList();
        List<String> providerClassNames = Arrays.stream(providerClasses)
                .map(Class::getName)
                .toList();

        assertTrue(providers.length > 0, () -> "providers=" + providerDescriptions);
        assertTrue(
                Arrays.stream(providers).anyMatch(provider -> provider.supports(MediaType.class)),
                () -> "providers=" + providerDescriptions);
        assertTrue(
                providerClassNames.contains(MediaTypeProvider.class.getName()),
                () -> "providerClasses=" + providerClassNames);
    }

    @Test
    void discoversHeaderDelegateProviderClassesWithSystemLookupWhenLoaderIsNull() {
        ServiceFinder<HeaderDelegateProvider> finder = ServiceFinder.find(
                HeaderDelegateProvider.class, null);

        Class<HeaderDelegateProvider>[] providerClasses = finder.toClassArray();
        List<String> providerClassNames = Arrays.stream(providerClasses)
                .map(Class::getName)
                .toList();

        assertTrue(
                providerClassNames.contains(MediaTypeProvider.class.getName()),
                () -> "providerClasses=" + providerClassNames);
    }

    private static final class MinimalRuntimeDelegate extends RuntimeDelegate {
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
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString(Object value) {
            throw new UnsupportedOperationException();
        }
    }
}
