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
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
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
import org.osgi.framework.FrameworkUtil;
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
    void getServicesCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        GreetingService firstService = () -> "first";
        GreetingService secondService = () -> "second";
        SimpleServiceReference<GreetingService> firstReference = new SimpleServiceReference<>(1L, 10);
        SimpleServiceReference<GreetingService> secondReference = new SimpleServiceReference<>(2L, 20);
        SimpleBundleContext context = new SimpleBundleContext(List.of(firstReference, secondReference));
        ServiceTrackerCustomizer<GreetingService, GreetingService> customizer = new MapBackedCustomizer<>(
                Map.of(firstReference, firstService, secondReference, secondService));
        ServiceTracker<GreetingService, GreetingService> tracker = new ServiceTracker<>(
                context, GreetingService.class, customizer);

        tracker.open();
        GreetingService[] providedArray = new GreetingService[1];

        GreetingService[] trackedServices = tracker.getServices(providedArray);

        assertThat(trackedServices).isNotSameAs(providedArray);
        assertThat(trackedServices).hasSize(2);
        assertThat(trackedServices.getClass().getComponentType()).isEqualTo(GreetingService.class);
        assertThat(trackedServices).containsExactlyInAnyOrder(firstService, secondService);
        assertThat(context.registeredListener).isNotNull();
    }

    private interface GreetingService {
        String greeting();
    }

    private static final class MapBackedCustomizer<S> implements ServiceTrackerCustomizer<S, S> {
        private final Map<ServiceReference<S>, S> services;

        private MapBackedCustomizer(Map<ServiceReference<S>, S> services) {
            this.services = services;
        }

        @Override
        public S addingService(ServiceReference<S> reference) {
            return services.get(reference);
        }

        @Override
        public void modifiedService(ServiceReference<S> reference, S service) {
        }

        @Override
        public void removedService(ServiceReference<S> reference, S service) {
        }
    }

    private static final class SimpleServiceReference<S> implements ServiceReference<S> {
        private final Long serviceId;
        private final Integer ranking;

        private SimpleServiceReference(Long serviceId, Integer ranking) {
            this.serviceId = serviceId;
            this.ranking = ranking;
        }

        @Override
        public Object getProperty(String key) {
            if (Constants.SERVICE_ID.equals(key)) {
                return serviceId;
            }
            if (Constants.SERVICE_RANKING.equals(key)) {
                return ranking;
            }
            return null;
        }

        @Override
        public String[] getPropertyKeys() {
            return new String[] {Constants.SERVICE_ID, Constants.SERVICE_RANKING};
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            return new Dictionary<>() {
                @Override
                public int size() {
                    return 2;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public Enumeration<String> keys() {
                    return Collections.enumeration(List.of(Constants.SERVICE_ID, Constants.SERVICE_RANKING));
                }

                @Override
                public Enumeration<Object> elements() {
                    return Collections.enumeration(List.of(serviceId, ranking));
                }

                @Override
                public Object get(Object key) {
                    return key instanceof String ? getProperty((String) key) : null;
                }

                @Override
                public Object put(String key, Object value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Object remove(Object key) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return null;
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return true;
        }

        @Override
        public <A> A adapt(Class<A> type) {
            return null;
        }

        @Override
        public int compareTo(Object reference) {
            ServiceReference<?> other = (ServiceReference<?>) reference;
            int rankingComparison = ranking.compareTo((Integer) other.getProperty(Constants.SERVICE_RANKING));
            if (rankingComparison != 0) {
                return rankingComparison;
            }
            return ((Long) other.getProperty(Constants.SERVICE_ID)).compareTo(serviceId);
        }
    }

    private static final class SimpleBundleContext implements BundleContext {
        private final List<? extends ServiceReference<?>> references;
        private ServiceListener registeredListener;

        private SimpleBundleContext(List<? extends ServiceReference<?>> references) {
            this.references = references;
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
            FrameworkUtil.createFilter(filter);
            registeredListener = listener;
        }

        @Override
        public void addServiceListener(ServiceListener listener) {
            registeredListener = listener;
        }

        @Override
        public void removeServiceListener(ServiceListener listener) {
            if (registeredListener == listener) {
                registeredListener = null;
            }
        }

        @Override
        public void addBundleListener(BundleListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeBundleListener(BundleListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addFrameworkListener(FrameworkListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeFrameworkListener(FrameworkListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration<?> registerService(
                String[] clazzes, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(
                Class<S> clazz, ServiceFactory<S> factory, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
            if (filter != null) {
                FrameworkUtil.createFilter(filter);
            }
            return references.toArray(new ServiceReference<?>[0]);
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
            return getServiceReferences(clazz, filter);
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz) {
            return references.get(0);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
            return (ServiceReference<S>) references.get(0);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
                throws InvalidSyntaxException {
            if (filter != null) {
                FrameworkUtil.createFilter(filter);
            }
            return (Collection<ServiceReference<S>>) references;
        }

        @Override
        public <S> S getService(ServiceReference<S> reference) {
            return null;
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
            return FrameworkUtil.createFilter(filter);
        }

        @Override
        public Bundle getBundle(String location) {
            return null;
        }
    }
}
