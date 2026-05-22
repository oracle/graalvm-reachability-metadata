/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.osgi.Activator;
import com.sun.jersey.core.osgi.OsgiLocator;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.service.ServiceFinder;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivatorInnerOsgiServiceFinderAnonymous1Test {
    private static final String SERVICE_NAME = OsgiDiscoveredService.class.getName();

    @Test
    public void createsProviderInstancesRegisteredInOsgiLocator() throws Exception {
        final Activator activator = new Activator();
        final BundleContext bundleContext = new EmptyBundleContext();
        final Callable<List<Class>> providerFactory = new Callable<List<Class>>() {
            @Override
            public List<Class> call() {
                return Collections.<Class>singletonList(OsgiServiceProvider.class);
            }
        };

        activator.start(bundleContext);
        OsgiLocator.register(SERVICE_NAME, providerFactory);
        try {
            final Iterator<OsgiDiscoveredService> iterator = ServiceFinder.find(OsgiDiscoveredService.class).iterator();

            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next()).isInstanceOf(OsgiServiceProvider.class);
            assertThat(iterator.hasNext()).isFalse();
        } finally {
            OsgiLocator.unregister(SERVICE_NAME, providerFactory);
            activator.stop(bundleContext);
            ServiceFinder.setIteratorProvider(new ServiceFinder.DefaultServiceIteratorProvider());
            PackageNamesScanner.setResourcesProvider(null);
        }
    }

    public interface OsgiDiscoveredService {
    }

    public static final class OsgiServiceProvider implements OsgiDiscoveredService {
    }

    private static final class EmptyBundleContext implements BundleContext {
        @Override
        public String getProperty(final String key) {
            throw unsupported();
        }

        @Override
        public Bundle getBundle() {
            throw unsupported();
        }

        @Override
        public Bundle installBundle(final String location) throws BundleException {
            throw unsupported();
        }

        @Override
        public Bundle installBundle(final String location, final InputStream input) throws BundleException {
            throw unsupported();
        }

        @Override
        public Bundle getBundle(final long id) {
            throw unsupported();
        }

        @Override
        public Bundle[] getBundles() {
            return new Bundle[0];
        }

        @Override
        public void addServiceListener(final ServiceListener listener, final String filter)
                throws InvalidSyntaxException {
            throw unsupported();
        }

        @Override
        public void addServiceListener(final ServiceListener listener) {
            throw unsupported();
        }

        @Override
        public void removeServiceListener(final ServiceListener listener) {
            throw unsupported();
        }

        @Override
        public void addBundleListener(final BundleListener listener) {
        }

        @Override
        public void removeBundleListener(final BundleListener listener) {
        }

        @Override
        public void addFrameworkListener(final FrameworkListener listener) {
            throw unsupported();
        }

        @Override
        public void removeFrameworkListener(final FrameworkListener listener) {
            throw unsupported();
        }

        @Override
        public ServiceRegistration registerService(
                final String[] clazzes,
                final Object service,
                final Dictionary properties) {
            throw unsupported();
        }

        @Override
        public ServiceRegistration registerService(
                final String clazz,
                final Object service,
                final Dictionary properties) {
            throw unsupported();
        }

        @Override
        public ServiceReference[] getServiceReferences(final String clazz, final String filter)
                throws InvalidSyntaxException {
            throw unsupported();
        }

        @Override
        public ServiceReference[] getAllServiceReferences(final String clazz, final String filter)
                throws InvalidSyntaxException {
            throw unsupported();
        }

        @Override
        public ServiceReference getServiceReference(final String clazz) {
            throw unsupported();
        }

        @Override
        public Object getService(final ServiceReference reference) {
            throw unsupported();
        }

        @Override
        public boolean ungetService(final ServiceReference reference) {
            throw unsupported();
        }

        @Override
        public File getDataFile(final String filename) {
            throw unsupported();
        }

        @Override
        public Filter createFilter(final String filter) throws InvalidSyntaxException {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "Only bundle listing and listener registration are used by this test");
        }
    }
}
