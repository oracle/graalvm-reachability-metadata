/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.osgi.Activator;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.service.ServiceFinder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    @TempDir
    Path tempDir;

    @Test
    void osgiServiceFinderCreatesProviderInstanceRegisteredByBundle() throws Exception {
        Path serviceFile = tempDir.resolve(Comparator.class.getName());
        Files.writeString(
                serviceFile,
                ComparatorProvider.class.getName(),
                StandardCharsets.UTF_8);
        TestBundle bundle = new TestBundle(17L, serviceFile.toUri().toURL());
        TestBundleContext bundleContext = new TestBundleContext(bundle);
        Activator activator = new Activator();
        boolean started = false;

        try {
            activator.start(bundleContext);
            started = true;

            ServiceFinder<Comparator> finder = ServiceFinder.find(
                    Comparator.class,
                    ActivatorInnerOsgiServiceFinderAnonymous1Test.class.getClassLoader());
            Iterator<Comparator> providers = finder.iterator();

            assertThat(providers.hasNext()).isTrue();
            Comparator provider = providers.next();
            assertThat(provider).isInstanceOf(ComparatorProvider.class);
            assertThat(provider.compare("a", "bbb")).isNegative();
        } finally {
            try {
                if (started) {
                    activator.stop(bundleContext);
                }
            } finally {
                ServiceFinder.setIteratorProvider(new ServiceFinder.DefaultServiceIteratorProvider());
                PackageNamesScanner.setResourcesProvider(new DefaultResourcesProvider());
            }
        }
    }

    public static final class ComparatorProvider implements Comparator<String> {
        public ComparatorProvider() {
        }

        @Override
        public int compare(String left, String right) {
            return Integer.compare(left.length(), right.length());
        }
    }

    private static final class DefaultResourcesProvider extends PackageNamesScanner.ResourcesProvider {
        @Override
        public Enumeration<URL> getResources(String name, ClassLoader classLoader) throws IOException {
            return classLoader.getResources(name);
        }
    }

    private static final class TestBundle implements Bundle {
        private final long bundleId;
        private final URL serviceUrl;

        private TestBundle(long bundleId, URL serviceUrl) {
            this.bundleId = bundleId;
            this.serviceUrl = serviceUrl;
        }

        @Override
        public int getState() {
            return Bundle.ACTIVE;
        }

        @Override
        public void start() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uninstall() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Dictionary getHeaders() {
            return new Hashtable();
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public String getLocation() {
            return "test-bundle";
        }

        @Override
        public ServiceReference[] getRegisteredServices() {
            return new ServiceReference[0];
        }

        @Override
        public ServiceReference[] getServicesInUse() {
            return new ServiceReference[0];
        }

        @Override
        public boolean hasPermission(Object permission) {
            return true;
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public Dictionary getHeaders(String locale) {
            return new Hashtable();
        }

        @Override
        public String getSymbolicName() {
            return "test-bundle";
        }

        @Override
        public Class loadClass(String name) throws ClassNotFoundException {
            if (ComparatorProvider.class.getName().equals(name)) {
                return ComparatorProvider.class;
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration getEntryPaths(String path) {
            return Collections.emptyEnumeration();
        }

        @Override
        public URL getEntry(String path) {
            return null;
        }

        @Override
        public long getLastModified() {
            return 0L;
        }

        @Override
        public Enumeration findEntries(String path, String filePattern, boolean recurse) {
            if ("META-INF/services/".equals(path)) {
                return Collections.enumeration(Collections.singletonList(serviceUrl));
            }
            return Collections.emptyEnumeration();
        }
    }

    private static final class TestBundleContext implements BundleContext {
        private final Bundle bundle;

        private TestBundleContext(Bundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public String getProperty(String key) {
            return null;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public Bundle installBundle(String location) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle installBundle(String location, InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle(long id) {
            if (id == bundle.getBundleId()) {
                return bundle;
            }
            return null;
        }

        @Override
        public Bundle[] getBundles() {
            return new Bundle[] {bundle};
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter)
                throws InvalidSyntaxException {
        }

        @Override
        public void addServiceListener(ServiceListener listener) {
        }

        @Override
        public void removeServiceListener(ServiceListener listener) {
        }

        @Override
        public void addBundleListener(BundleListener listener) {
        }

        @Override
        public void removeBundleListener(BundleListener listener) {
        }

        @Override
        public void addFrameworkListener(FrameworkListener listener) {
        }

        @Override
        public void removeFrameworkListener(FrameworkListener listener) {
        }

        @Override
        public ServiceRegistration registerService(String[] classNames, Object service, Dictionary properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration registerService(String className, Object service, Dictionary properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference[] getServiceReferences(String className, String filter)
                throws InvalidSyntaxException {
            return new ServiceReference[0];
        }

        @Override
        public ServiceReference[] getAllServiceReferences(String className, String filter)
                throws InvalidSyntaxException {
            return new ServiceReference[0];
        }

        @Override
        public ServiceReference getServiceReference(String className) {
            return null;
        }

        @Override
        public Object getService(ServiceReference reference) {
            return null;
        }

        @Override
        public boolean ungetService(ServiceReference reference) {
            return false;
        }

        @Override
        public File getDataFile(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Filter createFilter(String filter) throws InvalidSyntaxException {
            throw new UnsupportedOperationException();
        }
    }
}
