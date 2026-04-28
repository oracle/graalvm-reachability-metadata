/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class ServiceTrackerTest {
    @Test
    void getServicesCreatesTypedArrayForTrackedServices() {
        TestService firstService = new TestService("first");
        TestService secondService = new TestService("second");
        FakeServiceReference<TestService> firstReference = new FakeServiceReference<>(1L, firstService);
        FakeServiceReference<TestService> secondReference = new FakeServiceReference<>(2L, secondService);
        FakeBundleContext bundleContext = new FakeBundleContext(firstReference, secondReference);
        FakeTrackerCustomizer customizer = new FakeTrackerCustomizer();
        ServiceTracker<TestService, TestService> tracker =
                new ServiceTracker<>(bundleContext, TestService.class, customizer);

        tracker.open();
        try {
            TestService[] services = tracker.getServices(new TestService[0]);

            assertThat(services)
                    .isInstanceOf(TestService[].class)
                    .containsExactlyInAnyOrder(firstService, secondService);
        } finally {
            tracker.close();
        }
    }

    private static final class TestService {
        private final String name;

        private TestService(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class FakeTrackerCustomizer
            implements ServiceTrackerCustomizer<TestService, TestService> {
        @Override
        public TestService addingService(ServiceReference<TestService> reference) {
            return ((FakeServiceReference<TestService>) reference).service();
        }

        @Override
        public void modifiedService(ServiceReference<TestService> reference, TestService service) {
        }

        @Override
        public void removedService(ServiceReference<TestService> reference, TestService service) {
        }
    }

    private static final class FakeServiceReference<S> implements ServiceReference<S> {
        private final long serviceId;
        private final S service;

        private FakeServiceReference(long serviceId, S service) {
            this.serviceId = serviceId;
            this.service = service;
        }

        private S service() {
            return service;
        }

        @Override
        public Object getProperty(String key) {
            if (Constants.SERVICE_ID.equals(key)) {
                return serviceId;
            }
            if (Constants.SERVICE_RANKING.equals(key)) {
                return 0;
            }
            if (Constants.OBJECTCLASS.equals(key)) {
                return new String[] {TestService.class.getName()};
            }
            return null;
        }

        @Override
        public String[] getPropertyKeys() {
            return new String[] {
                Constants.SERVICE_ID,
                Constants.SERVICE_RANKING,
                Constants.OBJECTCLASS
            };
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return new Bundle[0];
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return TestService.class.getName().equals(className);
        }

        @Override
        public int compareTo(Object other) {
            if (!(other instanceof FakeServiceReference)) {
                return 0;
            }
            FakeServiceReference<?> otherReference = (FakeServiceReference<?>) other;
            return Long.compare(otherReference.serviceId, serviceId);
        }
    }

    private static final class FakeBundleContext implements BundleContext {
        private final ServiceReference<?>[] references;

        private FakeBundleContext(ServiceReference<?>... references) {
            this.references = references.clone();
        }

        @Override
        public String getProperty(String key) {
            return null;
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle installBundle(String location, InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle installBundle(String location) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle(long id) {
            return null;
        }

        @Override
        public Bundle[] getBundles() {
            return new Bundle[0];
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
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
        public ServiceRegistration<?> registerService(
                String[] clazzes, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration<?> registerService(
                String clazz, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                Class<S> clazz, S service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                Class<S> clazz, ServiceFactory<S> factory, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
            return references.clone();
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
                throws InvalidSyntaxException {
            return references.clone();
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz) {
            return references.length == 0 ? null : references[0];
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
            return references.length == 0 ? null : (ServiceReference<S>) references[0];
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
                throws InvalidSyntaxException {
            return (Collection<ServiceReference<S>>) (Collection<?>) List.of(references);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> S getService(ServiceReference<S> reference) {
            return ((FakeServiceReference<S>) reference).service();
        }

        @Override
        public boolean ungetService(ServiceReference<?> reference) {
            return true;
        }

        @Override
        public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFile(String filename) {
            return null;
        }

        @Override
        public Filter createFilter(String filter) throws InvalidSyntaxException {
            return new AcceptingFilter(filter);
        }

        @Override
        public Bundle getBundle(String location) {
            return null;
        }
    }

    private static final class AcceptingFilter implements Filter {
        private final String value;

        private AcceptingFilter(String value) {
            this.value = value;
        }

        @Override
        public boolean match(ServiceReference<?> reference) {
            return true;
        }

        @Override
        public boolean match(Dictionary<String, ?> dictionary) {
            return true;
        }

        @Override
        public boolean matchCase(Dictionary<String, ?> dictionary) {
            return true;
        }

        @Override
        public boolean matches(Map<String, ?> map) {
            return true;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
