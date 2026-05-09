/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.osgi.Activator;
import com.sun.jersey.core.osgi.OsgiLocator;
import com.sun.jersey.spi.service.ServiceFinder;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

public class ActivatorInnerOsgiServiceFinderAnonymous1Test {
    private Callable<List<Class>> serviceFactory;

    @AfterEach
    void resetServiceFinder() {
        if (serviceFactory != null) {
            OsgiLocator.unregister(TestService.class.getName(), serviceFactory);
        }
        ServiceFinder.setIteratorProvider(new ServiceFinder.DefaultServiceIteratorProvider());
    }

    @Test
    void serviceFinderInstantiatesProvidersLocatedFromOsgiRegistry() throws Exception {
        serviceFactory = new Callable<List<Class>>() {
            @Override
            public List<Class> call() {
                return Collections.<Class>singletonList(TestProvider.class);
            }
        };
        OsgiLocator.register(TestService.class.getName(), serviceFactory);

        final Activator activator = new Activator();
        final BundleContext bundleContext = new EmptyBundleContext();
        activator.start(bundleContext);
        try {
            final Iterator<TestService> services = ServiceFinder.find(
                    TestService.class,
                    ActivatorInnerOsgiServiceFinderAnonymous1Test.class.getClassLoader(),
                    false).iterator();

            assertThat(services.hasNext()).isTrue();
            final TestService service = services.next();
            assertThat(service).isInstanceOf(TestProvider.class);
            assertThat(service.name()).isEqualTo("osgi-provider");
            assertThat(services.hasNext()).isFalse();
        } finally {
            activator.stop(bundleContext);
        }
    }

    public interface TestService {
        String name();
    }

    public static final class TestProvider implements TestService {
        @Override
        public String name() {
            return "osgi-provider";
        }
    }

    private static final class EmptyBundleContext implements BundleContext {
        @Override
        public String getProperty(final String key) {
            throw unsupportedOperation();
        }

        @Override
        public Bundle getBundle() {
            throw unsupportedOperation();
        }

        @Override
        public Bundle installBundle(final String location, final InputStream input) throws BundleException {
            throw unsupportedOperation();
        }

        @Override
        public Bundle installBundle(final String location) throws BundleException {
            throw unsupportedOperation();
        }

        @Override
        public Bundle getBundle(final long id) {
            throw unsupportedOperation();
        }

        @Override
        public Bundle[] getBundles() {
            return new Bundle[0];
        }

        @Override
        public void addServiceListener(final ServiceListener listener, final String filter)
                throws InvalidSyntaxException {
            throw unsupportedOperation();
        }

        @Override
        public void addServiceListener(final ServiceListener listener) {
            throw unsupportedOperation();
        }

        @Override
        public void removeServiceListener(final ServiceListener listener) {
            throw unsupportedOperation();
        }

        @Override
        public void addBundleListener(final BundleListener listener) {
            // The activator only needs listener registration to succeed.
        }

        @Override
        public void removeBundleListener(final BundleListener listener) {
            // The activator only needs listener removal to succeed.
        }

        @Override
        public void addFrameworkListener(final FrameworkListener listener) {
            throw unsupportedOperation();
        }

        @Override
        public void removeFrameworkListener(final FrameworkListener listener) {
            throw unsupportedOperation();
        }

        @Override
        public ServiceRegistration<?> registerService(
                final String[] classes,
                final Object service,
                final Dictionary<String, ?> properties) {
            throw unsupportedOperation();
        }

        @Override
        public ServiceRegistration<?> registerService(
                final String className,
                final Object service,
                final Dictionary<String, ?> properties) {
            throw unsupportedOperation();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                final Class<S> serviceClass,
                final S service,
                final Dictionary<String, ?> properties) {
            throw unsupportedOperation();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                final Class<S> serviceClass,
                final ServiceFactory<S> factory,
                final Dictionary<String, ?> properties) {
            throw unsupportedOperation();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(final String className, final String filter)
                throws InvalidSyntaxException {
            throw unsupportedOperation();
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(final String className, final String filter)
                throws InvalidSyntaxException {
            throw unsupportedOperation();
        }

        @Override
        public ServiceReference<?> getServiceReference(final String className) {
            throw unsupportedOperation();
        }

        @Override
        public <S> ServiceReference<S> getServiceReference(final Class<S> serviceClass) {
            throw unsupportedOperation();
        }

        @Override
        public <S> Collection<ServiceReference<S>> getServiceReferences(
                final Class<S> serviceClass,
                final String filter) throws InvalidSyntaxException {
            throw unsupportedOperation();
        }

        @Override
        public <S> S getService(final ServiceReference<S> reference) {
            throw unsupportedOperation();
        }

        @Override
        public boolean ungetService(final ServiceReference<?> reference) {
            throw unsupportedOperation();
        }

        @Override
        public <S> ServiceObjects<S> getServiceObjects(final ServiceReference<S> reference) {
            throw unsupportedOperation();
        }

        @Override
        public File getDataFile(final String filename) {
            throw unsupportedOperation();
        }

        @Override
        public Filter createFilter(final String filter) throws InvalidSyntaxException {
            throw unsupportedOperation();
        }

        @Override
        public Bundle getBundle(final String location) {
            throw unsupportedOperation();
        }

        private static UnsupportedOperationException unsupportedOperation() {
            return new UnsupportedOperationException(
                    "Only bundle listener and bundle enumeration methods are supported");
        }
    }
}
