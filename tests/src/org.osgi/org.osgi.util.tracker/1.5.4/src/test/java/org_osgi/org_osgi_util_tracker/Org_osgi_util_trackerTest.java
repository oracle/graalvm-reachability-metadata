/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_util_tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class Org_osgi_util_trackerTest {
    @Test
    void serviceTrackerUsesDefaultCustomizerAndRanksTrackedServices() throws Exception {
        FakeBundleContext context = new FakeBundleContext();
        Greeting lowRanking = () -> "low";
        Greeting highRanking = () -> "high";
        ServiceRegistration<Greeting> lowRegistration = context.registerService(Greeting.class, lowRanking,
                dictionary(Constants.SERVICE_RANKING, 1));
        ServiceRegistration<Greeting> highRegistration = context.registerService(Greeting.class, highRanking,
                dictionary(Constants.SERVICE_RANKING, 10));

        ServiceTracker<Greeting, Greeting> tracker = new ServiceTracker<>(context, Greeting.class, null);

        assertThat(tracker.isEmpty()).isTrue();
        assertThat(tracker.getTrackingCount()).isEqualTo(-1);
        assertThat(tracker.getServiceReferences()).isNull();

        tracker.open();
        try {
            assertThat(context.serviceListenerCount()).isOne();
            assertThat(tracker.isEmpty()).isFalse();
            assertThat(tracker.size()).isEqualTo(2);
            assertThat(tracker.getTrackingCount()).isEqualTo(2);
            assertThat(tracker.getServiceReference()).isSameAs(highRegistration.getReference());
            assertThat(tracker.getService()).isSameAs(highRanking);
            assertThat(tracker.getService(lowRegistration.getReference())).isSameAs(lowRanking);
            assertThat(tracker.getServices()).containsExactlyInAnyOrder(lowRanking, highRanking);

            Greeting[] greetings = tracker.getServices(new Greeting[1]);
            assertThat(greetings).containsExactlyInAnyOrder(lowRanking, highRanking);
            assertThat(tracker.getServices(new Greeting[3])).contains(lowRanking, highRanking, null);

            SortedMap<ServiceReference<Greeting>, Greeting> tracked = tracker.getTracked();
            assertThat(tracked).containsEntry(lowRegistration.getReference(), lowRanking)
                    .containsEntry(highRegistration.getReference(), highRanking);
            assertThat(tracked.firstKey()).isSameAs(highRegistration.getReference());
        } finally {
            tracker.close();
        }

        assertThat(context.serviceListenerCount()).isZero();
        assertThat(context.ungetCount(lowRegistration.getReference())).isOne();
        assertThat(context.ungetCount(highRegistration.getReference())).isOne();
        assertThat(tracker.isEmpty()).isTrue();
        assertThat(tracker.size()).isZero();
        assertThat(tracker.getTracked()).isEmpty();
    }

    @Test
    void serviceTrackerCustomizerReceivesRegisterModifyRemoveEvents() throws Exception {
        FakeBundleContext context = new FakeBundleContext();
        RecordingServiceCustomizer customizer = new RecordingServiceCustomizer(context);
        Filter filter = context.createFilter("(category=tracked)");
        ServiceTracker<Greeting, String> tracker = new ServiceTracker<>(context, filter, customizer);

        tracker.open();
        try {
            assertThat(tracker.waitForService(1)).isNull();
            assertThatIllegalArgumentException().isThrownBy(() -> tracker.waitForService(-1));

            ServiceRegistration<Greeting> ignored = context.registerService(Greeting.class, () -> "ignored",
                    dictionary("category", "ignored"));
            assertThat(tracker.isEmpty()).isTrue();
            assertThat(customizer.events).isEmpty();

            ServiceRegistration<Greeting> registration = context.registerService(Greeting.class, () -> "tracked",
                    dictionary("category", "tracked"));
            ServiceReference<Greeting> reference = registration.getReference();

            assertThat(tracker.waitForService(1)).isEqualTo("custom:tracked");
            assertThat(tracker.getTrackingCount()).isEqualTo(1);
            assertThat(tracker.getService(reference)).isEqualTo("custom:tracked");
            assertThat(customizer.events).containsExactly("adding:tracked");

            registration.setProperties(dictionary("category", "tracked", "name", "renamed"));
            assertThat(tracker.getTrackingCount()).isEqualTo(2);
            assertThat(customizer.events).containsExactly("adding:tracked", "modified:tracked");

            registration.unregister();
            assertThat(tracker.isEmpty()).isTrue();
            assertThat(tracker.getTrackingCount()).isEqualTo(3);
            assertThat(customizer.events).containsExactly("adding:tracked", "modified:tracked", "removed:tracked");

            tracker.remove(ignored.getReference());
            assertThat(customizer.events).hasSize(3);
        } finally {
            tracker.close();
        }
    }

    @Test
    void serviceTrackerRemovesServiceWhenPropertiesNoLongerMatchFilter() throws Exception {
        FakeBundleContext context = new FakeBundleContext();
        RecordingServiceCustomizer customizer = new RecordingServiceCustomizer(context);
        ServiceRegistration<Greeting> registration = context.registerService(Greeting.class, () -> "tracked",
                dictionary("category", "tracked"));
        ServiceReference<Greeting> reference = registration.getReference();
        ServiceTracker<Greeting, String> tracker = new ServiceTracker<>(context,
                context.createFilter("(category=tracked)"), customizer);

        tracker.open();
        try {
            assertThat(tracker.getService(reference)).isEqualTo("custom:tracked");
            assertThat(tracker.size()).isOne();
            assertThat(customizer.events).containsExactly("adding:tracked");

            registration.setProperties(dictionary("category", "ignored"));

            assertThat(tracker.isEmpty()).isTrue();
            assertThat(tracker.getService(reference)).isNull();
            assertThat(tracker.getServiceReferences()).isNull();
            assertThat(tracker.getTrackingCount()).isEqualTo(2);
            assertThat(customizer.events).containsExactly("adding:tracked", "removed:tracked");
        } finally {
            tracker.close();
        }
    }

    @Test
    void serviceTrackerDoesNotTrackServicesRejectedByCustomizer() throws Exception {
        FakeBundleContext context = new FakeBundleContext();
        RejectingServiceCustomizer customizer = new RejectingServiceCustomizer(context);
        ServiceRegistration<Greeting> rejected = context.registerService(Greeting.class, () -> "rejected",
                dictionary("category", "tracked"));
        ServiceRegistration<Greeting> accepted = context.registerService(Greeting.class, () -> "accepted",
                dictionary("category", "tracked", "enabled", true));
        ServiceTracker<Greeting, String> tracker = new ServiceTracker<>(context,
                context.createFilter("(category=tracked)"), customizer);

        tracker.open();
        try {
            assertThat(customizer.events).containsExactly("adding:rejected", "adding:accepted");
            assertThat(tracker.size()).isOne();
            assertThat(tracker.getService(rejected.getReference())).isNull();
            assertThat(tracker.getService(accepted.getReference())).isEqualTo("accepted:accepted");
            assertThat(tracker.getTracked()).containsExactly(Map.entry(accepted.getReference(), "accepted:accepted"));
            assertThat(tracker.getTrackingCount()).isOne();

            rejected.setProperties(dictionary("category", "tracked", "enabled", true));

            assertThat(customizer.events).containsExactly("adding:rejected", "adding:accepted", "adding:rejected");
            assertThat(tracker.size()).isEqualTo(2);
            assertThat(tracker.getService(rejected.getReference())).isEqualTo("accepted:rejected");
            assertThat(tracker.getTrackingCount()).isEqualTo(2);
        } finally {
            tracker.close();
        }
    }

    @Test
    void serviceTrackerCanTrackAllServicesAndSingleReferences() throws Exception {
        FakeBundleContext context = new FakeBundleContext();
        ServiceRegistration<Greeting> registration = context.registerService(Greeting.class, () -> "all",
                dictionary("category", "tracked"));

        ServiceTracker<Greeting, Greeting> allServicesTracker = new ServiceTracker<>(context,
                context.createFilter("(category=tracked)"), null);
        allServicesTracker.open(true);
        try {
            assertThat(context.allServiceReferenceLookups).isOne();
            assertThat(context.serviceReferenceLookups).isZero();
            assertThat(allServicesTracker.getService()).isSameAs(context.getService(registration.getReference()));
        } finally {
            allServicesTracker.close();
        }

        ServiceTracker<Greeting, Greeting> singleReferenceTracker = new ServiceTracker<>(context,
                registration.getReference(), null);
        singleReferenceTracker.open();
        try {
            assertThat(singleReferenceTracker.size()).isOne();
            assertThat(singleReferenceTracker.getServiceReference()).isSameAs(registration.getReference());
        } finally {
            singleReferenceTracker.close();
        }
    }

    @Test
    void bundleTrackerUsesDefaultCustomizerForInitialBundles() {
        FakeBundleContext context = new FakeBundleContext();
        FakeBundle active = new FakeBundle(1L, "active", Bundle.ACTIVE, context);
        FakeBundle resolved = new FakeBundle(2L, "resolved", Bundle.RESOLVED, context);
        context.addBundle(active);
        context.addBundle(resolved);

        BundleTracker<Bundle> tracker = new BundleTracker<>(context, Bundle.ACTIVE, null);

        assertThat(tracker.isEmpty()).isTrue();
        assertThat(tracker.getTrackingCount()).isEqualTo(-1);
        assertThat(tracker.getBundles()).isNull();

        tracker.open();
        try {
            assertThat(context.bundleListenerCount()).isOne();
            assertThat(tracker.isEmpty()).isFalse();
            assertThat(tracker.size()).isOne();
            assertThat(tracker.getBundles()).containsExactly(active);
            assertThat(tracker.getObject(active)).isSameAs(active);
            assertThat(tracker.getObject(resolved)).isNull();
            assertThat(tracker.getTracked()).containsExactly(Map.entry(active, active));
            assertThat(tracker.getTrackingCount()).isOne();
        } finally {
            tracker.close();
        }

        assertThat(context.bundleListenerCount()).isZero();
        assertThat(tracker.isEmpty()).isTrue();
        assertThat(tracker.getTracked()).isEmpty();
    }

    @Test
    void bundleTrackerCustomizerReceivesStateTransitions() {
        FakeBundleContext context = new FakeBundleContext();
        FakeBundle bundle = new FakeBundle(7L, "example.bundle", Bundle.INSTALLED, context);
        context.addBundle(bundle);
        RecordingBundleCustomizer customizer = new RecordingBundleCustomizer();
        BundleTracker<String> tracker = new BundleTracker<>(context, Bundle.ACTIVE | Bundle.STARTING, customizer);

        tracker.open();
        try {
            assertThat(tracker.isEmpty()).isTrue();

            bundle.setState(Bundle.STARTING);
            context.fireBundleEvent(BundleEvent.STARTING, bundle);
            assertThat(tracker.size()).isOne();
            assertThat(tracker.getObject(bundle)).isEqualTo("tracked:example.bundle");
            assertThat(tracker.getTrackingCount()).isEqualTo(1);
            assertThat(customizer.events).containsExactly("adding:example.bundle:" + BundleEvent.STARTING);

            bundle.setState(Bundle.ACTIVE);
            context.fireBundleEvent(BundleEvent.STARTED, bundle);
            assertThat(tracker.getTrackingCount()).isEqualTo(2);
            assertThat(customizer.events).containsExactly("adding:example.bundle:" + BundleEvent.STARTING,
                    "modified:example.bundle:" + BundleEvent.STARTED);

            tracker.remove(bundle);
            assertThat(tracker.isEmpty()).isTrue();
            assertThat(tracker.getTrackingCount()).isEqualTo(3);
            assertThat(customizer.events).containsExactly("adding:example.bundle:" + BundleEvent.STARTING,
                    "modified:example.bundle:" + BundleEvent.STARTED, "removed:example.bundle:no-event");

            bundle.setState(Bundle.ACTIVE);
            context.fireBundleEvent(BundleEvent.STARTED, bundle);
            bundle.setState(Bundle.RESOLVED);
            context.fireBundleEvent(BundleEvent.STOPPED, bundle);
            assertThat(customizer.events).containsExactly("adding:example.bundle:" + BundleEvent.STARTING,
                    "modified:example.bundle:" + BundleEvent.STARTED, "removed:example.bundle:no-event",
                    "adding:example.bundle:" + BundleEvent.STARTED, "removed:example.bundle:" + BundleEvent.STOPPED);
        } finally {
            tracker.close();
        }
    }

    private static Dictionary<String, Object> dictionary(Object... keyValues) {
        Hashtable<String, Object> dictionary = (Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            dictionary.put((String) keyValues[i], keyValues[i + 1]);
        }
        return dictionary;
    }

    private interface Greeting {
        String message();
    }

    private static final class RecordingServiceCustomizer implements ServiceTrackerCustomizer<Greeting, String> {
        private final FakeBundleContext context;
        private final List<String> events = new ArrayList<>();

        private RecordingServiceCustomizer(FakeBundleContext context) {
            this.context = context;
        }

        @Override
        public String addingService(ServiceReference<Greeting> reference) {
            Greeting service = context.getService(reference);
            events.add("adding:" + service.message());
            return "custom:" + service.message();
        }

        @Override
        public void modifiedService(ServiceReference<Greeting> reference, String service) {
            events.add("modified:" + service.substring("custom:".length()));
        }

        @Override
        public void removedService(ServiceReference<Greeting> reference, String service) {
            events.add("removed:" + service.substring("custom:".length()));
        }
    }

    private static final class RejectingServiceCustomizer implements ServiceTrackerCustomizer<Greeting, String> {
        private final FakeBundleContext context;
        private final List<String> events = new ArrayList<>();

        private RejectingServiceCustomizer(FakeBundleContext context) {
            this.context = context;
        }

        @Override
        public String addingService(ServiceReference<Greeting> reference) {
            Greeting service = context.getService(reference);
            events.add("adding:" + service.message());
            if (!Boolean.TRUE.equals(reference.getProperty("enabled"))) {
                return null;
            }
            return "accepted:" + service.message();
        }

        @Override
        public void modifiedService(ServiceReference<Greeting> reference, String service) {
            events.add("modified:" + service.substring("accepted:".length()));
        }

        @Override
        public void removedService(ServiceReference<Greeting> reference, String service) {
            events.add("removed:" + service.substring("accepted:".length()));
        }
    }

    private static final class RecordingBundleCustomizer implements BundleTrackerCustomizer<String> {
        private final List<String> events = new ArrayList<>();

        @Override
        public String addingBundle(Bundle bundle, BundleEvent event) {
            events.add("adding:" + bundle.getSymbolicName() + ":" + eventType(event));
            return "tracked:" + bundle.getSymbolicName();
        }

        @Override
        public void modifiedBundle(Bundle bundle, BundleEvent event, String object) {
            events.add("modified:" + bundle.getSymbolicName() + ":" + eventType(event));
        }

        @Override
        public void removedBundle(Bundle bundle, BundleEvent event, String object) {
            events.add("removed:" + bundle.getSymbolicName() + ":" + eventType(event));
        }

        private static String eventType(BundleEvent event) {
            return event == null ? "no-event" : String.valueOf(event.getType());
        }
    }

    private static final class FakeBundleContext implements BundleContext {
        private final FakeBundle owner = new FakeBundle(0L, "owner", Bundle.INSTALLED, this);
        private final List<FakeBundle> bundles = new ArrayList<>();
        private final List<ServiceListenerRegistration> serviceListeners = new ArrayList<>();
        private final List<BundleListener> bundleListeners = new ArrayList<>();
        private final Map<FakeServiceReference<?>, Object> services = new LinkedHashMap<>();
        private final Map<ServiceReference<?>, AtomicInteger> ungetCounts = new HashMap<>();
        private long nextServiceId = 1L;
        private int serviceReferenceLookups;
        private int allServiceReferenceLookups;

        private FakeBundleContext() {
            bundles.add(owner);
        }

        private void addBundle(FakeBundle bundle) {
            bundles.add(bundle);
        }

        private int serviceListenerCount() {
            return serviceListeners.size();
        }

        private int bundleListenerCount() {
            return bundleListeners.size();
        }

        private int ungetCount(ServiceReference<?> reference) {
            return ungetCounts.getOrDefault(reference, new AtomicInteger()).get();
        }

        private void fireBundleEvent(int type, Bundle bundle) {
            BundleEvent event = new BundleEvent(type, bundle);
            for (BundleListener listener : List.copyOf(bundleListeners)) {
                listener.bundleChanged(event);
            }
        }

        @Override
        public String getProperty(String key) {
            return null;
        }

        @Override
        public Bundle getBundle() {
            return owner;
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
            return bundles.stream().filter(bundle -> bundle.getBundleId() == id).findFirst().orElse(null);
        }

        @Override
        public Bundle[] getBundles() {
            return bundles.toArray(new Bundle[0]);
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
            Filter parsedFilter = filter == null ? SimpleFilter.matchAll() : createFilter(filter);
            serviceListeners.add(new ServiceListenerRegistration(listener, parsedFilter));
        }

        @Override
        public void addServiceListener(ServiceListener listener) {
            serviceListeners.add(new ServiceListenerRegistration(listener, SimpleFilter.matchAll()));
        }

        @Override
        public void removeServiceListener(ServiceListener listener) {
            serviceListeners.removeIf(registration -> registration.listener == listener);
        }

        @Override
        public void addBundleListener(BundleListener listener) {
            bundleListeners.add(listener);
        }

        @Override
        public void removeBundleListener(BundleListener listener) {
            bundleListeners.remove(listener);
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
        public ServiceRegistration<?> registerService(String[] clazzes, Object service,
                Dictionary<String, ?> properties) {
            return register(clazzes, service, properties);
        }

        @Override
        public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
            return register(new String[] {clazz}, service, properties);
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
            return register(new String[] {clazz.getName()}, service, properties);
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory,
                Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
            serviceReferenceLookups++;
            return findReferences(clazz, filter);
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
                throws InvalidSyntaxException {
            allServiceReferenceLookups++;
            return findReferences(clazz, filter);
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz) {
            try {
                ServiceReference<?>[] references = findReferences(clazz, null);
                return references == null ? null : references[0];
            } catch (InvalidSyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
            @SuppressWarnings("unchecked")
            ServiceReference<S> reference = (ServiceReference<S>) getServiceReference(clazz.getName());
            return reference;
        }

        @Override
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
                throws InvalidSyntaxException {
            ServiceReference<?>[] references = findReferences(clazz.getName(), filter);
            if (references == null) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            Collection<ServiceReference<S>> typedReferences = (Collection<ServiceReference<S>>) (Collection<?>) Arrays
                    .asList(references);
            return typedReferences;
        }

        @Override
        public <S> S getService(ServiceReference<S> reference) {
            @SuppressWarnings("unchecked")
            S service = (S) services.get(reference);
            return service;
        }

        @Override
        public boolean ungetService(ServiceReference<?> reference) {
            ungetCounts.computeIfAbsent(reference, ignored -> new AtomicInteger()).incrementAndGet();
            return services.containsKey(reference);
        }

        @Override
        public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFile(String filename) {
            return new File(filename);
        }

        @Override
        public Filter createFilter(String filter) throws InvalidSyntaxException {
            return SimpleFilter.parse(filter);
        }

        @Override
        public Bundle getBundle(String location) {
            return bundles.stream()
                    .filter(bundle -> Objects.equals(bundle.getLocation(), location))
                    .findFirst()
                    .orElse(null);
        }

        private <S> ServiceRegistration<S> register(String[] clazzes, Object service,
                Dictionary<String, ?> properties) {
            Hashtable<String, Object> copied = copyProperties(properties);
            copied.put(Constants.OBJECTCLASS, clazzes);
            copied.put(Constants.SERVICE_ID, nextServiceId++);
            copied.putIfAbsent(Constants.SERVICE_RANKING, 0);
            FakeServiceReference<S> reference = new FakeServiceReference<>(owner, copied);
            services.put(reference, service);
            fireServiceEvent(ServiceEvent.REGISTERED, reference);
            return new FakeServiceRegistration<>(this, reference);
        }

        private Hashtable<String, Object> copyProperties(Dictionary<String, ?> properties) {
            Hashtable<String, Object> copy = (Hashtable<String, Object>) (Hashtable<?, ?>) new java.util.Properties();
            if (properties != null) {
                Enumeration<String> keys = properties.keys();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    copy.put(key, properties.get(key));
                }
            }
            return copy;
        }

        private ServiceReference<?>[] findReferences(String clazz, String filter) throws InvalidSyntaxException {
            SimpleFilter parsedFilter = filter == null ? SimpleFilter.matchAll() : SimpleFilter.parse(filter);
            List<ServiceReference<?>> matches = new ArrayList<>();
            for (FakeServiceReference<?> reference : services.keySet()) {
                if (reference.getBundle() != null && matchesClass(reference, clazz) && parsedFilter.match(reference)) {
                    matches.add(reference);
                }
            }
            if (matches.isEmpty()) {
                return null;
            }
            matches.sort(Collections.reverseOrder());
            return matches.toArray(new ServiceReference<?>[0]);
        }

        private boolean matchesClass(ServiceReference<?> reference, String clazz) {
            if (clazz == null) {
                return true;
            }
            return propertyContains(reference.getProperty(Constants.OBJECTCLASS), clazz);
        }

        private void updateProperties(FakeServiceReference<?> reference, Dictionary<String, ?> properties) {
            Map<ServiceListenerRegistration, Boolean> previousMatches = new LinkedHashMap<>();
            for (ServiceListenerRegistration registration : serviceListeners) {
                previousMatches.put(registration, registration.filter.match(reference));
            }
            String[] objectClasses = (String[]) reference.getProperty(Constants.OBJECTCLASS);
            Long serviceId = (Long) reference.getProperty(Constants.SERVICE_ID);
            Hashtable<String, Object> copied = copyProperties(properties);
            copied.put(Constants.OBJECTCLASS, objectClasses);
            copied.put(Constants.SERVICE_ID, serviceId);
            copied.putIfAbsent(Constants.SERVICE_RANKING, 0);
            reference.properties = copied;
            fireModifiedServiceEvents(reference, previousMatches);
        }

        private void fireModifiedServiceEvents(ServiceReference<?> reference,
                Map<ServiceListenerRegistration, Boolean> previousMatches) {
            for (ServiceListenerRegistration registration : List.copyOf(serviceListeners)) {
                boolean matches = registration.filter.match(reference);
                if (matches) {
                    registration.listener.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, reference));
                } else if (Boolean.TRUE.equals(previousMatches.get(registration))) {
                    registration.listener.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED_ENDMATCH, reference));
                }
            }
        }

        private void unregister(FakeServiceReference<?> reference) {
            if (services.remove(reference) != null) {
                fireServiceEvent(ServiceEvent.UNREGISTERING, reference);
                reference.bundle = null;
            }
        }

        private void fireServiceEvent(int type, ServiceReference<?> reference) {
            ServiceEvent event = new ServiceEvent(type, reference);
            for (ServiceListenerRegistration registration : List.copyOf(serviceListeners)) {
                if (registration.filter.match(reference)) {
                    registration.listener.serviceChanged(event);
                }
            }
        }
    }

    private static final class FakeServiceRegistration<S> implements ServiceRegistration<S> {
        private final FakeBundleContext context;
        private final FakeServiceReference<S> reference;

        private FakeServiceRegistration(FakeBundleContext context, FakeServiceReference<S> reference) {
            this.context = context;
            this.reference = reference;
        }

        @Override
        public ServiceReference<S> getReference() {
            return reference;
        }

        @Override
        public void setProperties(Dictionary<String, ?> properties) {
            context.updateProperties(reference, properties);
        }

        @Override
        public void unregister() {
            context.unregister(reference);
        }
    }

    private static final class FakeServiceReference<S> implements ServiceReference<S> {
        private Bundle bundle;
        private Dictionary<String, Object> properties;

        private FakeServiceReference(Bundle bundle, Dictionary<String, Object> properties) {
            this.bundle = bundle;
            this.properties = properties;
        }

        @Override
        public Object getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public String[] getPropertyKeys() {
            List<String> keys = new ArrayList<>();
            Enumeration<String> enumeration = properties.keys();
            while (enumeration.hasMoreElements()) {
                keys.add(enumeration.nextElement());
            }
            return keys.toArray(new String[0]);
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return null;
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String className) {
            return propertyContains(getProperty(Constants.OBJECTCLASS), className);
        }

        @Override
        public int compareTo(Object other) {
            ServiceReference<?> otherReference = (ServiceReference<?>) other;
            int rankingComparison = Integer.compare(ranking(this), ranking(otherReference));
            if (rankingComparison != 0) {
                return rankingComparison;
            }
            return Long.compare(id(otherReference), id(this));
        }

        public Dictionary<String, Object> getProperties() {
            return properties;
        }

        private static int ranking(ServiceReference<?> reference) {
            Object ranking = reference.getProperty(Constants.SERVICE_RANKING);
            return ranking instanceof Integer ? (Integer) ranking : 0;
        }

        private static long id(ServiceReference<?> reference) {
            return (Long) reference.getProperty(Constants.SERVICE_ID);
        }
    }

    private static final class FakeBundle implements Bundle {
        private final long id;
        private final String symbolicName;
        private final BundleContext context;
        private int state;

        private FakeBundle(long id, String symbolicName, int state, BundleContext context) {
            this.id = id;
            this.symbolicName = symbolicName;
            this.state = state;
            this.context = context;
        }

        private void setState(int state) {
            this.state = state;
        }

        @Override
        public int getState() {
            return state;
        }

        @Override
        public void start(int options) throws BundleException {
            state = ACTIVE;
        }

        @Override
        public void start() throws BundleException {
            start(0);
        }

        @Override
        public void stop(int options) throws BundleException {
            state = RESOLVED;
        }

        @Override
        public void stop() throws BundleException {
            stop(0);
        }

        @Override
        public void update(InputStream input) throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() throws BundleException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uninstall() throws BundleException {
            state = UNINSTALLED;
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            return (Dictionary<String, String>) (Dictionary<?, ?>) new java.util.Properties();
        }

        @Override
        public long getBundleId() {
            return id;
        }

        @Override
        public String getLocation() {
            return "memory:" + symbolicName;
        }

        @Override
        public ServiceReference<?>[] getRegisteredServices() {
            return null;
        }

        @Override
        public ServiceReference<?>[] getServicesInUse() {
            return null;
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
        public Dictionary<String, String> getHeaders(String locale) {
            return getHeaders();
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getEntryPaths(String path) {
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
        public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
            return Collections.emptyEnumeration();
        }

        @Override
        public BundleContext getBundleContext() {
            return context;
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
            return Collections.emptyMap();
        }

        @Override
        public Version getVersion() {
            return Version.emptyVersion;
        }

        @Override
        public <A> A adapt(Class<A> type) {
            return null;
        }

        @Override
        public File getDataFile(String filename) {
            return new File(filename);
        }

        @Override
        public int compareTo(Bundle other) {
            return Long.compare(id, other.getBundleId());
        }
    }

    private static final class ServiceListenerRegistration {
        private final ServiceListener listener;
        private final Filter filter;

        private ServiceListenerRegistration(ServiceListener listener, Filter filter) {
            this.listener = listener;
            this.filter = filter;
        }
    }

    private static final class SimpleFilter implements Filter {
        private final String expression;
        private final String key;
        private final String value;

        private SimpleFilter(String expression, String key, String value) {
            this.expression = expression;
            this.key = key;
            this.value = value;
        }

        private static SimpleFilter matchAll() {
            return new SimpleFilter("(*)", null, null);
        }

        private static SimpleFilter parse(String expression) throws InvalidSyntaxException {
            if (expression == null || expression.isBlank()) {
                return matchAll();
            }
            if (!expression.startsWith("(") || !expression.endsWith(")") || !expression.contains("=")) {
                throw new InvalidSyntaxException("Only simple equality filters are supported by this test", expression);
            }
            String body = expression.substring(1, expression.length() - 1);
            int separator = body.indexOf('=');
            return new SimpleFilter(expression, body.substring(0, separator), body.substring(separator + 1));
        }

        @Override
        public boolean match(ServiceReference<?> reference) {
            if (key == null) {
                return true;
            }
            return propertyContains(reference.getProperty(key), value);
        }

        @Override
        public boolean match(Dictionary<String, ?> dictionary) {
            if (key == null) {
                return true;
            }
            return propertyContains(dictionary.get(key), value);
        }

        @Override
        public boolean matchCase(Dictionary<String, ?> dictionary) {
            return match(dictionary);
        }

        @Override
        public boolean matches(Map<String, ?> map) {
            if (key == null) {
                return true;
            }
            return propertyContains(map.get(key), value);
        }

        @Override
        public String toString() {
            return expression;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SimpleFilter && Objects.equals(expression, ((SimpleFilter) other).expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression);
        }
    }

    private static boolean propertyContains(Object property, String expected) {
        if (property instanceof String[]) {
            return Arrays.asList((String[]) property).contains(expected);
        }
        return Objects.equals(String.valueOf(property), expected);
    }
}
